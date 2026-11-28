package com.primepay.billpay;

import com.primepay.admin.ServiceDefinition;
import com.primepay.admin.ServiceDefinitionRepository;
import com.primepay.audit.AuditService;
import com.primepay.commission.CommissionEngine;
import com.primepay.common.Money;
import com.primepay.common.error.BusinessException;
import com.primepay.common.error.ErrorCode;
import com.primepay.ledger.LedgerService;
import com.primepay.notification.NotificationService;
import com.primepay.transaction.FinancialTransaction;
import com.primepay.transaction.FinancialTransactionRepository;
import com.primepay.transaction.TransactionRecorder;
import com.primepay.transaction.TxnStatus;
import com.primepay.transaction.TxnType;
import com.primepay.user.AccountRole;
import com.primepay.user.AgentAccount;
import com.primepay.user.AgentAccountRepository;
import com.primepay.velocity.LimitService;
import com.primepay.wallet.Wallet;
import com.primepay.wallet.WalletService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Credit card bill payment from the main wallet: fetch bill → pay → SUCCESS / FAILED / PENDING.
 * Amount + charge are debited when the payment is sent; a FAILED result refunds both with a ledger entry;
 * PENDING stays debited until a status recheck settles it. Commission is paid only on SUCCESS.
 */
@Service
public class BillPaymentService {
    private static final java.time.Duration FETCH_VALIDITY = java.time.Duration.ofMinutes(15);

    private final BillerRepository billers;
    private final BillFetchRepository fetches;
    private final BillPaymentProvider provider;
    private final AgentAccountRepository accounts;
    private final WalletService walletService;
    private final LedgerService ledger;
    private final TransactionRecorder recorder;
    private final FinancialTransactionRepository transactions;
    private final LimitService limits;
    private final ServiceDefinitionRepository services;
    private final CommissionEngine commission;
    private final NotificationService notifications;
    private final AuditService audit;

    public BillPaymentService(BillerRepository billers, BillFetchRepository fetches, BillPaymentProvider provider,
                              AgentAccountRepository accounts, WalletService walletService, LedgerService ledger,
                              TransactionRecorder recorder, FinancialTransactionRepository transactions, LimitService limits,
                              ServiceDefinitionRepository services, CommissionEngine commission, NotificationService notifications,
                              AuditService audit) {
        this.billers = billers;
        this.fetches = fetches;
        this.provider = provider;
        this.accounts = accounts;
        this.walletService = walletService;
        this.ledger = ledger;
        this.recorder = recorder;
        this.transactions = transactions;
        this.limits = limits;
        this.services = services;
        this.commission = commission;
        this.notifications = notifications;
        this.audit = audit;
    }

    public record BillerView(Long id, String category, String code, String name, boolean enabled) {
    }

    public record FetchedBillView(String fetchRef, Long billerId, String billerName, String cardLast4, String mobileMasked,
                                  String customerName, BigDecimal totalDue, BigDecimal minimumDue, java.time.LocalDate dueDate,
                                  BigDecimal charge, OffsetDateTime expiresAt) {
    }

    @Transactional(readOnly = true)
    public List<BillerView> billers(String category) {
        return billers.findByCategoryAndEnabledTrueOrderByNameAsc(category).stream()
                .map(b -> new BillerView(b.getId(), b.getCategory(), b.getBillerCode(), b.getName(), b.isEnabled())).toList();
    }

