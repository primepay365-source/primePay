package com.primepay.user;

import com.primepay.common.ContactNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Emails a new user their sign-in details: role, user ID, username, sign-in email/mobile and password.
 * Sent through the same SMTP server as sign-in codes (MAIL_HOST, MAIL_FROM, ...), after the account is committed and
 * off the request thread. If SMTP is not configured or delivery fails, the user is still created and a warning is
 * logged; the password itself is never logged.
 */
@Service
public class WelcomeMailService {
    private static final Logger logger = LoggerFactory.getLogger(WelcomeMailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String host;
    private final String from;
    private final String signInUrl;

    public WelcomeMailService(ObjectProvider<JavaMailSender> mailSender, @Value("${spring.mail.host:}") String host,
                              @Value("${app.otp.email.from:}") String from,
                              @Value("${app.web.sign-in-url:http://localhost:5173/login}") String signInUrl) {
        this.mailSender = mailSender;
        this.host = host;
        this.from = from;
        this.signInUrl = signInUrl;
    }

    public boolean isConfigured() {
        return !host.isBlank() && !from.isBlank() && mailSender.getIfAvailable() != null;
    }

    @Async("mailExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserCreated(UserCreatedEvent event) {
        String to = ContactNormalizer.maskEmail(event.email());
        if (!isConfigured()) {
            logger.warn("Welcome email for {} not sent: SMTP is not configured (set MAIL_HOST and MAIL_FROM)", event.accountCode());
            return;
        }
        try {
            mailSender.getObject().send(message(event));
            logger.info("Welcome email with sign-in details sent to {} for {}", to, event.accountCode());
        } catch (MailException e) {
            logger.warn("Welcome email to {} for {} failed: {}", to, event.accountCode(), e.getClass().getSimpleName());
        }
    }

    SimpleMailMessage message(UserCreatedEvent event) {
        String role = AccountService.label(event.role());
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(event.email());
        message.setSubject("Your PrimePay " + role + " account is ready");
        message.setText("""
                Hello %s,

                A PrimePay account has been created for you.

                  Role:      %s
                  User ID:   %s
                  Username:  %s
                  Sign in with: %s%s
                  Password:  %s

                Sign in at %s
                After your password you'll get a one-time code by email or SMS to finish signing in.

                For your security, change this password after you first sign in (menu: Change password),
                and delete this email. PrimePay staff will never ask for your password or codes.
                """.formatted(event.displayName(), role, event.accountCode(), event.username(), event.email(),
                event.mobile() == null ? "" : " or " + event.mobile(), event.password(), signInUrl));
        return message;
    }
}
