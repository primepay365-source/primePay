package com.primepay.support;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.stereotype.Component;

/** Tests only: stands in for the SMTP server and keeps every plain-text email "sent", so tests can read them. */
@Component
@Primary
@Profile("test")
public class CapturingMailSender implements JavaMailSender {
    private final List<SimpleMailMessage> sent = new CopyOnWriteArrayList<>();

    /** The latest email to this address, waiting briefly because emails are sent in the background. */
    public SimpleMailMessage awaitLastTo(String address) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            for (int j = sent.size() - 1; j >= 0; j--) {
                String[] to = sent.get(j).getTo();
                if (to != null && List.of(to).contains(address)) return sent.get(j);
            }
            Thread.sleep(50);
        }
        throw new AssertionError("No email was sent to " + address);
    }

    public List<SimpleMailMessage> sent() {
        return sent;
    }

    @Override
    public void send(SimpleMailMessage... messages) {
        sent.addAll(List.of(messages));
    }

    @Override
    public MimeMessage createMimeMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    @Override
    public MimeMessage createMimeMessage(InputStream contentStream) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void send(MimeMessage... mimeMessages) {
        throw new UnsupportedOperationException("Tests capture plain-text emails only");
    }

    @Override
    public void send(MimeMessagePreparator... mimeMessagePreparators) {
        throw new UnsupportedOperationException("Tests capture plain-text emails only");
    }
}
