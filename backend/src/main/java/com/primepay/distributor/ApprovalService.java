package com.primepay.distributor;

import com.primepay.audit.AuditService;
import com.primepay.common.error.BusinessException;
import com.primepay.common.error.ErrorCode;
import com.primepay.notification.NotificationService;
import com.primepay.user.AccountRole;
import com.primepay.user.AccountStatus;
import com.primepay.user.AgentAccount;
import com.primepay.user.UserService;
import com.primepay.velocity.TransactionLimit;
import com.primepay.velocity.TransactionLimitRepository;
import com.primepay.wallet.WalletService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Platform Admin review of onboarding applications. Approval activates the account and creates its wallet. */
@Service
public class ApprovalService {
    private final DistributorOnboardingRepository onboardings;
    private final WalletService wallets;
    private final TransactionLimitRepository limits;
    private final UserService users;
    private final NotificationService notifications;
    private final AuditService audit;

    public ApprovalService(DistributorOnboardingRepository onboardings, WalletService wallets, TransactionLimitRepository limits,
                           UserService users, NotificationService notifications, AuditService audit) {
        this.onboardings = onboardings;
        this.wallets = wallets;
        this.limits = limits;
        this.users = users;
        this.notifications = notifications;
        this.audit = audit;
    }

    public record Application(Long id, Long accountId, String accountCode, String fullName, String role, String submittedBy,
                              String mobile, String email, String address, String kycDocumentType, String kycLastFour,
                              String bankAccountHolder, String bankName, String bankIfsc, String bankAccountLastFour,
                              BigDecimal velocityPerTransaction, BigDecimal velocityDaily, BigDecimal commissionPayout,
                              String agreementVersion, OffsetDateTime agreementAcceptedAt, String status, OffsetDateTime submittedAt,
                              String decisionNote, OffsetDateTime decidedAt) {
    }

    @Transactional(readOnly = true)
    public List<Application> list(AgentAccount actor) {
        return onboardings.findAll().stream()
                .filter(o -> actor.getRole() == AccountRole.ADMIN || o.getCreatedBy().getId().equals(actor.getId()))
                .sorted(Comparator.comparing(DistributorOnboarding::getSubmittedAt).reversed())
                .map(ApprovalService::view)
                .toList();
    }

    @Transactional
    public Application decide(AgentAccount admin, Long id, boolean approve, String note) {
        if (admin.getRole() != AccountRole.ADMIN) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Only a Platform Admin can approve applications.");
        }
        if (!approve && (note == null || note.isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Add a note explaining the rejection.");
        }
        DistributorOnboarding application = onboardings.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Application not found."));
        if (!"SUBMITTED".equals(application.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "This application was already decided.");
        }
        AgentAccount account = application.getDistributor();
        AccountStatus previous = account.getStatus();
        application.decide(approve, admin.getId(), note);
        if (approve) {
            account.changeStatus(AccountStatus.ACTIVE);
            wallets.getOrCreate(account);
            // The velocity values captured in step 4 become this user's transfer limits.
            TransactionLimit limit = limits.findByScopeAndAccountIdAndServiceCode("USER", account.getId(), "WALLET_TRANSFER")
                    .orElseGet(() -> TransactionLimit.forUser(account.getId(), "WALLET_TRANSFER"));
            BigDecimal daily = application.getVelocityDaily();
            limit.update(BigDecimal.ONE, application.getVelocityPerTransaction(), daily, daily.multiply(BigDecimal.valueOf(30)), 1000, 25000, null, admin.getId());
            limits.save(limit);
        } else {
            account.changeStatus(AccountStatus.REJECTED);
        }
        users.recordStatus(account.getId(), previous, account.getStatus(), approve ? "Application approved" : note, admin.getId());
        audit.record(admin, approve ? "ONBOARD_APPROVE" : "ONBOARD_REJECT", "USER", account.getAccountCode(), note);
        notifications.notify(application.getCreatedBy().getId(), NotificationService.ACCOUNT,
                approve ? "Application approved" : "Application rejected",
                account.getAccountCode() + " · " + application.getFullName() + (approve ? " is now active." : " was rejected: " + note),
                "/users/" + account.getId());
        if (approve) {
            notifications.notify(account.getId(), NotificationService.ACCOUNT, "Welcome to PrimePay",
                    "Your distributor account " + account.getAccountCode() + " was approved and is now active.", "/dashboard");
        }
        return view(application);
    }

    private static Application view(DistributorOnboarding o) {
        return new Application(o.getId(), o.getDistributor().getId(), o.getDistributor().getAccountCode(), o.getFullName(),
                o.getDistributor().getRole().name(), o.getCreatedBy().getAccountCode(), o.getMobile(), o.getEmail(), o.getAddress(),
                o.getKycDocumentType(), o.getKycLastFour(), o.getBankAccountHolder(), o.getBankName(), o.getBankIfsc(),
                o.getBankAccountLastFour(), o.getVelocityPerTransaction(), o.getVelocityDaily(), o.getCommissionPayout(),
                o.getAgreementVersion(), o.getAgreementAcceptedAt(), o.getStatus(), o.getSubmittedAt(), o.getDecisionNote(), o.getDecidedAt());
    }
}
