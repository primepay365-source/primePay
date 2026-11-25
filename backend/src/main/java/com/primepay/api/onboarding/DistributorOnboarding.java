package com.primepay.api.onboarding;

import com.primepay.api.account.AgentAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "distributor_onboarding")
public class DistributorOnboarding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "distributor_account_id", nullable = false, unique = true)
    private AgentAccount distributor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_account_id", nullable = false)
    private AgentAccount createdBy;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(nullable = false, length = 20)
    private String mobile;

    @Column(nullable = false, length = 160)
    private String email;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(name = "photo_content_type", nullable = false, length = 100)
    private String photoContentType;

    @Lob
    @Column(name = "photo_data", nullable = false)
    private byte[] photoData;

    @Column(name = "kyc_document_type", nullable = false, length = 32)
    private String kycDocumentType;

    @Column(name = "kyc_last_four", nullable = false, length = 4)
    private String kycLastFour;

    @Column(name = "bank_account_holder", nullable = false, length = 160)
    private String bankAccountHolder;

    @Column(name = "bank_name", nullable = false, length = 120)
    private String bankName;

    @Column(name = "bank_ifsc", nullable = false, length = 11)
    private String bankIfsc;

    @Column(name = "bank_account_last_four", nullable = false, length = 4)
    private String bankAccountLastFour;

    @Column(name = "velocity_per_transaction", nullable = false, precision = 18, scale = 2)
    private BigDecimal velocityPerTransaction;

    @Column(name = "velocity_daily", nullable = false, precision = 18, scale = 2)
    private BigDecimal velocityDaily;

    @Column(name = "commission_payin", nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionPayin;

    @Column(name = "commission_payout", nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionPayout;

    @Column(name = "commission_bill_payment", nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionBillPayment;

    @Column(name = "agreement_version", nullable = false, length = 40)
    private String agreementVersion;

    @Column(name = "agreement_accepted_at", nullable = false)
    private OffsetDateTime agreementAcceptedAt;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;

    protected DistributorOnboarding() {
    }

    public DistributorOnboarding(
            AgentAccount distributor,
            AgentAccount createdBy,
            OnboardDistributorRequest request,
            String photoContentType,
            byte[] photoData,
            OffsetDateTime now) {
        this.distributor = distributor;
        this.createdBy = createdBy;
        this.fullName = request.fullName().trim();
        this.mobile = request.mobile().trim();
        this.email = request.email().trim().toLowerCase();
        this.address = request.address().trim();
        this.photoContentType = photoContentType;
        this.photoData = photoData;
        this.kycDocumentType = request.kycDocumentType();
        this.kycLastFour = request.kycLastFour().toUpperCase();
        this.bankAccountHolder = request.bankAccountHolder().trim();
        this.bankName = request.bankName().trim();
        this.bankIfsc = request.bankIfsc().trim().toUpperCase();
        this.bankAccountLastFour = request.bankAccountLastFour();
        this.velocityPerTransaction = request.velocityPerTransaction();
        this.velocityDaily = request.velocityDaily();
        this.commissionPayin = request.commissionPayin();
        this.commissionPayout = request.commissionPayout();
        this.commissionBillPayment = request.commissionBillPayment();
        this.agreementVersion = request.agreementVersion();
        this.agreementAcceptedAt = now;
        this.status = "SUBMITTED";
        this.submittedAt = now;
    }

    public Long getId() { return id; }
    public AgentAccount getDistributor() { return distributor; }
    public String getFullName() { return fullName; }
    public String getStatus() { return status; }
    public OffsetDateTime getSubmittedAt() { return submittedAt; }
}
