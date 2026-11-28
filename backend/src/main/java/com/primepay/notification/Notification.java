package com.primepay.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "pp_notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    /** MONEY, ACCOUNT or SECURITY */
    @Column(nullable = false, length = 20)
    private String category;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 1000)
    private String body;

    @Column(length = 200)
    private String link;

    @Column(length = 40)
    private String channels;

    @Column(name = "read_at")
    private OffsetDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    Notification(Long accountId, String category, String title, String body, String link, String channels) {
        this.accountId = accountId;
        this.category = category;
        this.title = title;
        this.body = body;
        this.link = link;
        this.channels = channels;
        this.createdAt = OffsetDateTime.now();
    }

    void markRead() {
        if (readAt == null) {
            readAt = OffsetDateTime.now();
        }
    }
}
