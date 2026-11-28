package com.primepay.user;

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
import lombok.Getter;
import lombok.Setter;

@Getter
@Entity
@Table(name = "agent_account")
public class AgentAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_code", nullable = false, unique = true, length = 40)
    private String accountCode;

    @Setter
    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AccountRole role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private AgentAccount parent;

    /** Kept in sync with {@link #status}: true only when status is ACTIVE. */
    @Column(nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Setter
    @Column(length = 160)
    private String email;

    @Setter
    @Column(length = 20)
    private String mobile;

    @Setter
    @Column(name = "business_name", length = 160)
    private String businessName;

    @Setter
    @Column(length = 500)
    private String address;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected AgentAccount() {
    }

    public AgentAccount(String accountCode, String displayName, AccountRole role, AgentAccount parent) {
        this.accountCode = accountCode;
        this.displayName = displayName;
        this.role = role;
        this.parent = parent;
        this.createdAt = OffsetDateTime.now();
        changeStatus(AccountStatus.ACTIVE);
    }

    public void changeStatus(AccountStatus newStatus) {
        this.status = newStatus;
        this.active = newStatus == AccountStatus.ACTIVE;
    }

    /** Legacy update used by PUT /accounts/{id}. */
    public void update(String displayName, boolean active) {
        this.displayName = displayName;
        if (active != this.active) {
            changeStatus(active ? AccountStatus.ACTIVE : AccountStatus.INACTIVE);
        }
    }
}
