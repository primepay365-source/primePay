package com.primepay.transaction;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Transaction states and the only transitions allowed between them. */
public enum TxnStatus {
    INITIATED, PENDING, SUCCESS, FAILED, REVERSED, REFUNDED;

    private static final Map<TxnStatus, Set<TxnStatus>> ALLOWED = Map.of(
            INITIATED, EnumSet.of(PENDING, SUCCESS, FAILED),
            PENDING, EnumSet.of(SUCCESS, FAILED),
            SUCCESS, EnumSet.of(REVERSED, REFUNDED),
            FAILED, EnumSet.noneOf(TxnStatus.class),
            REVERSED, EnumSet.noneOf(TxnStatus.class),
            REFUNDED, EnumSet.noneOf(TxnStatus.class));

    public boolean canMoveTo(TxnStatus next) {
        return ALLOWED.get(this).contains(next);
    }
}
