package com.primepay.commission;

import com.primepay.common.Money;
import com.primepay.ledger.LedgerService;
import com.primepay.notification.NotificationService;
import com.primepay.transaction.FinancialTransaction;
import com.primepay.transaction.TransactionRecorder;
import com.primepay.transaction.TxnStatus;
import com.primepay.transaction.TxnType;
import com.primepay.user.AgentAccount;
import com.primepay.wallet.Wallet;
import com.primepay.wallet.WalletService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Splits commission for a successful transaction across the hierarchy, using the configured rule for the
 * service, amount slab and business date. Each level's share is stored as its own row. Earner wallets are
 * credited through one COMMISSION transaction; the platform's share is recorded without a wallet.
 */
@Service
public class CommissionEngine {
    private final CommissionRuleRepository rules;
    private final CommissionEntryRepository entries;
    private final TransactionRecorder recorder;
    private final WalletService walletService;
    private final LedgerService ledger;
    private final NotificationService notifications;
    private final ZoneId zone;

    public CommissionEngine(CommissionRuleRepository rules, CommissionEntryRepository entries, TransactionRecorder recorder,
                            WalletService walletService, LedgerService ledger, NotificationService notifications,
                            @Value("${app.business-timezone:Asia/Kolkata}") String zone) {
        this.rules = rules;
        this.entries = entries;
        this.recorder = recorder;
        this.walletService = walletService;
        this.ledger = ledger;
        this.notifications = notifications;
        this.zone = ZoneId.of(zone);
    }

    @Transactional(readOnly = true)
    public Optional<CommissionRule> ruleFor(String serviceCode, BigDecimal amount) {
        LocalDate today = LocalDate.now(zone);
        return rules.findByServiceCodeOrderByEffectiveFromDesc(serviceCode).stream()
                .filter(rule -> rule.appliesTo(amount, today))
                .findFirst();
    }

