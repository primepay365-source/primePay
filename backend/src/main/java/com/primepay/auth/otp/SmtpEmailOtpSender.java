package com.primepay.auth.otp;

import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Email codes through any SMTP server (Gmail with an app password, Outlook, Zoho, Amazon SES SMTP, ...).
 * Configured with MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD and MAIL_FROM.
 */
@Component
@Order(10)
public class SmtpEmailOtpSender implements OtpSender {
    private final ObjectProvider<JavaMailSender> mailSender;
    private final String host;
    private final String from;

    public SmtpEmailOtpSender(ObjectProvider<JavaMailSender> mailSender, @Value("${spring.mail.host:}") String host,
                              @Value("${app.otp.email.from:}") String from) {
        this.mailSender = mailSender;
        this.host = host;
        this.from = from;
    }

    @Override
    public boolean supports(OtpChannel channel) {
        return channel == OtpChannel.EMAIL;
    }

    @Override
    public boolean isConfigured() {
        return !host.isBlank() && !from.isBlank() && mailSender.getIfAvailable() != null;
    }

    @Override
    public void send(OtpChannel channel, String destination, String code, String purpose, Duration validFor) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(destination);
        message.setSubject("Your PrimePay " + purpose + " code: " + code);
        message.setText("""
                Your PrimePay %s code is %s

                It expires in %d minutes. Do not share it with anyone; PrimePay staff will never ask for it.
                If you did not try to sign in, change your password and contact support.
                """.formatted(purpose, code, Math.max(1, validFor.toMinutes())));
        try {
            mailSender.getObject().send(message);
        } catch (MailException e) {
            throw new OtpDeliveryException("SMTP delivery failed: " + e.getClass().getSimpleName(), e);
        }
    }
}
