package com.primepay.ledger;

import com.primepay.transaction.FinancialTransaction;
import com.primepay.wallet.Wallet;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only code allowed to change a wallet balance. Every change writes an immutable ledger line in the
 * caller's database transaction, so a balance and its ledger can never disagree.
 * Callers must hold the wallet row lock (WalletRepository.lockById) first.
 */
@Service
public class LedgerService {
    public static final String DR = "DR";
    public static final String CR = "CR";

    private final LedgerEntryRepository entries;

    public LedgerService(LedgerEntryRepository entries) {
        this.entries = entries;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public LedgerEntry debit(Wallet wallet, FinancialTransaction txn, BigDecimal amount, String entryType, String description) {
        BigDecimal before = wallet.getAvailableBalance();
        wallet.applyDebit(amount);
        return entries.save(new LedgerEntry(newRef(), wallet.getId(), txn.getId(), DR, entryType, amount, before,
                wallet.getAvailableBalance(), description));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public LedgerEntry credit(Wallet wallet, FinancialTransaction txn, BigDecimal amount, String entryType, String description) {
        BigDecimal before = wallet.getAvailableBalance();
        wallet.applyCredit(amount);
        return entries.save(new LedgerEntry(newRef(), wallet.getId(), txn.getId(), CR, entryType, amount, before,
                wallet.getAvailableBalance(), description));
    }

    private static String newRef() {
        return "LE" + UUID.randomUUID().toString().replace("-", "").substring(0, 26).toUpperCase();
    }
}
