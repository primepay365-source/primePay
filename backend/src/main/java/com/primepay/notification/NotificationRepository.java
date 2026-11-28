package com.primepay.notification;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByAccountIdOrderByCreatedAtDesc(Long accountId, Pageable pageable);

    Page<Notification> findByAccountIdAndCategoryOrderByCreatedAtDesc(Long accountId, String category, Pageable pageable);

    Page<Notification> findByAccountIdAndReadAtIsNullOrderByCreatedAtDesc(Long accountId, Pageable pageable);

    long countByAccountIdAndReadAtIsNull(Long accountId);

    List<Notification> findByAccountId(Long accountId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.accountId = :accountId and n.readAt is null")
    int markAllRead(Long accountId, java.time.OffsetDateTime now);
}