    /** All shares (network and platform) a pending transaction would pay if it succeeds. */
    @Transactional(readOnly = true)
    public BigDecimal expectedTotal(FinancialTransaction txn) {
        return ruleFor(txn.getServiceCode(), txn.getAmount()).map(rule -> rule.getShares().stream()
                        .map(s -> s.calculate(rule.getCalcType(), txn.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add))
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Commission {@code earner} would receive if {@code txn} (made by {@code transactingUser}) succeeds,
     * using the same rule and share as {@link #distribute}. Zero when the earner is not in the chain.
     */
    @Transactional(readOnly = true)
    public BigDecimal expectedShare(FinancialTransaction txn, AgentAccount transactingUser, AgentAccount earner) {
        boolean inChain = false;
        for (AgentAccount a = transactingUser; a != null; a = a.getParent()) {
            if (a.getId().equals(earner.getId())) {
                inChain = true;
                break;
            }
            if (a.getRole() == earner.getRole()) {
                return BigDecimal.ZERO; // a nearer account of the same level takes this share
            }
        }
        if (!inChain) {
            return BigDecimal.ZERO;
        }
        return ruleFor(txn.getServiceCode(), txn.getAmount()).map(rule -> rule.getShares().stream()
                        .filter(s -> s.getLevelRole().equals(earner.getRole().name()))
                        .map(s -> s.calculate(rule.getCalcType(), txn.getAmount()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .orElse(BigDecimal.ZERO);
    }

    /** Called inside the payout's database transaction after it reaches SUCCESS. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<CommissionEntry> distribute(FinancialTransaction source, AgentAccount transactingUser) {
        return distribute(source, transactingUser, false);
    }

    /**
     * One share of a rule for one transaction: the earning account (null for the platform share) and the amount.
     */
    public record Split(String levelRole, AgentAccount earner, BigDecimal amount) {
    }

    /**
     * Shares paid to the levels ABOVE the transacting account (the account itself earns nothing), with the
     * Platform Admin's share as PLATFORM. Levels missing from the chain, or inactive, are skipped and not charged.
     * Used for add funds: the loading account is credited the amount minus the gateway charge and these shares.
     */
    @Transactional(readOnly = true)
    public List<Split> uplineSplit(String serviceCode, BigDecimal amount, AgentAccount transactingUser) {
        Optional<CommissionRule> found = ruleFor(serviceCode, amount);
        if (found.isEmpty()) {
            return List.of();
        }
        CommissionRule rule = found.get();
        Map<String, AgentAccount> chain = new HashMap<>();
        for (AgentAccount a = transactingUser.getParent(); a != null; a = a.getParent()) {
            chain.putIfAbsent(a.getRole().name(), a);
        }
        List<Split> out = new ArrayList<>();
        for (CommissionShare share : rule.getShares()) {
            BigDecimal value = share.calculate(rule.getCalcType(), amount);
            if (value.signum() <= 0) continue;
            if ("PLATFORM".equals(share.getLevelRole())) {
                out.add(new Split("PLATFORM", null, value));
                continue;
            }
            AgentAccount earner = chain.get(share.getLevelRole());
            if (earner != null && earner.isActive()) {
                out.add(new Split(share.getLevelRole(), earner, value));
            }
        }
        return out;
    }

    /** As {@link #distribute(FinancialTransaction, AgentAccount)}; with {@code uplinesOnly} the transacting account earns no share. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<CommissionEntry> distribute(FinancialTransaction source, AgentAccount transactingUser, boolean uplinesOnly) {
        // Never credit commission twice for the same transaction (for example a retried status recheck).
        if (!entries.findBySourceTxnIdOrderByIdAsc(source.getId()).isEmpty()) {
            return List.of();
        }
        Optional<CommissionRule> found = ruleFor(source.getServiceCode(), source.getAmount());
        if (found.isEmpty()) {
            return List.of();
        }
        CommissionRule rule = found.get();

        Map<String, AgentAccount> chain = new HashMap<>();
        for (AgentAccount a = uplinesOnly ? transactingUser.getParent() : transactingUser; a != null; a = a.getParent()) {
            chain.putIfAbsent(a.getRole().name(), a);
        }

        List<CommissionEntry> created = new ArrayList<>();
        Map<AgentAccount, BigDecimal> credits = new HashMap<>();
        for (CommissionShare share : rule.getShares()) {
            BigDecimal value = share.calculate(rule.getCalcType(), source.getAmount());
            if (value.signum() <= 0) {
                continue;
            }
            if ("PLATFORM".equals(share.getLevelRole())) {
                created.add(new CommissionEntry(source.getId(), null, rule.getId(), null, "PLATFORM", source.getAmount(), value, "PLATFORM"));
                continue;
            }
            AgentAccount earner = chain.get(share.getLevelRole());
            if (earner == null || !earner.isActive()) {
                continue;
            }
            credits.merge(earner, value, BigDecimal::add);
            created.add(new CommissionEntry(source.getId(), null, rule.getId(), earner.getId(), share.getLevelRole(),
                    source.getAmount(), value, "CREDITED"));
        }

        BigDecimal total = credits.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        Long creditTxnId = null;
        if (total.signum() > 0) {
            FinancialTransaction commissionTxn = recorder.create(TxnType.COMMISSION, "COMMISSION", transactingUser.getId(), total,
                    BigDecimal.ZERO, null, null, "Commission for " + source.getReferenceNo());
            commissionTxn.setOriginalTxnId(source.getId());
            commissionTxn.setRemarks("Commission for " + source.getReferenceNo() + " (" + rule.getRuleCode() + ")");
            Map<Long, Wallet> wallets = new HashMap<>();
            List<Wallet> toLock = new ArrayList<>();
            for (AgentAccount earner : credits.keySet()) {
                Wallet w = walletService.getOrCreate(earner);
                wallets.put(earner.getId(), w);
                toLock.add(w);
            }
            Map<Long, Wallet> locked = walletService.lockAll(toLock);
            for (Map.Entry<AgentAccount, BigDecimal> credit : credits.entrySet()) {
                Wallet wallet = locked.get(wallets.get(credit.getKey().getId()).getId());
                ledger.credit(wallet, commissionTxn, credit.getValue(), "COMMISSION", "Commission on " + source.getReferenceNo());
                notifications.notify(credit.getKey().getId(), NotificationService.MONEY, "Commission credited",
                        Money.format(credit.getValue()) + " commission on " + source.getReferenceNo() + " was added to your wallet.",
                        "/commissions/statement");
            }
            recorder.move(commissionTxn, TxnStatus.SUCCESS, "Commission credited", null);
            creditTxnId = commissionTxn.getId();
        }
        List<CommissionEntry> saved = new ArrayList<>();
        for (CommissionEntry entry : created) {
            CommissionEntry withTxn = new CommissionEntry(entry.getSourceTxnId(), entry.getEarnerAccountId() == null ? null : creditTxnId,
                    entry.getRuleId(), entry.getEarnerAccountId(), entry.getLevelRole(), entry.getBaseAmount(), entry.getCommissionAmount(), entry.getStatus());
            saved.add(entries.save(withTxn));
        }
        return saved;
    }

    /** Reverses every commission paid on a transaction, debiting each earner. Fails if an earner can't cover it. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reverse(FinancialTransaction source, FinancialTransaction reversalTxn, Map<Long, Wallet> lockedByAccount) {
        for (CommissionEntry entry : entries.findBySourceTxnIdOrderByIdAsc(source.getId())) {
            if ("CREDITED".equals(entry.getStatus())) {
                Wallet wallet = lockedByAccount.get(entry.getEarnerAccountId());
                ledger.debit(wallet, reversalTxn, entry.getCommissionAmount(), "COMMISSION_REVERSAL",
                        "Commission reversed for " + source.getReferenceNo());
                notifications.notify(entry.getEarnerAccountId(), NotificationService.MONEY, "Commission reversed",
                        Money.format(entry.getCommissionAmount()) + " commission on " + source.getReferenceNo() + " was reversed.",
                        "/commissions/statement");
            }
            if (!"REVERSED".equals(entry.getStatus())) {
                entry.markReversed();
            }
        }
    }

    @Transactional(readOnly = true)
    public List<CommissionEntry> entriesFor(Long sourceTxnId) {
        return entries.findBySourceTxnIdOrderByIdAsc(sourceTxnId);
    }

    @Transactional(readOnly = true)
    public List<Long> creditedEarners(Long sourceTxnId) {
        return entries.findBySourceTxnIdOrderByIdAsc(sourceTxnId).stream()
                .filter(e -> "CREDITED".equals(e.getStatus()) && e.getEarnerAccountId() != null)
                .map(CommissionEntry::getEarnerAccountId).toList();
    }
}
