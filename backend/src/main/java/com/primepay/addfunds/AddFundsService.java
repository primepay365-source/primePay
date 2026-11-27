package com.primepay.addfunds;

import com.primepay.admin.ServiceDefinition;
import com.primepay.admin.ServiceDefinitionRepository;
import com.primepay.audit.AuditService;
import com.primepay.common.Money;
import com.primepay.common.error.BusinessException;
import com.primepay.common.error.ErrorCode;
import com.primepay.ledger.LedgerService;
import com.primepay.notification.NotificationService;
import com.primepay.transaction.FinancialTransaction;
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
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Add funds (pay-in) to the main wallet through a payment gateway:
 * choose gateway category → card type, holder, amount (within the category's limits) → payment mode
 * (card or UPI) → OTP for card / approval for UPI → SUCCESS credits amount minus the category charge.
 * A declined or abandoned attempt moves no money but is recorded for history.
 */
@Service
public class AddFundsService {
    private static final Set<String> NETWORKS = Set.of("VISA", "MASTERCARD", "RUPAY");
    private static final Duration SESSION_LIFETIME = Duration.ofMinutes(10);
    private static final int MAX_OTP_ATTEMPTS = 3;

    private final GatewayCategoryRepository categories;
    private final PaymentSessionRepository sessions;
    private final PaymentGateway gateway;
    private final AgentAccountRepository accounts;
    private final WalletService walletService;
    private final LedgerService ledger;
    private final TransactionRecorder recorder;
    private final LimitService limits;
    private final ServiceDefinitionRepository services;
    private final PasswordEncoder encoder;
    private final NotificationService notifications;
    private final AuditService audit;
    private final boolean devOtp;
    private final SecureRandom random = new SecureRandom();

    public AddFundsService(GatewayCategoryRepository categories, PaymentSessionRepository sessions, PaymentGateway gateway,
                           AgentAccountRepository accounts, WalletService walletService, LedgerService ledger, TransactionRecorder recorder,
                           LimitService limits, ServiceDefinitionRepository services, PasswordEncoder encoder,
                           NotificationService notifications, AuditService audit,
                           @Value("${app.onboarding.otp.dev-mode:false}") boolean devOtp) {
        this.categories = categories;
        this.sessions = sessions;
        this.gateway = gateway;
        this.accounts = accounts;
        this.walletService = walletService;
        this.ledger = ledger;
        this.recorder = recorder;
        this.limits = limits;
        this.services = services;
        this.encoder = encoder;
        this.notifications = notifications;
        this.audit = audit;
        this.devOtp = devOtp;
    }

    public record CategoryView(Long id, String code, String name, BigDecimal minAmount, BigDecimal maxAmount, BigDecimal chargePercent, boolean enabled) {
    }

    public record SessionView(String sessionRef, String categoryCode, String categoryName, String cardNetwork, String cardHolder,
                              String cardLast4, String paymentMode, String upiMasked, BigDecimal amount, BigDecimal charge,
                              BigDecimal netAmount, String status, String failureReason, Long transactionId, String referenceNo,
                              OffsetDateTime expiresAt, String devOtp) {
    }

    @Transactional(readOnly = true)
    public List<CategoryView> categories(boolean includeDisabled) {
        return categories.findAllByOrderByIdAsc().stream().filter(c -> includeDisabled || c.isEnabled()).map(AddFundsService::view).toList();
    }

    @Transactional
    public CategoryView updateCategory(AgentAccount admin, Long id, BigDecimal min, BigDecimal max, BigDecimal chargePercent, boolean enabled) {
        if (admin.getRole() != AccountRole.ADMIN) throw new BusinessException(ErrorCode.ACCESS_DENIED, "Only a Platform Admin can change gateway categories.");
        if (max.compareTo(min) < 0) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Maximum must be at least the minimum.");
        if (chargePercent.signum() < 0 || chargePercent.compareTo(BigDecimal.TEN) > 0) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Charge must be between 0% and 10%.");
        GatewayCategory c = categories.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Category not found."));
        c.setMinAmount(Money.of(min));
        c.setMaxAmount(Money.of(max));
        c.setChargePercent(chargePercent);
        c.setEnabled(enabled);
        audit.record(admin, "GATEWAY_CATEGORY_UPDATE", "GATEWAY_CATEGORY", c.getCode(), "min=" + min + " max=" + max + " charge%=" + chargePercent + " enabled=" + enabled);
        return view(c);
    }

    /** Step 1: category, card type, card holder and amount. Returns the charge and the amount that will reach the wallet. */
    @Transactional
    public SessionView start(AgentAccount actor, String categoryCode, String cardNetwork, String cardHolder, BigDecimal rawAmount) {
        AgentAccount retailer = requireRetailer(actor);
        services.requireEnabled(ServiceDefinition.ADD_FUNDS);
        GatewayCategory category = categories.findByCode(categoryCode).filter(GatewayCategory::isEnabled)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Choose a gateway category."));
        if (!NETWORKS.contains(cardNetwork)) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Card type must be Visa, Mastercard or RuPay.");
        BigDecimal amount = Money.of(rawAmount);
        if (amount.compareTo(category.getMinAmount()) < 0 || amount.compareTo(category.getMaxAmount()) > 0) {
            throw new BusinessException(ErrorCode.LIMIT_EXCEEDED, category.getName() + " allows " + Money.format(category.getMinAmount())
                    + " to " + Money.format(category.getMaxAmount()) + " per payment.");
        }
        BigDecimal charge = category.chargeFor(amount);
        PaymentSession session = sessions.save(new PaymentSession("PS" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(),
                retailer.getId(), category.getCode(), cardNetwork, cardHolder.trim(), amount, charge, OffsetDateTime.now().plus(SESSION_LIFETIME)));
        return view(session, category, null, null);
    }

    /**
     * Step 2: payment mode on the (simulated) gateway page. CARD sends an OTP to the cardholder;
     * UPI sends a collect request to approve in the UPI app.
     */
    @Transactional
    public SessionView choosePaymentMode(AgentAccount actor, String sessionRef, String mode, String cardLast4, String upiId) {
        PaymentSession s = requireOpen(actor, sessionRef);
        String plainOtp = null;
        if ("CARD".equals(mode)) {
            if (cardLast4 == null || !cardLast4.matches("[0-9]{4}")) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Card details are incomplete.");
            s.setPaymentMode("CARD");
            s.setCardLast4(cardLast4);
            plainOtp = String.format("%06d", random.nextInt(1_000_000));
            s.setOtpHash(encoder.encode(plainOtp));
            s.setOtpAttempts(0);
            s.setStatus("OTP_SENT");
        } else if ("UPI".equals(mode)) {
            if (upiId == null || !upiId.matches("[A-Za-z0-9._-]{2,64}@[A-Za-z]{2,32}")) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Enter a valid UPI ID, e.g. name@bank.");
            s.setPaymentMode("UPI");
            s.setUpiMasked(maskUpi(upiId));
            s.setStatus("AWAITING_UPI");
        } else {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Payment mode must be CARD or UPI.");
        }
        return view(s, categories.findByCode(s.getCategoryCode()).orElseThrow(), null, devOtp ? plainOtp : null);
    }

    /** Step 3: OTP (card) or UPI approval → gateway result → wallet credit on success. Safe to call again. */
    @Transactional(noRollbackFor = BusinessException.class)
    public SessionView complete(AgentAccount actor, String sessionRef, String otp, String upiId) {
        AgentAccount retailer = requireRetailer(actor);
        // Lock the wallet before reading the session so two concurrent completions cannot both credit.
        Wallet wallet = walletService.lockOne(walletService.getOrCreate(retailer));
        PaymentSession s = sessions.findBySessionRef(sessionRef).filter(x -> x.getAccountId().equals(retailer.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Payment not found."));
        GatewayCategory category = categories.findByCode(s.getCategoryCode()).orElseThrow();
        if ("SUCCESS".equals(s.getStatus()) || "FAILED".equals(s.getStatus())) {
            return view(s, category, reference(s), null);
        }
        if (!s.isOpen()) {
            s.setStatus("EXPIRED");
            throw new BusinessException(ErrorCode.INVALID_STATE, "This payment session expired. Start again.");
        }
        if ("OTP_SENT".equals(s.getStatus())) {
            if (otp == null || !encoder.matches(otp, s.getOtpHash())) {
                s.setOtpAttempts(s.getOtpAttempts() + 1);
                if (s.getOtpAttempts() >= MAX_OTP_ATTEMPTS) {
                    fail(retailer, s, "Too many wrong OTPs");
                    return view(s, category, reference(s), null);
                }
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The OTP is incorrect. " + (MAX_OTP_ATTEMPTS - s.getOtpAttempts()) + " attempts left.");
            }
        } else if (!"AWAITING_UPI".equals(s.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Choose a payment mode first.");
        }

        // Limits are checked before the gateway is asked to charge, so a refused top-up never charges the card.
        limits.check(retailer, ServiceDefinition.ADD_FUNDS, s.getAmount());
        PaymentGateway.Charge result = gateway.charge(s.getSessionRef(), s.getPaymentMode(), s.getCardNetwork(), s.getCardLast4(), upiId, s.getAmount());
        s.setGatewayRef(result.gatewayRef());
        if (!result.success()) {
            fail(retailer, s, result.reason());
            return view(s, category, reference(s), null);
        }

        FinancialTransaction txn = newTxn(retailer, s, category);
        ledger.credit(wallet, txn, s.getNetAmount(), "ADD_FUNDS", "Add funds via " + category.getName() + " gateway (" + describe(s) + "), after "
                + Money.format(s.getCharge()) + " charge");
        recorder.move(txn, TxnStatus.SUCCESS, "Gateway approved · ref " + result.gatewayRef(), retailer.getId());
        s.setStatus("SUCCESS");
        s.setTransactionId(txn.getId());
        s.setOtpHash(null);
        audit.record(retailer, "ADD_FUNDS", "TRANSACTION", txn.getReferenceNo(), "amount=" + s.getAmount() + " charge=" + s.getCharge() + " net=" + s.getNetAmount());
        notifications.notify(retailer.getId(), NotificationService.MONEY, "Funds added",
                Money.format(s.getNetAmount()) + " was added to your main wallet (" + Money.format(s.getAmount()) + " paid, "
                        + Money.format(s.getCharge()) + " charge). Ref " + txn.getReferenceNo() + ".", "/transactions/" + txn.getId());
        return view(s, category, txn.getReferenceNo(), null);
    }

    private void fail(AgentAccount retailer, PaymentSession s, String reason) {
        GatewayCategory category = categories.findByCode(s.getCategoryCode()).orElseThrow();
        FinancialTransaction txn = newTxn(retailer, s, category);
        txn.setFailureReason(reason);
        recorder.move(txn, TxnStatus.FAILED, reason + "; nothing was added to the wallet", retailer.getId());
        s.setStatus("FAILED");
        s.setFailureReason(reason);
        s.setTransactionId(txn.getId());
        s.setOtpHash(null);
        audit.record(retailer, "ADD_FUNDS", "TRANSACTION", txn.getReferenceNo(), "declined: " + reason, AuditService.FAILED);
        notifications.notify(retailer.getId(), NotificationService.MONEY, "Add funds failed",
                Money.format(s.getAmount()) + " was not added: " + reason + ". Your card or UPI account was not charged.", "/transactions/" + txn.getId());
    }

    private FinancialTransaction newTxn(AgentAccount retailer, PaymentSession s, GatewayCategory category) {
        FinancialTransaction txn = recorder.create(TxnType.ADD_FUNDS, ServiceDefinition.ADD_FUNDS, retailer.getId(), s.getAmount(), s.getCharge(),
                "ADDF-" + s.getSessionRef(), TransactionRecorder.hash("ADDF", s.getSessionRef()), "Gateway payment " + s.getSessionRef());
        txn.setTargetAccountId(retailer.getId());
        txn.setPayoutMode(s.getPaymentMode());
        txn.setCardNetwork(s.getCardNetwork());
        txn.setCardLast4(s.getCardLast4());
        txn.setGatewayCategory(category.getCode());
        txn.setCustomerName(s.getCardHolder());
        txn.setExternalRef(s.getGatewayRef());
        txn.setRemarks(category.getName() + " gateway · " + describe(s) + " · net " + s.getNetAmount().toPlainString());
        return txn;
    }

    private String reference(PaymentSession s) {
        return s.getTransactionId() == null ? null : recorderReference(s.getTransactionId());
    }

    private String recorderReference(Long txnId) {
        return recorder.referenceOf(txnId);
    }

    private PaymentSession requireOpen(AgentAccount actor, String ref) {
        AgentAccount retailer = requireRetailer(actor);
        PaymentSession s = sessions.findBySessionRef(ref).filter(x -> x.getAccountId().equals(retailer.getId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Payment not found."));
        if (!s.isOpen()) throw new BusinessException(ErrorCode.INVALID_STATE, "This payment session is closed or expired. Start again.");
        return s;
    }

    private AgentAccount requireRetailer(AgentAccount actor) {
        AgentAccount account = accounts.findById(actor.getId()).orElseThrow();
        if (account.getRole() == AccountRole.ADMIN) throw new BusinessException(ErrorCode.ACCESS_DENIED, "Platform Admins don't do business.");
        if (!account.isActive()) throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE, "Your account is not active.");
        return account;
    }

    private static String describe(PaymentSession s) {
        return "UPI".equals(s.getPaymentMode()) ? "UPI " + s.getUpiMasked() : s.getCardNetwork() + " card ••" + s.getCardLast4();
    }

    static String maskUpi(String upi) {
        int at = upi.indexOf('@');
        String name = upi.substring(0, at);
        return (name.length() <= 2 ? name.charAt(0) + "•" : name.substring(0, 2) + "•••") + upi.substring(at);
    }

    private static CategoryView view(GatewayCategory c) {
        return new CategoryView(c.getId(), c.getCode(), c.getName(), c.getMinAmount(), c.getMaxAmount(), c.getChargePercent(), c.isEnabled());
    }

    private static SessionView view(PaymentSession s, GatewayCategory c, String referenceNo, String devOtp) {
        return new SessionView(s.getSessionRef(), c.getCode(), c.getName(), s.getCardNetwork(), s.getCardHolder(), s.getCardLast4(),
                s.getPaymentMode(), s.getUpiMasked(), s.getAmount(), s.getCharge(), s.getNetAmount(), s.getStatus(), s.getFailureReason(),
                s.getTransactionId(), referenceNo, s.getExpiresAt(), devOtp);
    }
}
