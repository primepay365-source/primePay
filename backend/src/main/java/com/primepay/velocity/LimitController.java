package com.primepay.velocity;

import com.primepay.audit.AuditService;
import com.primepay.common.api.ApiResponse;
import com.primepay.common.error.BusinessException;
import com.primepay.common.error.ErrorCode;
import com.primepay.security.CurrentActor;
import com.primepay.user.AccountRole;
import com.primepay.user.AgentAccount;
import com.primepay.user.AgentAccountRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Velocity limits")
@RestController
@RequestMapping("/api/v1/limits")
public class LimitController {
    private final CurrentActor currentActor;
    private final TransactionLimitRepository limits;
    private final AgentAccountRepository accounts;
    private final AuditService audit;

    public LimitController(CurrentActor currentActor, TransactionLimitRepository limits, AgentAccountRepository accounts, AuditService audit) {
        this.currentActor = currentActor;
        this.limits = limits;
        this.accounts = accounts;
        this.audit = audit;
    }

    public record LimitView(Long id, String scope, String roleCode, Long accountId, String accountCode, String serviceCode,
                            BigDecimal minTxn, BigDecimal maxTxn, BigDecimal dailyAmount, BigDecimal monthlyAmount,
                            int dailyCount, int monthlyCount, LocalDate validUntil, String setBy, OffsetDateTime updatedAt) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<List<LimitView>> list() {
        currentActor.requireRole(AccountRole.ADMIN);
        Map<Long, String> codes = accounts.findAll().stream().collect(Collectors.toMap(AgentAccount::getId, AgentAccount::getAccountCode));
        return ApiResponse.ok(limits.findAll().stream()
                .sorted((a, b) -> (a.getScope() + a.getServiceCode() + a.getRoleCode()).compareTo(b.getScope() + b.getServiceCode() + b.getRoleCode()))
                .map(l -> view(l, codes)).toList());
    }

    public record LimitBody(@NotNull @DecimalMin("0") BigDecimal minTxn, @NotNull @DecimalMin("0.01") BigDecimal maxTxn,
                            @NotNull @DecimalMin("0.01") BigDecimal dailyAmount, @NotNull @DecimalMin("0.01") BigDecimal monthlyAmount,
                            @Min(1) int dailyCount, @Min(1) int monthlyCount, LocalDate validUntil) {
    }

    @PutMapping("/{id}")
    @Transactional
    public ApiResponse<LimitView> update(@PathVariable Long id, @Valid @RequestBody LimitBody body) {
        AgentAccount admin = currentActor.requireRole(AccountRole.ADMIN);
        TransactionLimit limit = limits.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Limit not found."));
        validate(body);
        limit.update(body.minTxn(), body.maxTxn(), body.dailyAmount(), body.monthlyAmount(), body.dailyCount(), body.monthlyCount(),
                body.validUntil(), admin.getId());
        audit.record(admin, "LIMIT_UPDATE", "LIMIT", id, describe(limit));
        return ApiResponse.ok(view(limit, codes()), "Limit saved");
    }

    public record OverrideBody(@NotNull Long accountId, @NotBlank @Pattern(regexp = "PAYOUT|WALLET_TRANSFER|WALLET_LOAD") String serviceCode,
                               @NotNull @Valid LimitBody limit) {
    }

    @PostMapping("/overrides")
    @Transactional
    public ApiResponse<LimitView> addOverride(@Valid @RequestBody OverrideBody body) {
        AgentAccount admin = currentActor.requireRole(AccountRole.ADMIN);
        AgentAccount target = accounts.findById(body.accountId()).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Account not found."));
        validate(body.limit());
        TransactionLimit limit = limits.findByScopeAndAccountIdAndServiceCode("USER", target.getId(), body.serviceCode())
                .orElseGet(() -> TransactionLimit.forUser(target.getId(), body.serviceCode()));
        LimitBody l = body.limit();
        limit.update(l.minTxn(), l.maxTxn(), l.dailyAmount(), l.monthlyAmount(), l.dailyCount(), l.monthlyCount(), l.validUntil(), admin.getId());
        TransactionLimit saved = limits.save(limit);
        audit.record(admin, "LIMIT_OVERRIDE", "USER", target.getAccountCode(), describe(saved));
        return ApiResponse.ok(view(saved, codes()), "Override saved");
    }

    @DeleteMapping("/overrides/{id}")
    @Transactional
    public ApiResponse<Void> removeOverride(@PathVariable Long id) {
        AgentAccount admin = currentActor.requireRole(AccountRole.ADMIN);
        TransactionLimit limit = limits.findById(id).filter(l -> "USER".equals(l.getScope()))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Override not found."));
        limits.delete(limit);
        audit.record(admin, "LIMIT_OVERRIDE_REMOVE", "LIMIT", id, describe(limit));
        return ApiResponse.ok(null, "Override removed");
    }

    private static void validate(LimitBody b) {
        if (b.maxTxn().compareTo(b.minTxn()) < 0 || b.dailyAmount().compareTo(b.maxTxn()) < 0 || b.monthlyAmount().compareTo(b.dailyAmount()) < 0
                || b.monthlyCount() < b.dailyCount()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Limits must grow: min ≤ max ≤ daily ≤ monthly, and daily count ≤ monthly count.");
        }
    }

    private Map<Long, String> codes() {
        return accounts.findAll().stream().collect(Collectors.toMap(AgentAccount::getId, AgentAccount::getAccountCode));
    }

    private static String describe(TransactionLimit l) {
        return l.getServiceCode() + " min=" + l.getMinTxn() + " max=" + l.getMaxTxn() + " daily=" + l.getDailyAmount() + " monthly=" + l.getMonthlyAmount();
    }

    private static LimitView view(TransactionLimit l, Map<Long, String> codes) {
        return new LimitView(l.getId(), l.getScope(), l.getRoleCode(), l.getAccountId(), l.getAccountId() == null ? null : codes.get(l.getAccountId()),
                l.getServiceCode(), l.getMinTxn(), l.getMaxTxn(), l.getDailyAmount(), l.getMonthlyAmount(), l.getDailyCount(), l.getMonthlyCount(),
                l.getValidUntil(), l.getSetByAccountId() == null ? "Seed" : codes.get(l.getSetByAccountId()), l.getUpdatedAt());
    }
}
