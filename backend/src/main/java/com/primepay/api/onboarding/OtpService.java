package com.primepay.api.onboarding;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OtpService {
    private static final Duration OTP_LIFETIME = Duration.ofMinutes(5);
    private static final Duration REQUEST_INTERVAL = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 5;

    private final ConcurrentMap<String, Challenge> challenges = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final boolean developmentMode;

    public OtpService(@Value("${app.onboarding.otp.dev-mode:false}") boolean developmentMode) {
        this.developmentMode = developmentMode;
    }

    public OtpResponse request(Long accountId, String mobile) {
        if (!developmentMode) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "OTP delivery is not configured for this environment");
        }

        String normalizedMobile = key(accountId, mobile);
        Instant now = Instant.now();
        Challenge current = challenges.get(normalizedMobile);
        if (current != null && current.requestedAt().plus(REQUEST_INTERVAL).isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Wait one minute before requesting another OTP");
        }

        String code = "%06d".formatted(random.nextInt(1_000_000));
        challenges.put(normalizedMobile, new Challenge(code, now, now.plus(OTP_LIFETIME), 0, false));
        return new OtpResponse("Development OTP generated. It is shown only in the local development UI.", code);
    }

    public void verify(Long accountId, String mobile, String code) {
        String key = key(accountId, mobile);
        Challenge challenge = challenges.get(key);
        if (challenge == null || challenge.expiresAt().isBefore(Instant.now()) || challenge.attempts() >= MAX_ATTEMPTS) {
            challenges.remove(key);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP is missing, expired, or locked");
        }

        if (!challenge.code().equals(code)) {
            int attempts = challenge.attempts() + 1;
            if (attempts >= MAX_ATTEMPTS) {
                challenges.remove(key);
            } else {
                challenges.put(key, new Challenge(challenge.code(), challenge.requestedAt(), challenge.expiresAt(), attempts, false));
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP is incorrect");
        }

        challenges.put(key, new Challenge(challenge.code(), challenge.requestedAt(), challenge.expiresAt(), challenge.attempts(), true));
    }

    public void requireVerified(Long accountId, String mobile) {
        String key = key(accountId, mobile);
        Challenge challenge = challenges.get(key);
        if (challenge == null || !challenge.verified() || challenge.expiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verify this mobile number with OTP before submitting");
        }
        challenges.remove(key);
    }

    private String key(Long accountId, String mobile) {
        return accountId + ":" + mobile.trim();
    }

    private record Challenge(String code, Instant requestedAt, Instant expiresAt, int attempts, boolean verified) {
    }
}
