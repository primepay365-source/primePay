package com.primepay.api.account;

import java.time.OffsetDateTime;

public record AccountResponse(
        Long id,
        String accountCode,
        String displayName,
        AccountRole role,
        Long parentId,
        boolean active,
        OffsetDateTime createdAt) {

    public static AccountResponse from(AgentAccount account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountCode(),
                account.getDisplayName(),
                account.getRole(),
                account.getParent() == null ? null : account.getParent().getId(),
                account.isActive(),
                account.getCreatedAt());
    }
}
