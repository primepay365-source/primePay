package com.primepay.auth;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {
    Optional<OtpVerification> findFirstByAccountIdAndPurposeOrderByIdDesc(Long accountId, String purpose);

    /** Locked so two verify requests for the same code can't both succeed. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OtpVerification> findByChallengeRefAndPurpose(String challengeRef, String purpose);

    long countByAccountIdAndPurposeAndCreatedAtAfter(Long accountId, String purpose, OffsetDateTime after);
}
