package com.primepay.api.account;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountUserRepository extends JpaRepository<AccountUser, Long> {
    Optional<AccountUser> findByUsername(String username);

    boolean existsByUsername(String username);
}
