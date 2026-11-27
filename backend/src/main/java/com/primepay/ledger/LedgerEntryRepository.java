package com.primepay.ledger;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long>, JpaSpecificationExecutor<LedgerEntry> {
    List<LedgerEntry> findByTransactionIdOrderByIdAsc(Long transactionId);

    List<LedgerEntry> findByWalletIdAndCreatedAtGreaterThanEqualOrderByIdAsc(Long walletId, OffsetDateTime from);

    /** Last entry before a point in time: its balance_after is the opening balance at that moment. */
    LedgerEntry findFirstByWalletIdAndCreatedAtLessThanOrderByIdDesc(Long walletId, OffsetDateTime before);

    @Query("select coalesce(sum(case when e.direction = 'CR' then e.amount else -e.amount end), 0) from LedgerEntry e where e.walletId = :walletId")
    BigDecimal netForWallet(Long walletId);

    @Query("select coalesce(sum(e.amount), 0) from LedgerEntry e where e.direction = :direction and e.walletId = :walletId and e.createdAt >= :from")
    BigDecimal sumSince(Long walletId, String direction, OffsetDateTime from);

    @Query("select coalesce(sum(case when e.direction = 'CR' then e.amount else -e.amount end), 0) from LedgerEntry e where e.entryType = :entryType")
    BigDecimal netOfType(String entryType);
}
