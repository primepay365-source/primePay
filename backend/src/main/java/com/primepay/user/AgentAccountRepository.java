package com.primepay.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentAccountRepository extends JpaRepository<AgentAccount, Long> {
    Optional<AgentAccount> findByAccountCode(String accountCode);

    boolean existsByAccountCode(String accountCode);

    List<AgentAccount> findByParent_IdIn(Collection<Long> parentIds);
}
