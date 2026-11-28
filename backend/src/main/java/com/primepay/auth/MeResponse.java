package com.primepay.auth;

import com.primepay.user.AccountRole;
import com.primepay.user.AccountUser;
import com.primepay.user.AgentAccount;

/** The signed-in user, as returned by {@code GET /api/v1/auth/me}. */
public record MeResponse(
        Long accountId,
        String accountCode,
        String username,
        String displayName,
        AccountRole role,
        Long parentId,
        boolean active,
        boolean canTransact) {

    static MeResponse from(AccountUser user) {
        AgentAccount account = user.getAccount();
        return new MeResponse(
                account.getId(),
                account.getAccountCode(),
                user.getUsername(),
                account.getDisplayName(),
                account.getRole(),
                account.getParent() == null ? null : account.getParent().getId(),
                account.isActive() && user.isActive(),
                account.getRole().canTransact());
    }
}
