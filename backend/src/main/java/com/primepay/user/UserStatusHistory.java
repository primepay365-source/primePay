package com.primepay.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "pp_user_status_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private AccountStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private AccountStatus toStatus;

    @Column(length = 500)
    private String reason;

    @Column(name = "changed_by_account_id")
    private Long changedByAccountId;

    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt;

    public UserStatusHistory(Long accountId, AccountStatus fromStatus, AccountStatus toStatus, String reason, Long changedBy) {
        this.accountId = accountId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.changedByAccountId = changedBy;
        this.changedAt = OffsetDateTime.now();
    }
}
