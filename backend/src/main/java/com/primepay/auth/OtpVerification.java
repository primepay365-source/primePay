package com.primepay.auth;

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

/** One OTP challenge. Only a BCrypt hash of the code is stored; the code itself is never saved or logged. */
@Getter
@Entity
@Table(name = "pp_otp_verifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OtpVerification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(nullable = false, length = 30)
    private String purpose;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    OtpVerification(Long accountId, String purpose, String codeHash, OffsetDateTime expiresAt) {
        this.accountId = accountId;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.createdAt = OffsetDateTime.now();
    }

    boolean usable(OffsetDateTime now, int maxAttempts) {
        return consumedAt == null && expiresAt.isAfter(now) && attempts < maxAttempts;
    }

    void failedAttempt() {
        attempts++;
    }

    void consume() {
        consumedAt = OffsetDateTime.now();
    }
}
