package com.primepay.user;

/** Registration and account lifecycle states. Only ACTIVE accounts can sign in or move money. */
public enum AccountStatus {
    DRAFT,
    PENDING_VERIFICATION,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    SUSPENDED,
    ACTIVE,
    INACTIVE
}
