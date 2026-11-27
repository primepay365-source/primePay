package com.primepay.commission;

import com.primepay.transaction.FinancialTransaction;
import com.primepay.transaction.FinancialTransactionRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only commission figures for dashboards, reports and reconciliation. */
@Service
public class CommissionStats {
    private final CommissionEntryRepository entries;
    private final FinancialTransactionRepository transactions;

    public CommissionStats(CommissionEntryRepository entries, FinancialTransactionRepository transactions) {
        this.entries = entries;
        this.transactions = transactions;
    }

    public record Totals(BigDecimal paid, BigDecimal platform, BigDecimal reversed, long count) {
    }

    public record Row(Long transactionId, String transactionRef, OffsetDateTime createdAt, Long earnerAccountId, String levelRole,
                      BigDecimal baseAmount, BigDecimal amount, String status) {
    }

    @Transactional(readOnly = true)
    public Totals platformTotals(OffsetDateTime from) {
        return new Totals(entries.sumAll("CREDITED", from), entries.sumAll("PLATFORM", from), entries.sumAll("REVERSED", from), 0);
    }

    @Transactional(readOnly = true)
    public Totals earnerTotals(Long accountId, OffsetDateTime from) {
        long count = entries.findAll().stream()
                .filter(e -> accountId.equals(e.getEarnerAccountId()) && !e.getCreatedAt().isBefore(from)).count();
        return new Totals(entries.sumForEarner(accountId, "CREDITED", from), BigDecimal.ZERO,
                entries.sumForEarner(accountId, "REVERSED", from).negate(), count);
    }

    /** Commission rows in a period; {@code earners} null means everyone (Platform Admin). */
    @Transactional(readOnly = true)
    public List<Row> rows(Collection<Long> earners, OffsetDateTime from, OffsetDateTime to) {
        List<CommissionEntry> list = entries.findAll().stream()
                .filter(e -> !e.getCreatedAt().isBefore(from) && e.getCreatedAt().isBefore(to))
                .filter(e -> earners == null || (e.getEarnerAccountId() != null && earners.contains(e.getEarnerAccountId())))
                .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                .toList();
        Map<Long, FinancialTransaction> txns = transactions.findAllById(list.stream().map(CommissionEntry::getSourceTxnId).distinct().toList())
                .stream().collect(Collectors.toMap(FinancialTransaction::getId, Function.identity()));
        return list.stream().map(e -> new Row(e.getSourceTxnId(), txns.get(e.getSourceTxnId()).getReferenceNo(), e.getCreatedAt(),
                e.getEarnerAccountId(), e.getLevelRole(), e.getBaseAmount(), e.getCommissionAmount(), e.getStatus())).toList();
    }

    /** Sum of commission currently credited to wallets (not reversed, not platform). */
    @Transactional(readOnly = true)
    public BigDecimal creditedNet() {
        return entries.totalCredited();
    }
}
