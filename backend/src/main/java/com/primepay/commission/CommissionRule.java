package com.primepay.commission;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "pp_commission_rules")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommissionRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_code", nullable = false, unique = true, length = 30)
    private String ruleCode;

    @Column(name = "service_code", nullable = false, length = 30)
    private String serviceCode;

    /** PERCENT or FIXED */
    @Column(name = "calc_type", nullable = false, length = 10)
    private String calcType;

    @Column(name = "slab_from", precision = 19, scale = 2)
    private BigDecimal slabFrom;

    @Column(name = "slab_to", precision = 19, scale = 2)
    private BigDecimal slabTo;

    /** Date-only; stored as an Oracle TIMESTAMP column. */
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Setter
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    /** ACTIVE or INACTIVE */
    @Setter
    @Column(nullable = false, length = 10)
    private String status;

    @Column(name = "created_by_account_id")
    private Long createdByAccountId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "rule_id", nullable = false)
    private List<CommissionShare> shares = new ArrayList<>();

    public CommissionRule(String ruleCode, String serviceCode, String calcType, BigDecimal slabFrom, BigDecimal slabTo,
                          LocalDate effectiveFrom, LocalDate effectiveTo, Long createdBy) {
        this.ruleCode = ruleCode;
        this.serviceCode = serviceCode;
        this.calcType = calcType;
        this.slabFrom = slabFrom;
        this.slabTo = slabTo;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.status = "ACTIVE";
        this.createdByAccountId = createdBy;
        this.createdAt = OffsetDateTime.now();
    }

    public boolean appliesTo(BigDecimal amount, LocalDate date) {
        return "ACTIVE".equals(status)
                && !date.isBefore(effectiveFrom)
                && (effectiveTo == null || !date.isAfter(effectiveTo))
                && (slabFrom == null || amount.compareTo(slabFrom) >= 0)
                && (slabTo == null || amount.compareTo(slabTo) <= 0);
    }
}
