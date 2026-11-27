package com.primepay.addfunds;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One add-funds attempt through the payment gateway. Only the card network and last 4 digits are kept:
 * full card numbers and CVVs never reach PrimePay (in production the gateway's own page collects them).
 * Status: CREATED → OTP_SENT (card) / AWAITING_UPI (UPI) → SUCCESS or FAILED; EXPIRED after 10 minutes.
 */
@Getter
@Setter
@Entity
@Table(name = "pp_payment_sessions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_ref", nullable = false, unique = true, length = 40)
    private String sessionRef;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "category_code", nullable = false, length = 20)
    private String categoryCode;

    @Column(name = "card_network", nullable = false, length = 20)
    private String cardNetwork;

    @Column(name = "card_holder", nullable = false, length = 160)
    private String cardHolder;

    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Column(name = "payment_mode", length = 10)
    private String paymentMode;

    @Column(name = "upi_masked", length = 80)
    private String upiMasked;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal charge;

    @Column(name = "net_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "otp_hash", length = 100)
    private String otpHash;

    @Column(name = "otp_attempts", nullable = false)
    private int otpAttempts;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    @Column(name = "gateway_ref", length = 60)
    private String gatewayRef;

    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    PaymentSession(String sessionRef, Long accountId, String categoryCode, String cardNetwork, String cardHolder,
                   BigDecimal amount, BigDecimal charge, OffsetDateTime expiresAt) {
        this.sessionRef = sessionRef;
        this.accountId = accountId;
        this.categoryCode = categoryCode;
        this.cardNetwork = cardNetwork;
        this.cardHolder = cardHolder;
        this.amount = amount;
        this.charge = charge;
        this.netAmount = amount.subtract(charge);
        this.status = "CREATED";
        this.createdAt = OffsetDateTime.now();
        this.expiresAt = expiresAt;
    }

    boolean isOpen() {
        return ("CREATED".equals(status) || "OTP_SENT".equals(status) || "AWAITING_UPI".equals(status))
                && expiresAt.isAfter(OffsetDateTime.now());
    }
}
