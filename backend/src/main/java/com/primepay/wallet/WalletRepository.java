package com.primepay.wallet;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
    Optional<Wallet> findByAccountId(Long accountId);

    List<Wallet> findByAccountIdIn(Collection<Long> accountIds);

    /** SELECT ... FOR UPDATE. Callers lock wallets in ascending ID order to avoid deadlocks. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.id = :id")
    Optional<Wallet> lockById(Long id);
}
