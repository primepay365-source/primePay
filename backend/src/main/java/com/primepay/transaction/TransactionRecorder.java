package com.primepay.transaction;

import com.primepay.common.error.BusinessException;
import com.primepay.common.error.ErrorCode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Creates transactions with references, enforces idempotency, and records every state change. */
@Component
public class TransactionRecorder {
    private final FinancialTransactionRepository transactions;
    private final TransactionStatusHistoryRepository history;
    private final ReferenceGenerator references;

    public TransactionRecorder(FinancialTransactionRepository transactions, TransactionStatusHistoryRepository history,
                               ReferenceGenerator references) {
        this.transactions = transactions;
        this.history = history;
        this.references = references;
    }

    /**
     * If this initiator already used the key: same request returns the original transaction, a different
     * request is refused. Call it while holding the initiator's wallet lock so concurrent retries serialise.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<FinancialTransaction> findReplay(Long initiatorId, String idempotencyKey, String requestHash) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 80) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Idempotency-Key must be 1 to 80 characters.");
        }
        Optional<FinancialTransaction> existing = transactions.findByInitiatorAccountIdAndIdempotencyKey(initiatorId, idempotencyKey);
        if (existing.isPresent() && !requestHash.equals(existing.get().getRequestHash())) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        return existing;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public FinancialTransaction create(TxnType type, String serviceCode, Long initiatorId, BigDecimal amount, BigDecimal fee,
                                       String idempotencyKey, String requestHash, String note) {
        // System-created rows (commission, reversal, adjustment) get their own key: Oracle treats (account, NULL)
        // pairs in a composite unique index as duplicates, so a NULL key would block the second such row.
        String key = idempotencyKey != null ? idempotencyKey : "SYS-" + java.util.UUID.randomUUID();
        FinancialTransaction txn = transactions.save(new FinancialTransaction(references.next(type.prefix()), type, serviceCode,
                initiatorId, amount, fee, key, requestHash));
        history.save(new TransactionStatusHistory(txn.getId(), null, TxnStatus.INITIATED, note, initiatorId));
        return txn;
    }

    @Transactional(readOnly = true)
    public String referenceOf(Long transactionId) {
        return transactions.findById(transactionId).map(FinancialTransaction::getReferenceNo).orElse(null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void move(FinancialTransaction txn, TxnStatus next, String note, Long actorId) {
        TxnStatus previous = txn.moveTo(next);
        history.save(new TransactionStatusHistory(txn.getId(), previous, next, note, actorId));
    }

    public static String hash(Object... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder canonical = new StringBuilder();
            for (Object part : parts) {
                canonical.append(part instanceof BigDecimal d ? d.stripTrailingZeros().toPlainString() : String.valueOf(part)).append('|');
            }
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
