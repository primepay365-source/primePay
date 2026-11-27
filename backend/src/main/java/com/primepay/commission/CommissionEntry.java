package com.primepay.commission;

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

/** One earner's commission on one transaction. Platform rows have no earner account. */
@Getter
@Entity
@Table(name = "pp_commission_transactions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommissionEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_txn_id", nullable = false)
    private Long sourceTxnId;

    @Column(name = "credit_txn_id")
    private Long creditTxnId;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "earner_account_id")
    private Long earnerAccountId;

    @Column(name = "level_role", nullable = false, length = 30)
    private String levelRole;

    @Column(name = "base_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal baseAmount;

    @Column(name = "commission_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal commissionAmount;

    /** CREDITED, PLATFORM or REVERSED */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    CommissionEntry(Long sourceTxnId, Long creditTxnId, Long ruleId, Long earnerAccountId, String levelRole,
                    BigDecimal baseAmount, BigDecimal commissionAmount, String status) {
        this.sourceTxnId = sourceTxnId;
        this.creditTxnId = creditTxnId;
        this.ruleId = ruleId;
        this.earnerAccountId = earnerAccountId;
        this.levelRole = levelRole;
        this.baseAmount = baseAmount;
        this.commissionAmount = commissionAmount;
        this.status = status;
        this.createdAt = OffsetDateTime.now();
    }

    void markReversed() {
        this.status = "REVERSED";
    }
}
