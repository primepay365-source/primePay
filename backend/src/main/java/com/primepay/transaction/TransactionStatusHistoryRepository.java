package com.primepay.transaction;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionStatusHistoryRepository extends JpaRepository<TransactionStatusHistory, Long> {
    List<TransactionStatusHistory> findByTransactionIdOrderByIdAsc(Long transactionId);
}
