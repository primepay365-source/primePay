package com.primepay.api.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "agent_account")
public class AgentAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_code", nullable = false, unique = true, length = 40)
    private String accountCode;

    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AccountRole role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private AgentAccount parent;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected AgentAccount() {
    }

    public AgentAccount(String accountCode, String displayName, AccountRole role, AgentAccount parent) {
        this.accountCode = accountCode;
        this.displayName = displayName;
        this.role = role;
        this.parent = parent;
        this.active = true;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getAccountCode() { return accountCode; }
    public String getDisplayName() { return displayName; }
    public AccountRole getRole() { return role; }
    public AgentAccount getParent() { return parent; }
    public boolean isActive() { return active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void update(String displayName, boolean active) {
        this.displayName = displayName;
        this.active = active;
    }
}
