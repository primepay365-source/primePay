package com.primepay.wallet;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdjustmentRepository extends JpaRepository<WalletAdjustment, Long> {
    List<WalletAdjustment> findAllByOrderByIdDesc();
}
