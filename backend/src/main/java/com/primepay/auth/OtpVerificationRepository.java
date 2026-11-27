package com.primepay.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {
    Optional<OtpVerification> findFirstByAccountIdAndPurposeOrderByIdDesc(Long accountId, String purpose);
}
