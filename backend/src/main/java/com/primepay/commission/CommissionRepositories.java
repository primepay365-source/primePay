package com.primepay.commission;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface CommissionRuleRepository extends JpaRepository<CommissionRule, Long> {
    List<CommissionRule> findByServiceCodeOrderByEffectiveFromDesc(String serviceCode);

    List<CommissionRule> findAllByOrderByServiceCodeAscEffectiveFromDesc();

    Optional<CommissionRule> findByRuleCode(String ruleCode);

    boolean existsByRuleCode(String ruleCode);
}

interface CommissionEntryRepository extends JpaRepository<CommissionEntry, Long> {
    List<CommissionEntry> findBySourceTxnIdOrderByIdAsc(Long sourceTxnId);

    Page<CommissionEntry> findByEarnerAccountIdOrderByIdDesc(Long earnerAccountId, Pageable pageable);

    Page<CommissionEntry> findByEarnerAccountIdAndStatusOrderByIdDesc(Long earnerAccountId, String status, Pageable pageable);

    @Query("select coalesce(sum(c.commissionAmount), 0) from CommissionEntry c where c.earnerAccountId = :accountId and c.status = :status and c.createdAt >= :from")
    BigDecimal sumForEarner(Long accountId, String status, OffsetDateTime from);

    @Query("select coalesce(sum(c.commissionAmount), 0) from CommissionEntry c where c.status = :status and c.createdAt >= :from")
    BigDecimal sumAll(String status, OffsetDateTime from);

    @Query("select coalesce(sum(c.commissionAmount), 0) from CommissionEntry c where c.status = 'CREDITED'")
    BigDecimal totalCredited();
}
