package com.primepay.user;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserStatusHistoryRepository extends JpaRepository<UserStatusHistory, Long> {
    List<UserStatusHistory> findByAccountIdOrderByIdDesc(Long accountId);
}
