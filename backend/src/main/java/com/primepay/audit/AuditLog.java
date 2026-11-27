package com.primepay.audit;

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

/** Append-only audit row. There are no setters: rows are never updated or deleted by the application. */
@Getter
@Entity
@Table(name = "pp_audit_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "actor_account_id")
    private Long actorAccountId;

    @Column(name = "actor_label", length = 160)
    private String actorLabel;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(name = "target_type", length = 40)
    private String targetType;

    @Column(name = "target_id", length = 80)
    private String targetId;

    @Column(nullable = false, length = 20)
    private String result;

    @Column(length = 4000)
    private String detail;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "request_id", length = 64)
    private String requestId;

    AuditLog(Long actorAccountId, String actorLabel, String action, String targetType, String targetId,
             String result, String detail, String ipAddress, String requestId) {
        this.occurredAt = OffsetDateTime.now();
        this.actorAccountId = actorAccountId;
        this.actorLabel = actorLabel;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.result = result;
        this.detail = detail == null || detail.length() <= 4000 ? detail : detail.substring(0, 4000);
        this.ipAddress = ipAddress;
        this.requestId = requestId;
    }
}
