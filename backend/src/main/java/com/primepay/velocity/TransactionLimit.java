package com.primepay.velocity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A limit for a role (default) or for one user (override, wins over the role default). */
@Getter
@Entity
@Table(name = "pp_transaction_limits")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransactionLimit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ROLE or USER */
    @Column(nullable = false, length = 10)
    private String scope;

    @Column(name = "role_code", length = 30)
    private String roleCode;

    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "service_code", nullable = false, length = 30)
    private String serviceCode;

    @Column(name = "min_txn", nullable = false, precision = 19, scale = 2)
    private BigDecimal minTxn;

    @Column(name = "max_txn", nullable = false, precision = 19, scale = 2)
    private BigDecimal maxTxn;

    @Column(name = "daily_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal dailyAmount;

    @Column(name = "monthly_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal monthlyAmount;

    @Column(name = "daily_count", nullable = false)
    private int dailyCount;

    @Column(name = "monthly_count", nullable = false)
    private int monthlyCount;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "set_by_account_id")
    private Long setByAccountId;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static TransactionLimit forUser(Long accountId, String serviceCode) {
        TransactionLimit limit = new TransactionLimit();
        limit.scope = "USER";
        limit.accountId = accountId;
        limit.serviceCode = serviceCode;
        return limit;
    }

    public void update(BigDecimal minTxn, BigDecimal maxTxn, BigDecimal dailyAmount, BigDecimal monthlyAmount,
                       int dailyCount, int monthlyCount, LocalDate validUntil, Long setBy) {
        this.minTxn = minTxn;
        this.maxTxn = maxTxn;
        this.dailyAmount = dailyAmount;
        this.monthlyAmount = monthlyAmount;
        this.dailyCount = dailyCount;
        this.monthlyCount = monthlyCount;
        this.validUntil = validUntil;
        this.setByAccountId = setBy;
        this.updatedAt = OffsetDateTime.now();
    }
}
