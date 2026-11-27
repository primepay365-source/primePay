package com.primepay.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountUserRepository extends JpaRepository<AccountUser, Long> {
    Optional<AccountUser> findByUsername(String username);

    Optional<AccountUser> findByAccount_Id(Long accountId);

    boolean existsByUsername(String username);
}
