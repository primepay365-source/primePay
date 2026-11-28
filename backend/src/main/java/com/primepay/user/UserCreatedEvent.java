package com.primepay.user;

/**
 * Published when a user is created. Carries the password the creator typed so the welcome email can include it;
 * it lives only in memory for the email and is never logged or stored (the database keeps a BCrypt hash).
 */
public record UserCreatedEvent(Long accountId, String accountCode, String displayName, AccountRole role,
                               String email, String mobile, String username, String password) {

    @Override
    public String toString() {
        return "UserCreatedEvent[accountCode=" + accountCode + ", role=" + role + "]"; // never print the password
    }
}
