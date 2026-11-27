package com.primepay.beneficiary;

import com.primepay.audit.AuditService;
import com.primepay.common.crypto.FieldEncryptor;
import com.primepay.common.error.BusinessException;
import com.primepay.common.error.ErrorCode;
import com.primepay.transaction.FinancialTransaction;
import com.primepay.transaction.PayoutService;
import com.primepay.user.AccountRole;
import com.primepay.user.AgentAccount;
import com.primepay.user.AgentAccountRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Withdraw funds: a retailer first adds a bank account (number, IFSC, holder name); the bank verifies it and
 * only a VERIFIED account appears in the list and can receive a transfer from the main wallet.
 */
@Service
public class BeneficiaryService {
    private final BeneficiaryRepository beneficiaries;
    private final BankVerifier verifier;
    private final FieldEncryptor crypto;
    private final AgentAccountRepository accounts;
    private final PayoutService payouts;
    private final AuditService audit;

    public BeneficiaryService(BeneficiaryRepository beneficiaries, BankVerifier verifier, FieldEncryptor crypto,
                              AgentAccountRepository accounts, PayoutService payouts, AuditService audit) {
        this.beneficiaries = beneficiaries;
        this.verifier = verifier;
        this.crypto = crypto;
        this.accounts = accounts;
        this.payouts = payouts;
        this.audit = audit;
    }

    public record BeneficiaryView(Long id, String holderName, String verifiedName, String accountMasked, String accountLast4, String ifsc,
                                  String bankName, String status, OffsetDateTime createdAt) {
    }

    /** Result of adding an account: the saved row, plus the bank's reason when verification failed. */
    public record AddResult(BeneficiaryView beneficiary, boolean verified, String reason) {
    }

    @Transactional(readOnly = true)
    public List<BeneficiaryView> list(AgentAccount actor) {
        AgentAccount me = requireRetailer(actor);
        return beneficiaries.findByAccountIdAndStatusNotOrderByIdDesc(me.getId(), Beneficiary.REMOVED).stream()
                .map(BeneficiaryService::view).toList();
    }

    @Transactional
    public AddResult add(AgentAccount actor, String accountNumber, String ifscRaw, String holderName) {
        AgentAccount me = requireRetailer(actor);
        String ifsc = ifscRaw.toUpperCase(Locale.ROOT);
        String hash = crypto.hash(accountNumber);
        if (beneficiaries.findFirstByAccountIdAndAccountHashAndIfscAndStatus(me.getId(), hash, ifsc, Beneficiary.VERIFIED).isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE, "This bank account is already in your list.");
        }
        BankVerifier.Result result = verifier.verify(accountNumber, ifsc, holderName);
        Beneficiary b = new Beneficiary(me.getId(), holderName.trim(), crypto.encrypt(accountNumber), hash,
                accountNumber.substring(accountNumber.length() - 4), ifsc, result.bankName());
        if (result.valid()) {
            b.setStatus(Beneficiary.VERIFIED);
            b.setVerifiedName(result.registeredName());
        }
        beneficiaries.save(b);
        audit.record(me, "BENEFICIARY_ADD", "BENEFICIARY", String.valueOf(b.getId()),
                "a/c ••" + b.getAccountLast4() + " " + ifsc + " → " + b.getStatus(), result.valid() ? AuditService.SUCCESS : AuditService.FAILED);
        return new AddResult(view(b), result.valid(), result.reason());
    }

    @Transactional
    public void remove(AgentAccount actor, Long id) {
        AgentAccount me = requireRetailer(actor);
        Beneficiary b = owned(me, id);
        b.setStatus(Beneficiary.REMOVED);
        audit.record(me, "BENEFICIARY_REMOVE", "BENEFICIARY", String.valueOf(id), "a/c ••" + b.getAccountLast4());
    }

    /** Fund transfer from the main wallet to a verified saved account. Runs the full payout flow (limits, fee, ledger, commission). */
    @Transactional
    public FinancialTransaction withdraw(AgentAccount actor, Long beneficiaryId, BigDecimal amount, String mode, String idempotencyKey) {
        AgentAccount me = requireRetailer(actor);
        Beneficiary b = owned(me, beneficiaryId);
        if (!Beneficiary.VERIFIED.equals(b.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Only a verified bank account can receive a withdrawal.");
        }
        String name = b.getVerifiedName() != null ? b.getVerifiedName() : b.getHolderName();
        return payouts.payout(me, new PayoutService.PayoutCommand(name, crypto.decrypt(b.getAccountNumberEnc()), b.getIfsc(), b.getBankName(),
                mode, amount), idempotencyKey);
    }

    private Beneficiary owned(AgentAccount me, Long id) {
        return beneficiaries.findById(id).filter(x -> x.getAccountId().equals(me.getId()) && !Beneficiary.REMOVED.equals(x.getStatus()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Bank account not found."));
    }

    private AgentAccount requireRetailer(AgentAccount actor) {
        AgentAccount account = accounts.findById(actor.getId()).orElseThrow();
        if (account.getRole() == AccountRole.ADMIN) throw new BusinessException(ErrorCode.ACCESS_DENIED, "Platform Admins don't do business.");
        if (!account.isActive()) throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE, "Your account is not active.");
        return account;
    }

    private static BeneficiaryView view(Beneficiary b) {
        return new BeneficiaryView(b.getId(), b.getHolderName(), b.getVerifiedName(), "••••" + b.getAccountLast4(), b.getAccountLast4(),
                b.getIfsc(), b.getBankName(), b.getStatus(), b.getCreatedAt());
    }
}
