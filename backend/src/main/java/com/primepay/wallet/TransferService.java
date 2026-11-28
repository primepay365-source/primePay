package com.primepay.wallet;

import com.primepay.admin.ServiceDefinition;
import com.primepay.admin.ServiceDefinitionRepository;
import com.primepay.audit.AuditService;
import com.primepay.common.Money;
import com.primepay.common.error.BusinessException;
import com.primepay.common.error.ErrorCode;
import com.primepay.ledger.LedgerService;
import com.primepay.notification.NotificationService;
import com.primepay.transaction.FinancialTransaction;
import com.primepay.transaction.FinancialTransactionRepository;
import com.primepay.transaction.TransactionRecorder;
import com.primepay.transaction.TxnStatus;
import com.primepay.transaction.TxnType;
import com.primepay.user.AccountRole;
import com.primepay.user.AgentAccount;
import com.primepay.user.AgentAccountRepository;
import com.primepay.velocity.LimitService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Wallet-to-wallet transfers down the hierarchy, and wallet load requests with admin approval. */
@Service
public class TransferService {
    private final AgentAccountRepository accounts;
    private final WalletService walletService;
    private final LedgerService ledger;
    private final TransactionRecorder recorder;
    private final FinancialTransactionRepository transactions;
    private final LimitService limits;
    private final ServiceDefinitionRepository services;
    private final NotificationService notifications;
    private final AuditService audit;

    public TransferService(AgentAccountRepository accounts, WalletService walletService, LedgerService ledger,
                           TransactionRecorder recorder, FinancialTransactionRepository transactions, LimitService limits,
                           ServiceDefinitionRepository services, NotificationService notifications, AuditService audit) {
        this.accounts = accounts;
        this.walletService = walletService;
        this.ledger = ledger;
        this.recorder = recorder;
        this.transactions = transactions;
        this.limits = limits;
        this.services = services;
        this.notifications = notifications;
        this.audit = audit;
    }
    /** A load request is PENDING until a Platform Admin matches the bank payment and approves it. */
    @Transactional
    public FinancialTransaction requestLoad(AgentAccount requester, BigDecimal rawAmount, String mode, String utr, String idempotencyKey) {
        BigDecimal amount = Money.of(rawAmount);
        if (requester.getRole() == AccountRole.ADMIN || requester.getRole() == AccountRole.RETAILER) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Your role can't request wallet loads. Use Add funds to top up your wallet.");
        }
        services.requireEnabled(ServiceDefinition.WALLET_LOAD);
        Wallet wallet = walletService.lockOne(walletService.getOrCreate(requester));
        String hash = TransactionRecorder.hash("LOAD", amount, mode, utr);
        Optional<FinancialTransaction> replay = recorder.findReplay(requester.getId(), idempotencyKey, hash);
        if (replay.isPresent()) {
            return replay.get();
        }
        limits.check(requester, ServiceDefinition.WALLET_LOAD, amount);
        FinancialTransaction txn = recorder.create(TxnType.WALLET_LOAD, ServiceDefinition.WALLET_LOAD, requester.getId(), amount,
                BigDecimal.ZERO, idempotencyKey, hash, "Load requested");
        txn.setTargetAccountId(requester.getId());
        txn.setPayoutMode(mode);
        txn.setExternalRef(utr);
        txn.setRemarks(mode + " · UTR " + utr);
        recorder.move(txn, TxnStatus.PENDING, "Waiting for admin to match the bank payment", requester.getId());
        audit.record(requester, "WALLET_LOAD_REQUEST", "TRANSACTION", txn.getReferenceNo(), "amount=" + amount.toPlainString() + " wallet=" + wallet.getWalletCode());
        return txn;
    }

    @Transactional
    public FinancialTransaction decideLoad(AgentAccount admin, Long txnId, boolean approve, String note) {
        if (admin.getRole() != AccountRole.ADMIN) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Only a Platform Admin can approve wallet loads.");
        }
        FinancialTransaction txn = transactions.findById(txnId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Transaction not found."));
        if (txn.getType() != TxnType.WALLET_LOAD || txn.getStatus() != TxnStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Only pending wallet loads can be approved or rejected.");
        }
        AgentAccount owner = accounts.findById(txn.getTargetAccountId()).orElseThrow();
        txn.setDecidedByAccountId(admin.getId());
        if (approve) {
            Wallet wallet = walletService.lockOne(walletService.getOrCreate(owner));
            ledger.credit(wallet, txn, txn.getAmount(), "LOAD", "Wallet load " + txn.getRemarks());
            recorder.move(txn, TxnStatus.SUCCESS, note == null || note.isBlank() ? "Bank payment matched" : note, admin.getId());
            notifications.notify(owner.getId(), NotificationService.MONEY, "Wallet credited",
                    Money.format(txn.getAmount()) + " from your load request " + txn.getReferenceNo() + " is now available.", "/wallet");
        } else {
            txn.setFailureReason(note);
            recorder.move(txn, TxnStatus.FAILED, note == null || note.isBlank() ? "Rejected by admin" : note, admin.getId());
            notifications.notify(owner.getId(), NotificationService.MONEY, "Wallet load rejected",
                    "Load request " + txn.getReferenceNo() + " was rejected: " + (note == null ? "no reason given" : note) + ".", "/wallet");
        }
        audit.record(admin, approve ? "WALLET_LOAD_APPROVE" : "WALLET_LOAD_REJECT", "TRANSACTION", txn.getReferenceNo(), note);
        return txn;
    }
}
