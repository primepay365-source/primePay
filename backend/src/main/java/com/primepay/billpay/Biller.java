package com.primepay.billpay;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A bill issuer, e.g. a bank's credit card. Category CREDIT_CARD today; more categories can be added as data. */
@Getter
@Entity
@Table(name = "pp_billers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Biller {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(name = "biller_code", nullable = false, unique = true, length = 30)
    private String billerCode;

    @Column(nullable = false, length = 120)
    private String name;

    @Setter
    @Column(nullable = false)
    private boolean enabled;
}
