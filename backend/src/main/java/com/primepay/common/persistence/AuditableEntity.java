package com.primepay.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Base class for mutable PP_ tables: who created/changed the row, when, and an optimistic-lock version.
 * Used by the Phase 2 schema; the three Phase 1 tables predate these columns.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

    @CreatedDate
    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @CreatedBy
    @Column(name = "CREATED_BY", length = 80, updatable = false)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "UPDATED_AT")
    private OffsetDateTime updatedAt;

    @LastModifiedBy
    @Column(name = "UPDATED_BY", length = 80)
    private String updatedBy;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Long version;
}
