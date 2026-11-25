package com.primepay.api.account;

public enum AccountRole {
    ADMIN,
    SUPER_DISTRIBUTOR,
    MASTER_DISTRIBUTOR,
    DISTRIBUTOR,
    RETAILER;

    public boolean canTransact() {
        return this != ADMIN;
    }

    public AccountRole requiredParentRole() {
        return switch (this) {
            case ADMIN -> null;
            case SUPER_DISTRIBUTOR -> ADMIN;
            case MASTER_DISTRIBUTOR -> SUPER_DISTRIBUTOR;
            case DISTRIBUTOR -> MASTER_DISTRIBUTOR;
            case RETAILER -> DISTRIBUTOR;
        };
    }
}
