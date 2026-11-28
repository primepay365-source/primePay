package com.primepay.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentAccountRepository extends JpaRepository<AgentAccount, Long> {
    Optional<AgentAccount> findByAccountCode(String accountCode);

    boolean existsByAccountCode(String accountCode);

    /** Emails are stored lower-case and mobiles as +91XXXXXXXXXX (see ContactNormalizer). */
    Optional<AgentAccount> findByEmail(String email);

    Optional<AgentAccount> findByMobile(String mobile);

    boolean existsByEmailAndIdNot(String email, Long id);

    boolean existsByMobileAndIdNot(String mobile, Long id);

    boolean existsByEmail(String email);

    boolean existsByMobile(String mobile);

    List<AgentAccount> findByParent_IdIn(Collection<Long> parentIds);
}
