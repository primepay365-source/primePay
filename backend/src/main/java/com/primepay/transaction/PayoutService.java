package com.primepay.transaction;

import com.primepay.admin.ServiceDefinition;
import com.primepay.admin.ServiceDefinitionRepository;
import com.primepay.audit.AuditService;
import com.primepay.commission.CommissionEngine;
import com.primepay.common.Money;
import com.primepay.common.error.BusinessException;
import com.primepay.common.error.ErrorCode;
import com.primepay.ledger.LedgerService;
import com.primepay.notification.NotificationService;
import com.primepay.user.AccountRole;
import com.primepay.user.AgentAccount;
import com.primepay.user.AgentAccountRepository;
import com.primepay.velocity.LimitService;
import com.primepay.wallet.Wallet;
import com.primepay.wallet.WalletService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Retailer bank payout (simulated). Flow: limits → debit amount + fee → provider → SUCCESS + commission,
 * or FAILED + compensating refund credit. Only the last 4 digits of the bank account are stored.
 */
@Service
public class PayoutService {
    private final WalletService walletService;
    private final LedgerService ledger;
    private final TransactionRecorder recorder;
    private final LimitService limits;
    private final ServiceDefinitionRepository services;
    private final PayoutProvider provider;
    private final CommissionEngine commission;
    private final NotificationService notifications;
    private final AuditService audit;
    private final AgentAccountRepository accounts;

    public PayoutService(WalletService walletService, LedgerService ledger, TransactionRecorder recorder, LimitService limits,
                         ServiceDefinitionRepository services, PayoutProvider provider, CommissionEngine commission,
                         NotificationService notifications, AuditService audit, AgentAccountRepository accounts) {
        this.walletService = walletService;
        this.ledger = ledger;
        this.recorder = recorder;
        this.limits = limits;
        this.services = services;
        this.provider = provider;
        this.commission = commission;
        this.notifications = notifications;
        this.audit = audit;
        this.accounts = accounts;
    }

    public record PayoutCommand(String beneficiaryName, String accountNumber, String ifsc, String bankName, String mode, BigDecimal amount) {
    }

    @Transactional
    public FinancialTransaction payout(AgentAccount actor, PayoutCommand command, String idempotencyKey) {
        // Reload inside this transaction: we walk up the parent chain for commission and locking.
        AgentAccount retailer = accounts.findById(actor.getId()).orElseThrow();
        if (retailer.getRole() == AccountRole.ADMIN) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Platform Admins don't do business.");
        }
        BigDecimal amount = Money.of(command.amount());
        if ("IMPS".equals(command.mode()) && amount.compareTo(new BigDecimal("25000")) > 0) {
            throw new BusinessException(ErrorCode.LIMIT_EXCEEDED, "IMPS payouts are limited to ₹ 25,000. Use NEFT for larger amounts.");
        }
        ServiceDefinition service = services.requireEnabled(ServiceDefinition.PAYOUT);
        BigDecimal fee = Money.of(service.getFeeAmount());

        // Lock the retailer's wallet and every wallet that may earn commission, in one ordered pass.
        List<Wallet> toLock = new ArrayList<>();
        Wallet own = walletService.getOrCreate(retailer);
        toLock.add(own);
        for (AgentAccount up = retailer.getParent(); up != null; up = up.getParent()) {
            if (up.getRole() != AccountRole.ADMIN) {
                toLock.add(walletService.getOrCreate(up));
            }
        }
        Map<Long, Wallet> locked = walletService.lockAll(toLock);
        Wallet wallet = locked.get(own.getId());

        String last4 = command.accountNumber().substring(command.accountNumber().length() - 4);
        String hash = TransactionRecorder.hash("PAYOUT", command.accountNumber(), command.ifsc(), command.mode(), amount);
        Optional<FinancialTransaction> replay = recorder.findReplay(retailer.getId(), idempotencyKey, hash);
        if (replay.isPresent()) {
            return replay.get();
        }
        limits.check(retailer, ServiceDefinition.PAYOUT, amount);
        if (wallet.getAvailableBalance().compareTo(amount.add(fee)) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE, "This payout needs " + Money.format(amount.add(fee))
                    + " (including the " + Money.format(fee) + " charge) but your balance is " + Money.format(wallet.getAvailableBalance()) + ".");
        }

        FinancialTransaction txn = recorder.create(TxnType.PAYOUT, ServiceDefinition.PAYOUT, retailer.getId(), amount, fee,
                idempotencyKey, hash, "Payout requested; limits and balance checked");
        txn.setSourceAccountId(retailer.getId());
        txn.setBeneficiaryName(command.beneficiaryName());
        txn.setBeneficiaryAccountLast4(last4);
        txn.setBeneficiaryIfsc(command.ifsc().toUpperCase());
        txn.setBeneficiaryBank(command.bankName());
        txn.setPayoutMode(command.mode());

        ledger.debit(wallet, txn, amount, "PAYOUT", "Payout to " + command.beneficiaryName() + " a/c ••" + last4);
        if (fee.signum() > 0) {
            ledger.debit(wallet, txn, fee, "FEE", "Payout service charge");
        }
        recorder.move(txn, TxnStatus.PENDING, "Wallet debited; sent to payout provider (simulated)", retailer.getId());

        PayoutProvider.Result result = provider.send(txn.getReferenceNo(), command.accountNumber(), command.ifsc(),
                command.beneficiaryName(), amount, command.mode());
        if (result.success()) {
            txn.setExternalRef(result.providerRef());
            recorder.move(txn, TxnStatus.SUCCESS, "Bank confirmed · UTR " + result.providerRef(), null);
            commission.distribute(txn, retailer);
            notifications.notify(retailer.getId(), NotificationService.MONEY, "Payout successful",
                    Money.format(amount) + " paid to " + command.beneficiaryName() + " a/c ••" + last4 + ". Ref " + txn.getReferenceNo() + ".",
                    "/transactions/" + txn.getId());
        } else {
            txn.setFailureReason(result.failureReason());
            ledger.credit(wallet, txn, amount.add(fee), "PAYOUT_REFUND", "Refund: " + result.failureReason());
            recorder.move(txn, TxnStatus.FAILED, result.failureReason() + "; amount and charge refunded", null);
            notifications.notify(retailer.getId(), NotificationService.MONEY, "Payout failed",
                    Money.format(amount) + " to a/c ••" + last4 + " failed: " + result.failureReason() + ". The full amount was refunded to your wallet.",
                    "/transactions/" + txn.getId());
            if (retailer.getParent() != null) {
                notifications.notify(retailer.getParent().getId(), NotificationService.MONEY, "Payout failed in your network",
                        retailer.getAccountCode() + " · " + Money.format(amount) + " failed: " + result.failureReason() + ". Nothing was lost.",
                        "/transactions/" + txn.getId());
            }
        }
        audit.record(retailer, "PAYOUT", "TRANSACTION", txn.getReferenceNo(), "status=" + txn.getStatus() + " amount=" + amount.toPlainString(),
                txn.getStatus() == TxnStatus.SUCCESS ? AuditService.SUCCESS : AuditService.FAILED);
        return txn;
    }
}
