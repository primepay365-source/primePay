package com.primepay.ledger;

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

/** Immutable ledger line: insert-only, no setters. */
@Getter
@Entity
@Table(name = "pp_wallet_ledger")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_ref", nullable = false, unique = true, length = 40)
    private String entryRef;

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    /** DR or CR */
    @Column(nullable = false, length = 2)
    private String direction;

    @Column(name = "entry_type", nullable = false, length = 30)
    private String entryType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_before", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(length = 300)
    private String description;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    LedgerEntry(String entryRef, Long walletId, Long transactionId, String direction, String entryType, BigDecimal amount,
                BigDecimal balanceBefore, BigDecimal balanceAfter, String description) {
        this.entryRef = entryRef;
        this.walletId = walletId;
        this.transactionId = transactionId;
        this.direction = direction;
        this.entryType = entryType;
        this.amount = amount;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
        this.description = description;
        this.createdAt = OffsetDateTime.now();
    }
}
