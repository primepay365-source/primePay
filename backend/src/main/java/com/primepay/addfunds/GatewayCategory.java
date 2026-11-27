package com.primepay.addfunds;

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
import lombok.Setter;

/** Payment-gateway category (Ecom, Edu, Travel) with its own amount limits and charge percentage. */
@Getter
@Setter
@Entity
@Table(name = "pp_gateway_categories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GatewayCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "min_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "max_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal maxAmount;

    @Column(name = "charge_percent", nullable = false, precision = 9, scale = 6)
    private BigDecimal chargePercent;

    @Column(nullable = false)
    private boolean enabled;

    public BigDecimal chargeFor(BigDecimal amount) {
        return amount.multiply(chargePercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
