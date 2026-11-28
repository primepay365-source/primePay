package com.primepay.wallet;

import com.primepay.audit.AuditService;
import com.primepay.common.Money;
import com.primepay.common.error.BusinessException;
import com.primepay.common.error.ErrorCode;
import com.primepay.ledger.LedgerService;
import com.primepay.notification.NotificationService;
import com.primepay.transaction.FinancialTransaction;
import com.primepay.transaction.ReferenceGenerator;
import com.primepay.transaction.TransactionRecorder;
import com.primepay.transaction.TxnStatus;
import com.primepay.transaction.TxnType;
import com.primepay.user.AccountRole;
import com.primepay.user.AgentAccount;
import com.primepay.user.AgentAccountRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdjustmentService {
    private final AdjustmentRepository adjustments;
    private final AgentAccountRepository accounts;
    private final WalletService walletService;
    private final LedgerService ledger;
    private final TransactionRecorder recorder;
    private final ReferenceGenerator references;
    private final NotificationService notifications;
    private final AuditService audit;

    public AdjustmentService(AdjustmentRepository adjustments, AgentAccountRepository accounts, WalletService walletService,
                             LedgerService ledger, TransactionRecorder recorder, ReferenceGenerator references,
                             NotificationService notifications, AuditService audit) {
        this.adjustments = adjustments;
        this.accounts = accounts;
        this.walletService = walletService;
        this.ledger = ledger;
        this.recorder = recorder;
        this.references = references;
        this.notifications = notifications;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<WalletAdjustment> list() {
        return adjustments.findAllByOrderByIdDesc();
    }

    @Transactional
    public WalletAdjustment request(AgentAccount maker, Long accountId, String direction, BigDecimal amount, String reason, String details) {
        requireAdmin(maker);
        if (!"CR".equals(direction) && !"DR".equals(direction)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Direction must be CR (credit) or DR (debit).");
        }
        AgentAccount target = accounts.findById(accountId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Account not found."));
        walletService.getOrCreate(target);
        WalletAdjustment adjustment = adjustments.save(new WalletAdjustment(references.next("ADJ"), accountId, direction,
                Money.of(amount), reason, details, maker.getId()));
        audit.record(maker, "ADJUSTMENT_REQUEST", "ADJUSTMENT", adjustment.getRequestRef(),
                direction + " " + amount.toPlainString() + " account=" + target.getAccountCode() + " reason=" + reason);
        return adjustment;
    }

    @Transactional
    public WalletAdjustment decide(AgentAccount checker, Long id, boolean approve) {
        requireAdmin(checker);
        WalletAdjustment adjustment = adjustments.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Request not found."));
        if (!"PENDING".equals(adjustment.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This request was already decided.");
        }
        if (adjustment.getRequestedByAccountId().equals(checker.getId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "You raised this request, so another admin must approve it.");
        }
        Long txnId = null;
        if (approve) {
            AgentAccount target = accounts.findById(adjustment.getAccountId()).orElseThrow();
            Wallet wallet = walletService.lockOne(walletService.getOrCreate(target));
            FinancialTransaction txn = recorder.create(TxnType.ADJUSTMENT, "ADJUSTMENT", checker.getId(), adjustment.getAmount(),
                    BigDecimal.ZERO, null, null, "Adjustment " + adjustment.getRequestRef());
            txn.setRemarks(adjustment.getReason() + " · " + adjustment.getRequestRef());
            if ("CR".equals(adjustment.getDirection())) {
                txn.setTargetAccountId(target.getId());
                ledger.credit(wallet, txn, adjustment.getAmount(), "ADJUSTMENT", adjustment.getReason());
            } else {
                txn.setSourceAccountId(target.getId());
                ledger.debit(wallet, txn, adjustment.getAmount(), "ADJUSTMENT", adjustment.getReason());
            }
            txn.setDecidedByAccountId(checker.getId());
            recorder.move(txn, TxnStatus.SUCCESS, "Approved by " + checker.getAccountCode(), checker.getId());
            txnId = txn.getId();
            notifications.notify(target.getId(), NotificationService.MONEY, "Wallet adjusted",
                    ("CR".equals(adjustment.getDirection()) ? "Credit of " : "Debit of ") + Money.format(adjustment.getAmount())
                            + ": " + adjustment.getReason() + ". Ref " + txn.getReferenceNo() + ".", "/wallet");
        }
        adjustment.decide(approve, checker.getId(), txnId);
        audit.record(checker, approve ? "ADJUSTMENT_APPROVE" : "ADJUSTMENT_REJECT", "ADJUSTMENT", adjustment.getRequestRef(), null);
        return adjustment;
    }

    private static void requireAdmin(AgentAccount actor) {
        if (actor.getRole() != AccountRole.ADMIN) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Only Platform Admins handle wallet adjustments.");
        }
    }
}
