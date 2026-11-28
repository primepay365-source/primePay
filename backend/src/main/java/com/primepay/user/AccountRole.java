package com.primepay.user;

import java.util.Set;

public enum AccountRole {
    ADMIN,
    SUPER_DISTRIBUTOR,
    MASTER_DISTRIBUTOR,
    DISTRIBUTOR,
    RETAILER;

    public boolean canTransact() {
        return this != ADMIN;
    }

    /**
     * Levels an account of this role may sit under. Network levels keep the strict chain; a Retailer may be
     * created directly under any level above it, and its commission chain then has only those levels.
     */
    public Set<AccountRole> allowedParentRoles() {
        return switch (this) {
            case ADMIN -> Set.of();
            case SUPER_DISTRIBUTOR -> Set.of(ADMIN);
            case MASTER_DISTRIBUTOR -> Set.of(SUPER_DISTRIBUTOR);
            case DISTRIBUTOR -> Set.of(MASTER_DISTRIBUTOR);
            case RETAILER -> Set.of(DISTRIBUTOR, MASTER_DISTRIBUTOR, SUPER_DISTRIBUTOR, ADMIN);
        };
    }
}
