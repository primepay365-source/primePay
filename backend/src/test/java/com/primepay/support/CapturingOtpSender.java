package com.primepay.support;

import com.primepay.auth.otp.OtpChannel;
import com.primepay.auth.otp.OtpSender;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Tests only: stands in for the email/SMS providers and keeps the last code sent to each destination, so tests
 * can complete the second sign-in step exactly like a user reading the email or SMS.
 */
@Component
@Profile("test")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CapturingOtpSender implements OtpSender {
    private final Map<String, String> lastCode = new ConcurrentHashMap<>();
    private final Map<String, OtpChannel> lastChannel = new ConcurrentHashMap<>();

    @Override
    public boolean supports(OtpChannel channel) {
        return true;
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public void send(OtpChannel channel, String destination, String code, String purpose, Duration validFor) {
        lastCode.put(destination, code);
        lastChannel.put(destination, channel);
    }

    /** The last code sent to a normalised email (lower-case) or mobile (+91XXXXXXXXXX). */
    public String codeFor(String destination) {
        String code = lastCode.get(destination);
        if (code == null) throw new AssertionError("No code was sent to " + destination);
        return code;
    }

    public OtpChannel channelFor(String destination) {
        return lastChannel.get(destination);
    }
}
