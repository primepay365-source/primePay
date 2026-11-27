package com.primepay.commission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One hierarchy level's share of a rule (rate in percent, or a fixed amount), with optional min/max. */
@Getter
@Entity
@Table(name = "pp_commission_distribution")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommissionShare {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** RETAILER, DISTRIBUTOR, MASTER_DISTRIBUTOR, SUPER_DISTRIBUTOR or PLATFORM */
    @Column(name = "level_role", nullable = false, length = 30)
    private String levelRole;

    @Column(precision = 9, scale = 6)
    private BigDecimal rate;

    @Column(name = "fixed_amount", precision = 19, scale = 2)
    private BigDecimal fixedAmount;

    @Column(name = "min_amount", precision = 19, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 19, scale = 2)
    private BigDecimal maxAmount;

    public CommissionShare(String levelRole, BigDecimal rate, BigDecimal fixedAmount, BigDecimal minAmount, BigDecimal maxAmount) {
        this.levelRole = levelRole;
        this.rate = rate;
        this.fixedAmount = fixedAmount;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
    }

    /** This level's commission on a transaction amount, rounded half-up to paise and clamped to min/max. */
    public BigDecimal calculate(String calcType, BigDecimal amount) {
        BigDecimal value;
        if ("FIXED".equals(calcType)) {
            value = fixedAmount == null ? BigDecimal.ZERO : fixedAmount;
        } else {
            value = rate == null ? BigDecimal.ZERO : amount.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        if (value.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        if (minAmount != null && value.compareTo(minAmount) < 0) {
            value = minAmount;
        }
        if (maxAmount != null && value.compareTo(maxAmount) > 0) {
            value = maxAmount;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
