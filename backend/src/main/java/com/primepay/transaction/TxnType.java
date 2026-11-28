package com.primepay.transaction;

public enum TxnType {
    WALLET_LOAD("PPL", "Wallet load"),
    WALLET_TRANSFER("PPT", "Wallet transfer"),
    WALLET_CREDIT("PPT", "Wallet credit"),
    WALLET_DEBIT("PPT", "Wallet debit"),
    PAYOUT("PPP", "Withdraw (payout)"),
    BILL_PAYMENT("PPB", "Bill payment"),
    ADD_FUNDS("PPI", "Add funds"),
    REFUND("PPF", "Refund"),
    REVERSAL("PPR", "Reversal"),
    ADJUSTMENT("PPA", "Adjustment"),
    COMMISSION("PPC", "Commission");

    private final String prefix;
    private final String label;

    TxnType(String prefix, String label) {
        this.prefix = prefix;
        this.label = label;
    }

    public String prefix() {
        return prefix;
    }

    public String label() {
        return label;
    }
}
