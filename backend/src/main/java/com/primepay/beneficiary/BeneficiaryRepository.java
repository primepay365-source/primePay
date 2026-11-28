package com.primepay.beneficiary;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface BeneficiaryRepository extends JpaRepository<Beneficiary, Long> {
    List<Beneficiary> findByAccountIdAndStatusNotOrderByIdDesc(Long accountId, String status);

    Optional<Beneficiary> findFirstByAccountIdAndAccountHashAndIfscAndStatus(Long accountId, String accountHash, String ifsc, String status);
}
