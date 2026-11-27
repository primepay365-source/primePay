package com.primepay.transaction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Long>,
        JpaSpecificationExecutor<FinancialTransaction> {

    Optional<FinancialTransaction> findByInitiatorAccountIdAndIdempotencyKey(Long initiatorAccountId, String idempotencyKey);

    List<FinancialTransaction> findByOriginalTxnId(Long originalTxnId);

    List<FinancialTransaction> findByStatus(TxnStatus status);

    /** Usage counted towards limits: everything not FAILED, so pending items also count. */
    @Query("""
            select coalesce(sum(t.amount), 0) from FinancialTransaction t
            where t.initiatorAccountId = :accountId and t.serviceCode = :serviceCode
              and t.status <> com.primepay.transaction.TxnStatus.FAILED
              and t.createdAt >= :from and t.createdAt < :to""")
    BigDecimal sumUsage(Long accountId, String serviceCode, OffsetDateTime from, OffsetDateTime to);

    @Query("""
            select count(t) from FinancialTransaction t
            where t.initiatorAccountId = :accountId and t.serviceCode = :serviceCode
              and t.status <> com.primepay.transaction.TxnStatus.FAILED
              and t.createdAt >= :from and t.createdAt < :to""")
    long countUsage(Long accountId, String serviceCode, OffsetDateTime from, OffsetDateTime to);

    @Query("""
            select t from FinancialTransaction t
            where (t.initiatorAccountId in :ids or t.sourceAccountId in :ids or t.targetAccountId in :ids)
              and t.createdAt >= :from and t.createdAt < :to""")
    List<FinancialTransaction> findInScopeBetween(Collection<Long> ids, OffsetDateTime from, OffsetDateTime to);
}
