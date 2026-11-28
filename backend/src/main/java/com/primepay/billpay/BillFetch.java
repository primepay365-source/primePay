package com.primepay.billpay;

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

/** A fetched bill, valid for a short time. Paying refers to it, so the amount shown is the amount checked. */
@Getter
@Entity
@Table(name = "pp_bill_fetches")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillFetch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fetch_ref", nullable = false, unique = true, length = 40)
    private String fetchRef;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "biller_id", nullable = false)
    private Long billerId;

    @Column(name = "card_last4", nullable = false, length = 4)
    private String cardLast4;

    @Column(name = "mobile_masked", nullable = false, length = 20)
    private String mobileMasked;

    @Column(name = "customer_name", nullable = false, length = 160)
    private String customerName;

    @Column(name = "total_due", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalDue;

    @Column(name = "minimum_due", nullable = false, precision = 19, scale = 2)
    private BigDecimal minimumDue;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "provider_ref", length = 60)
    private String providerRef;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    BillFetch(String fetchRef, Long accountId, Long billerId, String cardLast4, String mobileMasked, String customerName,
              BigDecimal totalDue, BigDecimal minimumDue, LocalDate dueDate, String providerRef, OffsetDateTime expiresAt) {
        this.fetchRef = fetchRef;
        this.accountId = accountId;
        this.billerId = billerId;
        this.cardLast4 = cardLast4;
        this.mobileMasked = mobileMasked;
        this.customerName = customerName;
        this.totalDue = totalDue;
        this.minimumDue = minimumDue;
        this.dueDate = dueDate;
        this.providerRef = providerRef;
        this.createdAt = OffsetDateTime.now();
        this.expiresAt = expiresAt;
    }
}
