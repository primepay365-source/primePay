package com.primepay.api.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "account_user")
public class AccountUser {
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

    protected AccountUser() {
    }

    public AccountUser(String username, String passwordHash, AgentAccount account) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.account = account;
        this.active = true;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public AgentAccount getAccount() { return account; }
    public boolean isActive() { return active; }

    public void updatePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
