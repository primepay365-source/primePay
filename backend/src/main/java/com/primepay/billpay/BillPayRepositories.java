package com.primepay.billpay;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface BillerRepository extends JpaRepository<Biller, Long> {
    List<Biller> findByCategoryAndEnabledTrueOrderByNameAsc(String category);

    List<Biller> findAllByOrderByCategoryAscNameAsc();
}

interface BillFetchRepository extends JpaRepository<BillFetch, Long> {
    Optional<BillFetch> findByFetchRef(String fetchRef);
}
