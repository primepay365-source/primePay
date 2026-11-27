package com.primepay.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Entity
@Table(name = "pp_service_master")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ServiceDefinition {
    public static final String WALLET_LOAD = "WALLET_LOAD";
    public static final String WALLET_TRANSFER = "WALLET_TRANSFER";
    public static final String PAYOUT = "PAYOUT";
    public static final String BILL_PAYMENT = "BILL_PAYMENT";
    public static final String ADD_FUNDS = "ADD_FUNDS";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_code", nullable = false, unique = true, length = 30)
    private String serviceCode;

    @Column(nullable = false, length = 80)
    private String name;

    @Setter
    @Column(name = "fee_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal feeAmount;

    @Setter
    @Column(nullable = false)
    private boolean enabled;

    @Column(length = 80)
    private String provider;
}
