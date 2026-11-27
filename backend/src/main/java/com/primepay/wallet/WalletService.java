package com.primepay.wallet;

import com.primepay.common.error.BusinessException;
import com.primepay.common.error.ErrorCode;
import com.primepay.user.AccountRole;
import com.primepay.user.AgentAccount;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Wallet lookup, creation and locking. Balances themselves change only in LedgerService. */
@Service
public class WalletService {
    private final WalletRepository wallets;
    private final EntityManager entityManager;

    public WalletService(WalletRepository wallets, EntityManager entityManager) {
        this.wallets = wallets;
        this.entityManager = entityManager;
    }

    /** Every business account (not Platform Admin) has exactly one wallet, created on first use. */
    @Transactional
    public Wallet getOrCreate(AgentAccount account) {
        if (account.getRole() == AccountRole.ADMIN) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE, "Platform Admin accounts don't have a wallet.");
        }
        return wallets.findByAccountId(account.getId())
                .orElseGet(() -> wallets.saveAndFlush(new Wallet("WLT-" + account.getAccountCode(), account.getId())));
    }

    /**
     * Locks the given wallets with SELECT ... FOR UPDATE, always in ascending ID order so two transfers
     * between the same wallets in opposite directions can't deadlock. Returns them keyed by wallet ID.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<Long, Wallet> lockAll(Collection<Wallet> toLock) {
        Map<Long, Wallet> locked = new LinkedHashMap<>();
        for (Long id : new TreeSet<>(toLock.stream().map(Wallet::getId).toList())) {
            // refresh + PESSIMISTIC_WRITE re-reads the row under SELECT ... FOR UPDATE, so balances are never stale
            Wallet wallet = wallets.findById(id).orElseThrow();
            entityManager.refresh(wallet, LockModeType.PESSIMISTIC_WRITE);
            if (!wallet.isActive()) {
                throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE, "Wallet " + wallet.getWalletCode() + " is not active.");
            }
            locked.put(id, wallet);
        }
        return locked;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Wallet lockOne(Wallet wallet) {
        return lockAll(List.of(wallet)).get(wallet.getId());
    }

    @Transactional(readOnly = true)
    public Map<Long, java.math.BigDecimal> balancesByAccount(Collection<Long> accountIds) {
        Map<Long, java.math.BigDecimal> result = new LinkedHashMap<>();
        if (accountIds.isEmpty()) {
            return result;
        }
        for (List<Long> chunk : chunks(new ArrayList<>(accountIds))) {
            wallets.findByAccountIdIn(chunk).forEach(w -> result.put(w.getAccountId(), w.getAvailableBalance()));
        }
        return result;
    }

    private static List<List<Long>> chunks(List<Long> ids) {
        List<List<Long>> out = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += 900) {
            out.add(ids.subList(i, Math.min(ids.size(), i + 900)));
        }
        return out;
    }
}
