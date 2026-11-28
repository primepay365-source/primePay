package com.primepay.commission;

import com.primepay.audit.AuditService;
import com.primepay.common.api.ApiResponse;
import com.primepay.common.error.BusinessException;
import com.primepay.common.error.ErrorCode;
import com.primepay.common.web.CsvWriter;
import com.primepay.security.CurrentActor;
import com.primepay.transaction.FinancialTransaction;
import com.primepay.transaction.FinancialTransactionRepository;
import com.primepay.hierarchy.HierarchyService;
import com.primepay.transaction.TxnStatus;
import com.primepay.transaction.TxnType;
import com.primepay.user.AccountRole;
import com.primepay.user.AccountService;
import com.primepay.user.AgentAccount;
import com.primepay.user.AgentAccountRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Commission")
@RestController
@RequestMapping("/api/v1/commissions")
public class CommissionController {
    private static final Set<String> LEVELS = Set.of("RETAILER", "DISTRIBUTOR", "MASTER_DISTRIBUTOR", "SUPER_DISTRIBUTOR", "PLATFORM");

    private final CurrentActor currentActor;
    private final CommissionRuleRepository rules;
    private final CommissionEntryRepository entries;
    private final FinancialTransactionRepository transactions;
    private final AgentAccountRepository accounts;
    private final AuditService audit;
    private final ZoneId zone;
    private final HierarchyService hierarchy;
    private final CommissionEngine engine;

    public CommissionController(CurrentActor currentActor, CommissionRuleRepository rules, CommissionEntryRepository entries,
                                FinancialTransactionRepository transactions, AgentAccountRepository accounts, AuditService audit,
                                HierarchyService hierarchy, CommissionEngine engine,
                                @Value("${app.business-timezone:Asia/Kolkata}") String zone) {
        this.currentActor = currentActor;
        this.rules = rules;
        this.entries = entries;
        this.transactions = transactions;
        this.accounts = accounts;
        this.audit = audit;
        this.zone = ZoneId.of(zone);
        this.hierarchy = hierarchy;
        this.engine = engine;
    }

    public record ShareView(String levelRole, BigDecimal rate, BigDecimal fixedAmount, BigDecimal minAmount, BigDecimal maxAmount) {
    }

    public record RuleView(Long id, String ruleCode, String serviceCode, String calcType, BigDecimal slabFrom, BigDecimal slabTo,
                           LocalDate effectiveFrom, LocalDate effectiveTo, String status, String state, List<ShareView> shares) {
    }

    @GetMapping("/rules")
    @Transactional(readOnly = true)
    public ApiResponse<List<RuleView>> list() {
        currentActor.require();
        LocalDate today = LocalDate.now(zone);
        return ApiResponse.ok(rules.findAllByOrderByServiceCodeAscEffectiveFromDesc().stream().map(r -> view(r, today)).toList());
    }

    public record ShareBody(@NotBlank String levelRole, @DecimalMin("0") BigDecimal rate, @DecimalMin("0") BigDecimal fixedAmount,
                            @DecimalMin("0") BigDecimal minAmount, @DecimalMin("0") BigDecimal maxAmount) {
    }

    public record RuleBody(@NotBlank @Size(max = 30) @Pattern(regexp = "[A-Z0-9-]+") String ruleCode,
                           @NotBlank @Pattern(regexp = "PAYOUT|BILL_PAYMENT|ADD_FUNDS|WALLET_TRANSFER|WALLET_LOAD") String serviceCode,
                           @NotBlank @Pattern(regexp = "PERCENT|FIXED") String calcType,
                           @DecimalMin("0") BigDecimal slabFrom, @DecimalMin("0") BigDecimal slabTo,
                           @NotNull LocalDate effectiveFrom, LocalDate effectiveTo,
                           @NotEmpty List<@Valid ShareBody> shares) {
    }

