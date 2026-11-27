package com.primepay.transaction;

import com.primepay.common.error.BusinessException;
import com.primepay.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One financial operation. Completed rows are never deleted; corrections are new REVERSAL/ADJUSTMENT rows. */
@Getter
@Entity
@Table(name = "pp_transactions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinancialTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference_no", nullable = false, unique = true, length = 30)
    private String referenceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type", nullable = false, length = 20)
    private TxnType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TxnStatus status;

    @Column(name = "service_code", nullable = false, length = 30)
    private String serviceCode;

    @Column(name = "initiator_account_id", nullable = false)
    private Long initiatorAccountId;

    @Setter
    @Column(name = "source_account_id")
    private Long sourceAccountId;

    @Setter
    @Column(name = "target_account_id")
    private Long targetAccountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal fee = BigDecimal.ZERO;

    @Column(name = "idempotency_key", length = 80)
    private String idempotencyKey;

    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @Setter
    @Column(name = "original_txn_id")
    private Long originalTxnId;

    @Setter
    @Column(length = 300)
    private String remarks;

    @Setter
    @Column(name = "external_ref", length = 80)
    private String externalRef;

    @Setter
    @Column(name = "payout_mode", length = 10)
    private String payoutMode;

    @Setter
    @Column(name = "beneficiary_name", length = 160)
    private String beneficiaryName;

    @Setter
    @Column(name = "beneficiary_account_last4", length = 4)
    private String beneficiaryAccountLast4;

    @Setter
    @Column(name = "beneficiary_ifsc", length = 11)
    private String beneficiaryIfsc;

    @Setter
    @Column(name = "beneficiary_bank", length = 120)
    private String beneficiaryBank;

    @Setter
    @Column(name = "biller_name", length = 120)
    private String billerName;

    @Setter
    @Column(name = "customer_name", length = 160)
    private String customerName;

    @Setter
    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Setter
    @Column(name = "card_network", length = 20)
    private String cardNetwork;

    @Setter
    @Column(name = "gateway_category", length = 20)
    private String gatewayCategory;

    @Setter
    @Column(name = "customer_mobile_masked", length = 20)
    private String customerMobileMasked;

    @Setter
    @Column(name = "failure_reason", length = 300)
    private String failureReason;

    @Setter
    @Column(name = "decided_by_account_id")
    private Long decidedByAccountId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    private Long version;

    public FinancialTransaction(String referenceNo, TxnType type, String serviceCode, Long initiatorAccountId,
                                BigDecimal amount, BigDecimal fee, String idempotencyKey, String requestHash) {
        this.referenceNo = referenceNo;
        this.type = type;
        this.serviceCode = serviceCode;
        this.initiatorAccountId = initiatorAccountId;
        this.amount = amount;
        this.fee = fee == null ? BigDecimal.ZERO : fee;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.status = TxnStatus.INITIATED;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    /** Moves to the next state if allowed; returns the previous state. */
    public TxnStatus moveTo(TxnStatus next) {
        if (!status.canMoveTo(next)) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "A " + status.name().toLowerCase() + " transaction can't become " + next.name().toLowerCase() + ".");
        }
        TxnStatus previous = status;
        status = next;
        updatedAt = OffsetDateTime.now();
        return previous;
    }

    public BigDecimal totalDebit() {
        return amount.add(fee);
    }
}
