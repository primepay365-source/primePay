package com.primepay.velocity;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionLimitRepository extends JpaRepository<TransactionLimit, Long> {
    Optional<TransactionLimit> findByScopeAndRoleCodeAndServiceCode(String scope, String roleCode, String serviceCode);

    Optional<TransactionLimit> findByScopeAndAccountIdAndServiceCode(String scope, Long accountId, String serviceCode);

    List<TransactionLimit> findByScopeOrderByServiceCodeAscRoleCodeAsc(String scope);

    List<TransactionLimit> findByAccountId(Long accountId);
}
