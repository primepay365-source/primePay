package com.primepay.addfunds;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface GatewayCategoryRepository extends JpaRepository<GatewayCategory, Long> {
    Optional<GatewayCategory> findByCode(String code);

    List<GatewayCategory> findAllByOrderByIdAsc();
}

interface PaymentSessionRepository extends JpaRepository<PaymentSession, Long> {
    Optional<PaymentSession> findBySessionRef(String sessionRef);
}
