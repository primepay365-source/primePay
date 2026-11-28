package com.primepay.auth.otp;

import com.primepay.common.ContactNormalizer;
import com.primepay.common.error.BusinessException;
import com.primepay.common.error.ErrorCode;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Picks the provider for a channel and sends the code. When no provider is configured the code is written to
 * the server log only if {@code app.otp.log-delivery-allowed=true} (dev profile); otherwise the request fails,
 * so production can never silently skip delivery.
 */
@Service
public class OtpDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(OtpDeliveryService.class);

    private final List<OtpSender> senders;
    private final boolean logDeliveryAllowed;

    public OtpDeliveryService(List<OtpSender> senders, @Value("${app.otp.log-delivery-allowed:false}") boolean logDeliveryAllowed) {
        this.senders = senders;
        this.logDeliveryAllowed = logDeliveryAllowed;
    }

    public boolean canDeliver(OtpChannel channel) {
        return logDeliveryAllowed || senders.stream().anyMatch(s -> s.supports(channel) && s.isConfigured());
    }

    public void deliver(OtpChannel channel, String destination, String code, String purpose, Duration validFor) {
        OtpSender sender = senders.stream().filter(s -> s.supports(channel) && s.isConfigured()).findFirst().orElse(null);
        if (sender == null) {
            if (logDeliveryAllowed) {
                log.warn("DEV ONLY - no {} provider configured. {} code for {}: {}", channel, purpose, mask(channel, destination), code);
                return;
            }
            log.error("OTP not sent: no {} provider is configured", channel);
            throw new BusinessException(ErrorCode.OTP_DELIVERY_FAILED, channel == OtpChannel.EMAIL
                    ? "Codes can't be sent by email right now. Sign in with your mobile number, or contact support."
                    : "Codes can't be sent by SMS right now. Sign in with your email address, or contact support.");
        }
        try {
            sender.send(channel, destination, code, purpose, validFor);
            log.info("{} code sent by {} to {}", purpose, channel, mask(channel, destination));
        } catch (OtpSender.OtpDeliveryException e) {
            log.error("{} code to {} failed: {}", purpose, mask(channel, destination), e.getMessage());
            throw new BusinessException(ErrorCode.OTP_DELIVERY_FAILED);
        }
    }

    public static String mask(OtpChannel channel, String destination) {
        return channel == OtpChannel.EMAIL ? ContactNormalizer.maskEmail(destination) : ContactNormalizer.maskMobile(destination);
    }
}