    @Transactional
    public FetchedBillView fetch(AgentAccount retailer, Long billerId, String cardLast4, String mobile) {
        requireRetailer(retailer);
        ServiceDefinition service = services.requireEnabled(ServiceDefinition.BILL_PAYMENT);
        Biller biller = billers.findById(billerId).filter(Biller::isEnabled)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Choose a bank from the list."));
        BillPaymentProvider.FetchedBill bill = provider.fetch(biller.getBillerCode(), cardLast4, mobile);
        if (bill == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "No bill found for this card and mobile number. Check the last 4 digits and the card's registered mobile.");
        }
        BillFetch saved = fetches.save(new BillFetch("BF" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(),
                retailer.getId(), biller.getId(), cardLast4, mask(mobile), bill.customerName(), bill.totalDue(), bill.minimumDue(),
                bill.dueDate(), bill.providerRef(), OffsetDateTime.now().plus(FETCH_VALIDITY)));
        return view(saved, biller, service.getFeeAmount());
    }

    @Transactional
    public FinancialTransaction pay(AgentAccount actor, String fetchRef, BigDecimal rawAmount, String idempotencyKey) {
        AgentAccount retailer = accounts.findById(actor.getId()).orElseThrow();
        requireRetailer(retailer);
        BigDecimal amount = Money.of(rawAmount);
        ServiceDefinition service = services.requireEnabled(ServiceDefinition.BILL_PAYMENT);
        BigDecimal fee = Money.of(service.getFeeAmount());
        BillFetch bill = fetches.findByFetchRef(fetchRef).filter(f -> f.getAccountId().equals(retailer.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Fetch the bill again before paying."));
        if (bill.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This bill was fetched more than 15 minutes ago. Fetch it again.");
        }
        if (amount.compareTo(bill.getTotalDue()) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The amount can't be more than the total due of " + Money.format(bill.getTotalDue()) + ".");
        }
        Biller biller = billers.findById(bill.getBillerId()).orElseThrow();

        Map<Long, Wallet> locked = lockChain(retailer);
        Wallet wallet = locked.get(walletService.getOrCreate(retailer).getId());
        String hash = TransactionRecorder.hash("BILL", fetchRef, amount);
        Optional<FinancialTransaction> replay = recorder.findReplay(retailer.getId(), idempotencyKey, hash);
        if (replay.isPresent()) {
            return replay.get();
        }
        limits.check(retailer, ServiceDefinition.BILL_PAYMENT, amount);
        if (wallet.getAvailableBalance().compareTo(amount.add(fee)) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE, "This payment needs " + Money.format(amount.add(fee))
                    + " including the " + Money.format(fee) + " charge, but your main wallet has " + Money.format(wallet.getAvailableBalance()) + ".");
        }

        FinancialTransaction txn = recorder.create(TxnType.BILL_PAYMENT, ServiceDefinition.BILL_PAYMENT, retailer.getId(), amount, fee,
                idempotencyKey, hash, "Bill payment requested; limits and balance checked");
        txn.setSourceAccountId(retailer.getId());
        txn.setBillerName(biller.getName());
        txn.setCustomerName(bill.getCustomerName());
        txn.setCardLast4(bill.getCardLast4());
        txn.setCustomerMobileMasked(bill.getMobileMasked());
        txn.setRemarks(biller.getName() + " · card ••" + bill.getCardLast4());
        ledger.debit(wallet, txn, amount, "BILL_PAYMENT", "Bill payment " + biller.getName() + " ••" + bill.getCardLast4());
        if (fee.signum() > 0) {
            ledger.debit(wallet, txn, fee, "FEE", "Bill payment charge");
        }
        recorder.move(txn, TxnStatus.PENDING, "Wallet debited; sent to biller (simulated)", retailer.getId());

        BillPaymentProvider.PaymentResult result = provider.pay(biller.getBillerCode(), bill.getProviderRef(), bill.getCardLast4(), amount);
        txn.setExternalRef(result.providerRef());
        settle(txn, retailer, wallet, result);
        audit.record(retailer, "BILL_PAYMENT", "TRANSACTION", txn.getReferenceNo(), "status=" + txn.getStatus() + " amount=" + amount.toPlainString(),
                txn.getStatus() == TxnStatus.FAILED ? AuditService.FAILED : AuditService.SUCCESS);
        return txn;
    }

    /** Status enquiry for a PENDING bill payment (retailer who made it, or Platform Admin). */
    @Transactional
    public FinancialTransaction recheck(AgentAccount actor, Long txnId) {
        FinancialTransaction txn = transactions.findById(txnId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Transaction not found."));
        if (txn.getType() != TxnType.BILL_PAYMENT) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Only bill payments can be rechecked.");
        }
        if (actor.getRole() != AccountRole.ADMIN && !txn.getInitiatorAccountId().equals(actor.getId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Transaction not found.");
        }
        if (txn.getStatus() != TxnStatus.PENDING) {
            return txn;
        }
        AgentAccount retailer = accounts.findById(txn.getInitiatorAccountId()).orElseThrow();
        Map<Long, Wallet> locked = lockChain(retailer);
        Wallet wallet = locked.get(walletService.getOrCreate(retailer).getId());
        settle(txn, retailer, wallet, provider.status(txn.getExternalRef()));
        audit.record(actor, "BILL_PAYMENT_RECHECK", "TRANSACTION", txn.getReferenceNo(), "status=" + txn.getStatus());
        return txn;
    }

    private void settle(FinancialTransaction txn, AgentAccount retailer, Wallet wallet, BillPaymentProvider.PaymentResult result) {
        switch (result.status()) {
            case "SUCCESS" -> {
                recorder.move(txn, TxnStatus.SUCCESS, "Biller confirmed payment · ref " + result.providerRef(), null);
                commission.distribute(txn, retailer);
                notifications.notify(retailer.getId(), NotificationService.MONEY, "Bill paid",
                        Money.format(txn.getAmount()) + " paid to " + txn.getBillerName() + " card ••" + txn.getCardLast4() + ". Ref " + txn.getReferenceNo() + ".",
                        "/transactions/" + txn.getId());
            }
            case "FAILED" -> {
                txn.setFailureReason(result.reason());
                ledger.credit(wallet, txn, txn.totalDebit(), "BILL_REFUND", "Refund: " + result.reason());
                recorder.move(txn, TxnStatus.FAILED, result.reason() + "; amount and charge refunded", null);
                notifications.notify(retailer.getId(), NotificationService.MONEY, "Bill payment failed",
                        Money.format(txn.getAmount()) + " to " + txn.getBillerName() + " failed: " + result.reason()
                                + ". " + Money.format(txn.totalDebit()) + " was refunded to your main wallet.", "/transactions/" + txn.getId());
            }
            default -> notifications.notify(retailer.getId(), NotificationService.MONEY, "Bill payment pending",
                    Money.format(txn.getAmount()) + " to " + txn.getBillerName() + " is awaiting the biller's confirmation. Recheck it from the transaction page.",
                    "/transactions/" + txn.getId());
        }
    }

    private Map<Long, Wallet> lockChain(AgentAccount retailer) {
        List<Wallet> toLock = new ArrayList<>();
        toLock.add(walletService.getOrCreate(retailer));
        for (AgentAccount up = retailer.getParent(); up != null; up = up.getParent()) {
            if (up.getRole() != AccountRole.ADMIN) toLock.add(walletService.getOrCreate(up));
        }
        return walletService.lockAll(toLock);
    }

    private static void requireRetailer(AgentAccount account) {
        if (account.getRole() == AccountRole.ADMIN) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Platform Admins don't do business; bill payments are made by Super, Master, Distributor and Retailer accounts.");
        }
    }

    static String mask(String mobile) {
        String digits = mobile.replaceAll("\\D", "");
        return digits.length() < 4 ? "••••" : "••••••" + digits.substring(digits.length() - 4);
    }

    private static FetchedBillView view(BillFetch f, Biller biller, BigDecimal fee) {
        return new FetchedBillView(f.getFetchRef(), biller.getId(), biller.getName(), f.getCardLast4(), f.getMobileMasked(),
                f.getCustomerName(), f.getTotalDue(), f.getMinimumDue(), f.getDueDate(), fee, f.getExpiresAt());
    }
}
