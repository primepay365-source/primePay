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

/**
 * A one-time code, stored only as a BCrypt hash. LOGIN codes are the second sign-in step and are addressed by an
 * opaque {@code challengeRef}; PASSWORD_RESET codes by account.
 */
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

    @Column(name = "challenge_ref", length = 64, unique = true)
    private String challengeRef;

    @Column(length = 10)
    private String channel;

    @Column(name = "destination_masked", length = 80)
    private String destinationMasked;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "resend_count", nullable = false)
    private int resendCount;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

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

    static OtpVerification loginChallenge(Long accountId, String challengeRef, String channel, String destinationMasked,
                                          String codeHash, OffsetDateTime expiresAt, String ipAddress) {
        OtpVerification otp = new OtpVerification(accountId, "LOGIN", codeHash, expiresAt);
        otp.challengeRef = challengeRef;
        otp.channel = channel;
        otp.destinationMasked = destinationMasked;
        otp.ipAddress = ipAddress;
        return otp;
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

    /** A fresh code for the same challenge: wrong-attempt count and expiry start again. */
    void reissue(String newCodeHash, OffsetDateTime newExpiry) {
        codeHash = newCodeHash;
        expiresAt = newExpiry;
        attempts = 0;
        resendCount++;
        createdAt = OffsetDateTime.now();
    }
}