    @PostMapping("/rules")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ApiResponse<RuleView> create(@Valid @RequestBody RuleBody body) {
        AgentAccount admin = currentActor.requireRole(AccountRole.ADMIN);
        if (rules.existsByRuleCode(body.ruleCode())) {
            throw new BusinessException(ErrorCode.DUPLICATE, "Rule code " + body.ruleCode() + " already exists.");
        }
        if (body.effectiveTo() != null && body.effectiveTo().isBefore(body.effectiveFrom())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "End date must be on or after the start date.");
        }
        if (body.slabFrom() != null && body.slabTo() != null && body.slabTo().compareTo(body.slabFrom()) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Slab end must be at least the slab start.");
        }
        CommissionRule rule = new CommissionRule(body.ruleCode(), body.serviceCode(), body.calcType(), body.slabFrom(), body.slabTo(),
                body.effectiveFrom(), body.effectiveTo(), admin.getId());
        Set<String> seen = new java.util.HashSet<>();
        BigDecimal totalRate = BigDecimal.ZERO;
        for (ShareBody s : body.shares()) {
            if (!LEVELS.contains(s.levelRole())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Unknown level " + s.levelRole() + ".");
            }
            if (!seen.add(s.levelRole())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Each level can appear only once in a rule (" + s.levelRole() + ").");
            }
            if ("PERCENT".equals(body.calcType()) && s.rate() != null) {
                totalRate = totalRate.add(s.rate());
            }
        }
        if (totalRate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "The level shares add up to more than 100% of the transaction.");
        }
        for (ShareBody s : body.shares()) {
            rule.getShares().add(new CommissionShare(s.levelRole(), s.rate(), s.fixedAmount(), s.minAmount(), s.maxAmount()));
        }
        // An overlapping active rule for the same service and slab starting on the same day would be ambiguous.
        boolean clash = rules.findByServiceCodeOrderByEffectiveFromDesc(body.serviceCode()).stream()
                .anyMatch(r -> "ACTIVE".equals(r.getStatus()) && r.getEffectiveFrom().equals(body.effectiveFrom())
                        && java.util.Objects.equals(r.getSlabFrom(), body.slabFrom()));
        if (clash) {
            throw new BusinessException(ErrorCode.DUPLICATE, "An active rule for this service and slab already starts on that date.");
        }
        CommissionRule saved = rules.save(rule);
        audit.record(admin, "COMMISSION_RULE_CREATE", "COMMISSION_RULE", saved.getRuleCode(),
                body.serviceCode() + " " + body.calcType() + " from " + body.effectiveFrom());
        return ApiResponse.ok(view(saved, LocalDate.now(zone)), "Rule saved");
    }

    @PostMapping("/rules/{id}/deactivate")
    @Transactional
    public ApiResponse<RuleView> deactivate(@PathVariable Long id) {
        AgentAccount admin = currentActor.requireRole(AccountRole.ADMIN);
        CommissionRule rule = rules.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Rule not found."));
        rule.setStatus("INACTIVE");
        audit.record(admin, "COMMISSION_RULE_DEACTIVATE", "COMMISSION_RULE", rule.getRuleCode(), null);
        return ApiResponse.ok(view(rule, LocalDate.now(zone)), "Rule deactivated");
    }

    public record PreviewLine(String levelRole, BigDecimal rate, BigDecimal amount) {
    }

    @GetMapping("/rules/{id}/preview")
    @Transactional(readOnly = true)
    public ApiResponse<List<PreviewLine>> preview(@PathVariable Long id, @RequestParam BigDecimal amount) {
        currentActor.require();
        CommissionRule rule = rules.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Rule not found."));
        return ApiResponse.ok(rule.getShares().stream()
                .map(s -> new PreviewLine(s.getLevelRole(), s.getRate(), s.calculate(rule.getCalcType(), amount))).toList());
    }

    public record StatementLine(Long id, OffsetDateTime createdAt, String transactionRef, Long transactionId, String fromCode,
                                String service, BigDecimal baseAmount, String ruleCode, String levelRole, BigDecimal amount, String status) {
    }

    /** One direct child account (or "own transactions") and the commission this account earned from its subtree. */
    public record ChildSummary(Long accountId, String accountCode, String name, String role, BigDecimal commission, long entries) {
    }

    /** The share configured for one level in an active rule. */
    public record RateView(String serviceCode, String ruleCode, String calcType, String levelRole, BigDecimal rate, BigDecimal fixedAmount,
                           BigDecimal minAmount, BigDecimal maxAmount, BigDecimal slabFrom, BigDecimal slabTo) {
    }

    /**
     * Commission statement. For a network account: what it earned. For the Platform Admin: the whole platform
     * (paid to the network plus the platform share). "Paid" is credited to the wallet at the moment the transaction
     * succeeds; "pending" is what transactions still awaiting a final status would pay if they succeed.
     */
    public record Statement(String scope, BigDecimal earnedThisMonth, BigDecimal creditedThisMonth, BigDecimal reversedThisMonth,
                            BigDecimal earnedAllTime, BigDecimal paidAllTime, BigDecimal reversedAllTime, BigDecimal platformShareAllTime,
                            BigDecimal pendingAmount, long pendingTransactions, List<ChildSummary> children, List<RateView> myRates,
                            List<StatementLine> lines) {
    }

    @GetMapping("/statement")
    @Transactional(readOnly = true)
    public ApiResponse<Statement> statement(@RequestParam(required = false) String status) {
        return ApiResponse.ok(buildStatement(currentActor.require(), status));
    }

    @GetMapping(value = "/statement.csv", produces = "text/csv")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> statementCsv(@RequestParam(required = false) String status) {
        CsvWriter csv = new CsvWriter("Date", "Transaction", "From", "Service", "Transaction amount", "Rule", "Level", "Commission", "Status");
        buildStatement(currentActor.require(), status).lines().forEach(l -> csv.row(l.createdAt(), l.transactionRef(), l.fromCode(),
                l.service(), l.baseAmount(), l.ruleCode(), l.levelRole(), l.amount(), l.status()));
        return csv.download("primepay-commission-statement.csv");
    }

    /**
     * Configured commission share for a level, from the active rules. Allowed for your own level and for the
     * levels you can create or manage, so a parent sees what a new child will earn; nobody else's earnings.
     */
    @GetMapping("/rates")
    @Transactional(readOnly = true)
    public ApiResponse<List<RateView>> rates(@RequestParam AccountRole role) {
        AgentAccount me = currentActor.require();
        if (me.getRole() != AccountRole.ADMIN && me.getRole() != role && !AccountService.canManage(me.getRole(), role)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "You can't view commission for this level.");
        }
        return ApiResponse.ok(ratesFor(role.name()));
    }

    private List<RateView> ratesFor(String level) {
        LocalDate today = LocalDate.now(zone);
        return rules.findAllByOrderByServiceCodeAscEffectiveFromDesc().stream()
                .filter(r -> "ACTIVE".equals(r.getStatus()) && !r.getEffectiveFrom().isAfter(today)
                        && (r.getEffectiveTo() == null || !r.getEffectiveTo().isBefore(today)))
                .flatMap(r -> r.getShares().stream().filter(s -> s.getLevelRole().equals(level))
                        .map(s -> new RateView(r.getServiceCode(), r.getRuleCode(), r.getCalcType(), s.getLevelRole(), s.getRate(), s.getFixedAmount(),
                                s.getMinAmount(), s.getMaxAmount(), r.getSlabFrom(), r.getSlabTo())))
                .toList();
    }

    private Statement buildStatement(AgentAccount me, String status) {
        boolean admin = me.getRole() == AccountRole.ADMIN;
        OffsetDateTime monthStart = LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone).toOffsetDateTime();
        Map<Long, AgentAccount> byId = accounts.findAll().stream().collect(Collectors.toMap(AgentAccount::getId, Function.identity()));
        // Admin sees every network share plus the platform share; everyone else sees only what they earned.
        List<CommissionEntry> mine = entries.findAll().stream()
                .filter(c -> admin || me.getId().equals(c.getEarnerAccountId()))
                .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                .toList();
        Map<Long, FinancialTransaction> txns = transactions.findAllById(mine.stream().map(CommissionEntry::getSourceTxnId).distinct().toList())
                .stream().collect(Collectors.toMap(FinancialTransaction::getId, Function.identity()));
        Map<Long, String> ruleCodes = rules.findAll().stream().collect(Collectors.toMap(CommissionRule::getId, CommissionRule::getRuleCode));

        List<StatementLine> lines = mine.stream()
                .filter(c -> status == null || status.isBlank() || status.equals(c.getStatus()))
                .limit(500)
                .map(c -> {
                    FinancialTransaction t = txns.get(c.getSourceTxnId());
                    String earner = c.getEarnerAccountId() == null ? "Platform" : code(byId, c.getEarnerAccountId());
                    return new StatementLine(c.getId(), c.getCreatedAt(), t.getReferenceNo(), t.getId(), code(byId, t.getInitiatorAccountId()),
                            t.getType().label(), c.getBaseAmount(), ruleCodes.get(c.getRuleId()),
                            admin ? c.getLevelRole() + " · " + earner : c.getLevelRole(),
                            "REVERSED".equals(c.getStatus()) ? c.getCommissionAmount().negate() : c.getCommissionAmount(), c.getStatus());
                }).toList();

        BigDecimal credited = sum(mine, "CREDITED", monthStart);
        BigDecimal reversed = sum(mine, "REVERSED", monthStart);
        OffsetDateTime ever = OffsetDateTime.parse("2000-01-01T00:00:00Z");
        BigDecimal paidAll = sum(mine, "CREDITED", ever);
        BigDecimal reversedAll = sum(mine, "REVERSED", ever);
        BigDecimal platformAll = sum(mine, "PLATFORM", ever);
        if (admin) {
            credited = credited.add(sum(mine, "PLATFORM", monthStart));
        }

        // Pending: business transactions in my scope still awaiting a final status.
        Set<Long> scope = admin ? byId.keySet() : hierarchy.descendantsIncludingSelf(me.getId());
        BigDecimal pending = BigDecimal.ZERO;
        long pendingCount = 0;
        for (FinancialTransaction t : transactions.findByStatus(TxnStatus.PENDING)) {
            AgentAccount initiator = byId.get(t.getInitiatorAccountId());
            if (initiator == null || !scope.contains(initiator.getId()) || t.getType() == TxnType.WALLET_LOAD) {
                continue;
            }
            BigDecimal expected = admin ? engine.expectedTotal(t) : engine.expectedShare(t, initiator, me);
            if (expected.signum() > 0) {
                pending = pending.add(expected);
                pendingCount++;
            }
        }

        // Child-wise summary: which direct child's network the commission came from.
        List<AgentAccount> direct = byId.values().stream()
                .filter(a -> a.getParent() != null && a.getParent().getId().equals(me.getId()))
                .sorted(Comparator.comparing(AgentAccount::getAccountCode)).toList();
        Map<Long, Long> ownerOf = new HashMap<>();
        for (AgentAccount child : direct) {
            for (Long id : hierarchy.descendantsIncludingSelf(child.getId())) {
                ownerOf.put(id, child.getId());
            }
        }
        Map<Long, BigDecimal> byChild = new HashMap<>();
        Map<Long, Long> countByChild = new HashMap<>();
        for (CommissionEntry c : mine) {
            if ("REVERSED".equals(c.getStatus())) {
                continue;
            }
            Long key = ownerOf.getOrDefault(txns.get(c.getSourceTxnId()).getInitiatorAccountId(), me.getId());
            byChild.merge(key, c.getCommissionAmount(), BigDecimal::add);
            countByChild.merge(key, 1L, Long::sum);
        }
        List<ChildSummary> children = new ArrayList<>();
        if (!admin) {
            children.add(new ChildSummary(me.getId(), me.getAccountCode(), "Own transactions", me.getRole().name(),
                    byChild.getOrDefault(me.getId(), BigDecimal.ZERO), countByChild.getOrDefault(me.getId(), 0L)));
        }
        for (AgentAccount child : direct) {
            children.add(new ChildSummary(child.getId(), child.getAccountCode(), child.getDisplayName(), child.getRole().name(),
                    byChild.getOrDefault(child.getId(), BigDecimal.ZERO), countByChild.getOrDefault(child.getId(), 0L)));
        }

        return new Statement(admin ? "PLATFORM" : "OWN", credited.add(reversed), credited, reversed,
                paidAll.add(reversedAll).add(platformAll), paidAll, reversedAll, platformAll, pending, pendingCount, children,
                admin ? List.of() : ratesFor(me.getRole().name()), lines);
    }

    private static BigDecimal sum(List<CommissionEntry> list, String status, OffsetDateTime from) {
        return list.stream().filter(c -> status.equals(c.getStatus()) && !c.getCreatedAt().isBefore(from))
                .map(CommissionEntry::getCommissionAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String code(Map<Long, AgentAccount> byId, Long id) {
        AgentAccount a = id == null ? null : byId.get(id);
        return a == null ? null : a.getAccountCode();
    }

    private static RuleView view(CommissionRule r, LocalDate today) {
        String state;
        if (!"ACTIVE".equals(r.getStatus())) state = "Inactive";
        else if (r.getEffectiveFrom().isAfter(today)) state = "Scheduled";
        else if (r.getEffectiveTo() != null && r.getEffectiveTo().isBefore(today)) state = "Expired";
        else state = "Active";
        return new RuleView(r.getId(), r.getRuleCode(), r.getServiceCode(), r.getCalcType(), r.getSlabFrom(), r.getSlabTo(),
                r.getEffectiveFrom(), r.getEffectiveTo(), r.getStatus(), state,
                r.getShares().stream().map(s -> new ShareView(s.getLevelRole(), s.getRate(), s.getFixedAmount(), s.getMinAmount(), s.getMaxAmount())).toList());
    }
}
