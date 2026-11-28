package com.primepay.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "pp_transaction_status_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransactionStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private TxnStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private TxnStatus toStatus;

    @Column(length = 300)
    private String note;

    @Column(name = "changed_by_account_id")
    private Long changedByAccountId;

    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt;

    public TransactionStatusHistory(Long transactionId, TxnStatus fromStatus, TxnStatus toStatus, String note, Long changedBy) {
        this.transactionId = transactionId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.note = note;
        this.changedByAccountId = changedBy;
        this.changedAt = OffsetDateTime.now();
    }
}
