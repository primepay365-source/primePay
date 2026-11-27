package com.primepay.beneficiary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A retailer's saved bank account for withdrawals. The account number is stored encrypted (AES-GCM) with an
 * HMAC for duplicate checks; only the last 4 digits are ever shown. Status VERIFIED / REJECTED / REMOVED.
 */
@Getter
@Setter
@Entity
@Table(name = "pp_beneficiaries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Beneficiary {
    public static final String VERIFIED = "VERIFIED";
    public static final String REJECTED = "REJECTED";
    public static final String REMOVED = "REMOVED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "holder_name", nullable = false, length = 160)
    private String holderName;

    @Column(name = "verified_name", length = 160)
    private String verifiedName;

    @Column(name = "account_number_enc", nullable = false, length = 200)
    private String accountNumberEnc;

    @Column(name = "account_hash", nullable = false, length = 64)
    private String accountHash;

    @Column(name = "account_last4", nullable = false, length = 4)
    private String accountLast4;

    @Column(nullable = false, length = 11)
    private String ifsc;

    @Column(name = "bank_name", nullable = false, length = 120)
    private String bankName;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    Beneficiary(Long accountId, String holderName, String accountNumberEnc, String accountHash, String accountLast4, String ifsc, String bankName) {
        this.accountId = accountId;
        this.holderName = holderName;
        this.accountNumberEnc = accountNumberEnc;
        this.accountHash = accountHash;
        this.accountLast4 = accountLast4;
        this.ifsc = ifsc;
        this.bankName = bankName;
        this.status = REJECTED;
        this.createdAt = OffsetDateTime.now();
    }
}
