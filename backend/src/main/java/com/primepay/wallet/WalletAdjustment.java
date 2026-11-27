package com.primepay.wallet;

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

/** Maker-checker request for a manual wallet credit or debit. The checker must be a different admin. */
@Getter
@Entity
@Table(name = "pp_wallet_adjustments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalletAdjustment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_ref", nullable = false, unique = true, length = 30)
    private String requestRef;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    /** CR or DR */
    @Column(nullable = false, length = 2)
    private String direction;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 80)
    private String reason;

    @Column(length = 500)
    private String details;

    /** PENDING, APPROVED or REJECTED */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "requested_by_account_id", nullable = false)
    private Long requestedByAccountId;

    @Column(name = "decided_by_account_id")
    private Long decidedByAccountId;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    WalletAdjustment(String requestRef, Long accountId, String direction, BigDecimal amount, String reason, String details, Long requestedBy) {
        this.requestRef = requestRef;
        this.accountId = accountId;
        this.direction = direction;
        this.amount = amount;
        this.reason = reason;
        this.details = details;
        this.status = "PENDING";
        this.requestedByAccountId = requestedBy;
        this.createdAt = OffsetDateTime.now();
    }

    void decide(boolean approved, Long checkerId, Long transactionId) {
        this.status = approved ? "APPROVED" : "REJECTED";
        this.decidedByAccountId = checkerId;
        this.decidedAt = OffsetDateTime.now();
        this.transactionId = transactionId;
    }
}
