package com.primepay.notification;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-app notifications are stored; email and SMS go through {@link MessageSender}.
 * Development uses a mock sender that only logs that a message would be sent (never its secret contents).
 */
@Service
public class NotificationService {
    public static final String MONEY = "MONEY";
    public static final String ACCOUNT = "ACCOUNT";
    public static final String SECURITY = "SECURITY";

    private final NotificationRepository repository;
    private final MessageSender sender;

    public NotificationService(NotificationRepository repository, MessageSender sender) {
        this.repository = repository;
        this.sender = sender;
    }

    @Transactional
    public void notify(Long accountId, String category, String title, String body, String link) {
        repository.save(new Notification(accountId, category, title, body, link, "APP"));
        sender.send(accountId, category, title);
    }

    @Transactional(readOnly = true)
    public Page<Notification> list(Long accountId, String category, boolean unreadOnly, Pageable pageable) {
        if (unreadOnly) {
            return repository.findByAccountIdAndReadAtIsNullOrderByCreatedAtDesc(accountId, pageable);
        }
        if (category != null && !category.isBlank()) {
            return repository.findByAccountIdAndCategoryOrderByCreatedAtDesc(accountId, category, pageable);
        }
        return repository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long accountId) {
        return repository.countByAccountIdAndReadAtIsNull(accountId);
    }

    @Transactional
    public void markRead(Long accountId, Long id) {
        repository.findById(id).filter(n -> n.getAccountId().equals(accountId)).ifPresent(Notification::markRead);
    }

    @Transactional
    public int markAllRead(Long accountId) {
        return repository.markAllRead(accountId, java.time.OffsetDateTime.now());
    }

    /** Email/SMS delivery. Real providers are connected later, with approved credentials. */
    public interface MessageSender {
        void send(Long accountId, String category, String title);
    }

    @Service
    static class MockMessageSender implements MessageSender {
        private static final Logger log = LoggerFactory.getLogger(MockMessageSender.class);

        @Override
        public void send(Long accountId, String category, String title) {
            log.debug("[mock SMS/email] account={} category={} title='{}'", accountId, category, title);
        }
    }
}
