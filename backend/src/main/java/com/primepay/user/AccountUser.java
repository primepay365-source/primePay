package com.primepay.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;

@Getter
@Entity
@Table(name = "account_user")
public class AccountUser {
    public static final int MAX_FAILED_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private AgentAccount account;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    @Column(name = "password_changed_at")
    private OffsetDateTime passwordChangedAt;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    protected AccountUser() {
    }

    public AccountUser(String username, String passwordHash, AgentAccount account) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.account = account;
        this.active = true;
        this.passwordChangedAt = OffsetDateTime.now();
    }

    public boolean isLocked(OffsetDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Records a wrong password; returns true when this attempt locks the account. */
    public boolean registerFailedLogin(OffsetDateTime now, java.time.Duration lockFor) {
        failedAttempts++;
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            lockedUntil = now.plus(lockFor);
            failedAttempts = 0;
            return true;
        }
        return false;
    }

    public int remainingAttempts() {
        return MAX_FAILED_ATTEMPTS - failedAttempts;
    }

    public void registerSuccessfulLogin(OffsetDateTime now) {
        failedAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public void updatePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.passwordChangedAt = OffsetDateTime.now();
        this.failedAttempts = 0;
        this.lockedUntil = null;
    }
}
