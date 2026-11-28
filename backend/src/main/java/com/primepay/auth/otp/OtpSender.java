package com.primepay.auth.otp;

import java.time.Duration;

/**
 * Sends a one-time code through one provider. Implementations: SMTP email, MSG91 SMS. The first sender (by
 * {@code @Order}) that supports a channel and is configured is used. A sender must never log the code.
 */
public interface OtpSender {

    boolean supports(OtpChannel channel);

    /** True when this provider has the credentials it needs. */
    boolean isConfigured();

    /**
     * Delivers the code or throws {@link OtpDeliveryException}. {@code destination} is a normalised email
     * address or +91 mobile number.
     */
    void send(OtpChannel channel, String destination, String code, String purpose, Duration validFor);

    /** Provider-level failure (bad credentials, provider down). The message is logged, not shown to users. */
    class OtpDeliveryException extends RuntimeException {
        public OtpDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }

        public OtpDeliveryException(String message) {
            super(message);
        }
    }
}
