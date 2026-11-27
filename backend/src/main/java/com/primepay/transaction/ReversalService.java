package com.primepay.transaction;

import com.primepay.audit.AuditService;
import com.primepay.commission.CommissionEngine;
import com.primepay.common.Money;
import com.primepay.common.error.BusinessException;
import com.primepay.common.error.ErrorCode;
import com.primepay.ledger.LedgerService;
import com.primepay.notification.NotificationService;
import com.primepay.user.AccountRole;
import com.primepay.user.AgentAccount;
import com.primepay.user.AgentAccountRepository;
import com.primepay.wallet.Wallet;
import com.primepay.wallet.WalletService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin reversal: a new REVERSAL transaction whose ledger lines mirror the original's, plus reversal of any
 * commission paid. The original rows are never edited apart from its status moving to REVERSED.
 * If a wallet that received money no longer has it, the whole reversal is refused and nothing changes.
 */
@Service
public class ReversalService {
    static final Duration WINDOW = Duration.ofDays(7);

    private final FinancialTransactionRepository transactions;
    private final AgentAccountRepository accounts;
    private final WalletService walletService;
    private final LedgerService ledger;
    private final TransactionRecorder recorder;
    private final CommissionEngine commission;
    private final NotificationService notifications;
    private final AuditService audit;

    public ReversalService(FinancialTransactionRepository transactions, AgentAccountRepository accounts, WalletService walletService,
                           LedgerService ledger, TransactionRecorder recorder, CommissionEngine commission,
                           NotificationService notifications, AuditService audit) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.walletService = walletService;
        this.ledger = ledger;
        this.recorder = recorder;
        this.commission = commission;
        this.notifications = notifications;
        this.audit = audit;
    }

    @Transactional
    public FinancialTransaction reverse(AgentAccount admin, Long txnId, String reason) {
        if (admin.getRole() != AccountRole.ADMIN) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Only a Platform Admin can reverse transactions.");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Give a reason for the reversal.");
        }
        FinancialTransaction original = transactions.findById(txnId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Transaction not found."));
        if (original.getStatus() != TxnStatus.SUCCESS) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Only successful transactions can be reversed.");
        }
        if (original.getType() != TxnType.WALLET_TRANSFER && original.getType() != TxnType.PAYOUT && original.getType() != TxnType.WALLET_LOAD) {
            throw new BusinessException(ErrorCode.INVALID_STATE, original.getType().label() + " transactions can't be reversed here.");
        }
        if (original.getCreatedAt().isBefore(OffsetDateTime.now().minus(WINDOW))) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "The 7-day reversal window has passed. Use a wallet adjustment instead.");
        }

        // Every account whose wallet changes: the parties plus anyone who earned commission.
        Set<Long> accountIds = new LinkedHashSet<>();
        if (original.getSourceAccountId() != null) accountIds.add(original.getSourceAccountId());
        if (original.getTargetAccountId() != null) accountIds.add(original.getTargetAccountId());
        accountIds.addAll(commission.creditedEarners(original.getId()));
        Map<Long, Wallet> byAccount = new HashMap<>();
        List<Wallet> toLock = new ArrayList<>();
        for (Long id : accountIds) {
            Wallet w = walletService.getOrCreate(accounts.findById(id).orElseThrow());
            byAccount.put(id, w);
            toLock.add(w);
        }
        Map<Long, Wallet> locked = walletService.lockAll(toLock);
        Map<Long, Wallet> lockedByAccount = new HashMap<>();
        byAccount.forEach((accountId, w) -> lockedByAccount.put(accountId, locked.get(w.getId())));

        FinancialTransaction reversal = recorder.create(TxnType.REVERSAL, original.getServiceCode(), admin.getId(),
                original.getAmount(), BigDecimal.ZERO, null, null, "Reversal of " + original.getReferenceNo());
        reversal.setOriginalTxnId(original.getId());
        reversal.setSourceAccountId(original.getTargetAccountId());
        reversal.setTargetAccountId(original.getSourceAccountId());
        reversal.setRemarks("Reversal of " + original.getReferenceNo() + ": " + reason);

        switch (original.getType()) {
            case WALLET_TRANSFER -> {
                ledger.debit(lockedByAccount.get(original.getTargetAccountId()), reversal, original.getAmount(), "REVERSAL",
                        "Reversal of " + original.getReferenceNo());
                // Mirror of the original transfer lines: debit receiver, credit sender, same amount (any fee is not refunded).
                ledger.credit(lockedByAccount.get(original.getSourceAccountId()), reversal, original.getAmount(), "REVERSAL",
                        "Reversal of " + original.getReferenceNo());
            }
            case WALLET_LOAD -> ledger.debit(lockedByAccount.get(original.getTargetAccountId()), reversal, original.getAmount(), "REVERSAL",
                    "Reversal of load " + original.getReferenceNo());
            case PAYOUT -> {
                commission.reverse(original, reversal, lockedByAccount);
                ledger.credit(lockedByAccount.get(original.getSourceAccountId()), reversal, original.totalDebit(), "REVERSAL",
                        "Payout reversed: " + original.getReferenceNo());
            }
            default -> throw new IllegalStateException();
        }
        recorder.move(reversal, TxnStatus.SUCCESS, "Compensating entries posted", admin.getId());
        recorder.move(original, TxnStatus.REVERSED, "Reversed by " + admin.getAccountCode() + ": " + reason + " (" + reversal.getReferenceNo() + ")", admin.getId());
        original.setDecidedByAccountId(admin.getId());

        audit.record(admin, "TRANSACTION_REVERSE", "TRANSACTION", original.getReferenceNo(),
                "reversal=" + reversal.getReferenceNo() + " reason=" + reason);
        Set<Long> parties = new LinkedHashSet<>();
        if (original.getSourceAccountId() != null) parties.add(original.getSourceAccountId());
        if (original.getTargetAccountId() != null) parties.add(original.getTargetAccountId());
        for (Long accountId : parties) {
            {
                notifications.notify(accountId, NotificationService.MONEY, "Transaction reversed",
                        original.getReferenceNo() + " (" + Money.format(original.getAmount()) + ") was reversed by the platform: " + reason + ".",
                        "/transactions/" + original.getId());
            }
        }
        return reversal;
    }
}
