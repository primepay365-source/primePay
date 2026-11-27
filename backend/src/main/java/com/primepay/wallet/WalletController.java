package com.primepay.wallet;

import com.primepay.audit.AuditService;
import com.primepay.common.api.ApiResponse;
import com.primepay.common.error.BusinessException;
import com.primepay.common.error.ErrorCode;
import com.primepay.common.web.CsvWriter;
import com.primepay.hierarchy.HierarchyService;
import com.primepay.ledger.LedgerEntry;
import com.primepay.ledger.LedgerEntryRepository;
import com.primepay.security.CurrentActor;
import com.primepay.transaction.FinancialTransaction;
import com.primepay.transaction.FinancialTransactionRepository;
import com.primepay.transaction.TransactionView;
import com.primepay.user.AccountRole;
import com.primepay.user.AgentAccount;
import com.primepay.user.AgentAccountRepository;
import com.primepay.velocity.LimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Wallet")
@RestController
@RequestMapping("/api/v1")
public class WalletController {
    private final CurrentActor currentActor;
    private final WalletService walletService;
    private final TransferService transfers;
    private final AdjustmentService adjustmentService;
    private final LedgerEntryRepository ledger;
    private final FinancialTransactionRepository transactions;
    private final AgentAccountRepository accounts;
    private final HierarchyService hierarchy;
    private final LimitService limits;
    private final AuditService audit;
    private final ZoneId zone;

    public WalletController(CurrentActor currentActor, WalletService walletService, TransferService transfers,
                            AdjustmentService adjustmentService, LedgerEntryRepository ledger, FinancialTransactionRepository transactions,
                            AgentAccountRepository accounts, HierarchyService hierarchy, LimitService limits, AuditService audit,
                            @Value("${app.business-timezone:Asia/Kolkata}") String zone) {
        this.currentActor = currentActor;
        this.walletService = walletService;
        this.transfers = transfers;
        this.adjustmentService = adjustmentService;
        this.ledger = ledger;
        this.transactions = transactions;
        this.accounts = accounts;
        this.hierarchy = hierarchy;
        this.limits = limits;
        this.audit = audit;
        this.zone = ZoneId.of(zone);
    }

    public record WalletSummary(Long walletId, String walletCode, Long accountId, String accountCode, String status,
                                BigDecimal availableBalance, BigDecimal ledgerBalance, BigDecimal blockedBalance,
                                BigDecimal openingBalanceThisMonth, BigDecimal creditsThisMonth, BigDecimal debitsThisMonth,
                                BigDecimal totalCredit, BigDecimal totalDebit, BigDecimal pendingLoads, OffsetDateTime createdAt) {
    }

    @GetMapping("/wallets/me")
    @Transactional
    public ApiResponse<WalletSummary> mine() {
        AgentAccount me = currentActor.require();
        return ApiResponse.ok(summary(me));
    }

    @GetMapping("/wallets/{accountId}")
    @Transactional
    public ApiResponse<WalletSummary> forAccount(@PathVariable Long accountId) {
        AgentAccount target = hierarchy.requireInScope(currentActor.require(), accountId);
        return ApiResponse.ok(summary(target));
    }

    public record StatementRow(Long id, OffsetDateTime createdAt, String reference, Long transactionId, String type,
                               String description, String direction, BigDecimal amount, BigDecimal balanceAfter) {
    }

    @Operation(summary = "Ledger statement for your wallet (or a wallet in your scope)")
    @GetMapping("/wallets/statement")
    @Transactional
    public ApiResponse<List<StatementRow>> statement(@RequestParam(required = false) Long accountId,
                                                     @RequestParam(required = false) LocalDate from,
                                                     @RequestParam(required = false) LocalDate to,
                                                     @RequestParam(required = false) String direction) {
        return ApiResponse.ok(statementRows(accountId, from, to, direction));
    }

    @GetMapping(value = "/wallets/statement.csv", produces = "text/csv")
    @Transactional
    public ResponseEntity<byte[]> statementCsv(@RequestParam(required = false) Long accountId,
                                               @RequestParam(required = false) LocalDate from,
                                               @RequestParam(required = false) LocalDate to,
                                               @RequestParam(required = false) String direction) {
        CsvWriter csv = new CsvWriter("Date", "Reference", "Description", "Debit", "Credit", "Balance");
        statementRows(accountId, from, to, direction).forEach(r -> csv.row(r.createdAt(), r.reference(), r.description(),
                "DR".equals(r.direction()) ? r.amount() : null, "CR".equals(r.direction()) ? r.amount() : null, r.balanceAfter()));
        return csv.download("primepay-statement.csv");
    }

    public record BalancePoint(LocalDate date, BigDecimal closingBalance) {
    }

    @GetMapping("/wallets/me/balance-history")
    @Transactional
    public ApiResponse<List<BalancePoint>> balanceHistory(@RequestParam(defaultValue = "30") int days) {
        Wallet wallet = walletService.getOrCreate(currentActor.require());
        int span = Math.min(Math.max(days, 7), 90);
        LocalDate today = LocalDate.now(zone);
        LocalDate first = today.minusDays(span - 1L);
        OffsetDateTime start = first.atStartOfDay(zone).toOffsetDateTime();
        LedgerEntry before = ledger.findFirstByWalletIdAndCreatedAtLessThanOrderByIdDesc(wallet.getId(), start);
        BigDecimal running = before == null ? BigDecimal.ZERO : before.getBalanceAfter();
        List<LedgerEntry> entries = ledger.findByWalletIdAndCreatedAtGreaterThanEqualOrderByIdAsc(wallet.getId(), start);
        List<BalancePoint> points = new ArrayList<>();
        int index = 0;
        for (LocalDate day = first; !day.isAfter(today); day = day.plusDays(1)) {
            OffsetDateTime end = day.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
            while (index < entries.size() && entries.get(index).getCreatedAt().isBefore(end)) {
                running = entries.get(index).getBalanceAfter();
                index++;
            }
            points.add(new BalancePoint(day, running));
        }
        return ApiResponse.ok(points);
    }

    public record Recipient(Long accountId, String accountCode, String displayName, String businessName, String role, BigDecimal walletBalance) {
    }

    @Operation(summary = "Accounts you may transfer to: your direct, active children")
    @GetMapping("/wallets/transfer/recipients")
    @Transactional(readOnly = true)
    public ApiResponse<List<Recipient>> recipients() {
        AgentAccount me = currentActor.require();
        List<AgentAccount> children = accounts.findByParent_IdIn(List.of(me.getId())).stream()
                .filter(AgentAccount::isActive)
                .sorted(Comparator.comparing(AgentAccount::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        Map<Long, BigDecimal> balances = walletService.balancesByAccount(children.stream().map(AgentAccount::getId).toList());
        return ApiResponse.ok(children.stream().map(c -> new Recipient(c.getId(), c.getAccountCode(), c.getDisplayName(),
                c.getBusinessName(), c.getRole().name(), balances.getOrDefault(c.getId(), BigDecimal.ZERO))).toList());
    }

    public record LimitStatus(String serviceCode, BigDecimal minTxn, BigDecimal maxTxn, BigDecimal dailyAmount, BigDecimal usedToday,
                              BigDecimal monthlyAmount, BigDecimal usedThisMonth, int dailyCount, long countToday) {
    }

    @GetMapping("/wallets/me/limits")
    @Transactional(readOnly = true)
    public ApiResponse<List<LimitStatus>> myLimits() {
        AgentAccount me = currentActor.require();
        List<LimitStatus> result = new ArrayList<>();
        for (String service : List.of("WALLET_TRANSFER", "WALLET_LOAD", "PAYOUT")) {
            limits.effective(me, service).ifPresent(l -> {
                LimitService.Usage u = limits.usage(me.getId(), service);
                result.add(new LimitStatus(service, l.getMinTxn(), l.getMaxTxn(), l.getDailyAmount(), u.dayAmount(),
                        l.getMonthlyAmount(), u.monthAmount(), l.getDailyCount(), u.dayCount()));
            });
        }
        return ApiResponse.ok(result);
    }

    public record TransferBody(@NotNull Long toAccountId,
                               @NotNull @DecimalMin("1.00") @DecimalMax("100000000.00") @Digits(integer = 17, fraction = 2) BigDecimal amount,
                               @Size(max = 300) String remarks) {
    }

    @Operation(summary = "Send money to a direct child. Requires an Idempotency-Key header (UUID).")
    @PostMapping("/wallets/transfer")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TransactionView> transfer(@RequestHeader("Idempotency-Key") @Size(min = 8, max = 80) String idempotencyKey,
                                                 @Valid @RequestBody TransferBody body) {
        AgentAccount me = currentActor.require();
        try {
            FinancialTransaction txn = transfers.transfer(me, body.toAccountId(), body.amount(), body.remarks(), idempotencyKey);
            return ApiResponse.ok(TransactionView.of(txn, accounts), "Transfer successful");
        } catch (BusinessException e) {
            audit.recordIndependent(me.getId(), me.getAccountCode(), "WALLET_TRANSFER", "ACCOUNT", body.toAccountId(),
                    e.code() == ErrorCode.ACCESS_DENIED || e.code() == ErrorCode.OUTSIDE_HIERARCHY ? AuditService.DENIED : AuditService.FAILED,
                    e.code() + ": " + e.getMessage() + " amount=" + body.amount().toPlainString());
            throw e;
        }
    }

    public record LoadBody(@NotNull @DecimalMin("1.00") @Digits(integer = 17, fraction = 2) BigDecimal amount,
                           @NotBlank @Pattern(regexp = "NEFT|RTGS|IMPS|UPI") String mode,
                           @NotBlank @Size(min = 6, max = 40) @Pattern(regexp = "[A-Za-z0-9]+") String utr) {
    }

    @PostMapping("/wallets/load")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TransactionView> load(@RequestHeader("Idempotency-Key") @Size(min = 8, max = 80) String idempotencyKey,
                                             @Valid @RequestBody LoadBody body) {
        FinancialTransaction txn = transfers.requestLoad(currentActor.require(), body.amount(), body.mode(), body.utr(), idempotencyKey);
        return ApiResponse.ok(TransactionView.of(txn, accounts), "Load request submitted");
    }

    // ----- adjustments (maker-checker, Platform Admin) -----

    public record AdjustmentRow(Long id, String requestRef, Long accountId, String accountCode, String direction, BigDecimal amount,
                                String reason, String details, String status, String requestedBy, Long requestedById, String decidedBy,
                                OffsetDateTime createdAt, Long transactionId) {
    }

    @GetMapping("/admin/adjustments")
    @Transactional(readOnly = true)
    public ApiResponse<List<AdjustmentRow>> adjustments() {
        currentActor.requireRole(AccountRole.ADMIN);
        Map<Long, String> codes = accounts.findAll().stream().collect(Collectors.toMap(AgentAccount::getId, AgentAccount::getAccountCode));
        return ApiResponse.ok(adjustmentService.list().stream().map(a -> new AdjustmentRow(a.getId(), a.getRequestRef(), a.getAccountId(),
                codes.get(a.getAccountId()), a.getDirection(), a.getAmount(), a.getReason(), a.getDetails(), a.getStatus(),
                codes.get(a.getRequestedByAccountId()), a.getRequestedByAccountId(), a.getDecidedByAccountId() == null ? null : codes.get(a.getDecidedByAccountId()),
                a.getCreatedAt(), a.getTransactionId())).toList());
    }

    public record AdjustmentBody(@NotNull Long accountId, @NotBlank @Pattern(regexp = "CR|DR") String direction,
                                 @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount,
                                 @NotBlank @Size(max = 80) String reason, @Size(max = 500) String details) {
    }

    @PostMapping("/admin/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Object>> requestAdjustment(@Valid @RequestBody AdjustmentBody body) {
        WalletAdjustment a = adjustmentService.request(currentActor.require(), body.accountId(), body.direction(), body.amount(), body.reason(), body.details());
        return ApiResponse.ok(Map.of("id", a.getId(), "requestRef", a.getRequestRef()), "Sent for approval");
    }

    @PostMapping("/admin/adjustments/{id}/approve")
    public ApiResponse<Map<String, Object>> approveAdjustment(@PathVariable Long id) {
        WalletAdjustment a = adjustmentService.decide(currentActor.require(), id, true);
        return ApiResponse.ok(Map.of("id", a.getId(), "status", a.getStatus()), "Adjustment posted");
    }

    @PostMapping("/admin/adjustments/{id}/reject")
    public ApiResponse<Map<String, Object>> rejectAdjustment(@PathVariable Long id) {
        WalletAdjustment a = adjustmentService.decide(currentActor.require(), id, false);
        return ApiResponse.ok(Map.of("id", a.getId(), "status", a.getStatus()), "Adjustment rejected");
    }

    // ----- helpers -----

    private WalletSummary summary(AgentAccount account) {
        if (account.getRole() == AccountRole.ADMIN) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE, "Platform Admin accounts don't have a wallet.");
        }
        Wallet w = walletService.getOrCreate(account);
        OffsetDateTime monthStart = LocalDate.now(zone).withDayOfMonth(1).atStartOfDay(zone).toOffsetDateTime();
        LedgerEntry beforeMonth = ledger.findFirstByWalletIdAndCreatedAtLessThanOrderByIdDesc(w.getId(), monthStart);
        BigDecimal pending = transactions.findAll((Specification<FinancialTransaction>) (root, q, cb) -> cb.and(
                        cb.equal(root.get("targetAccountId"), account.getId()),
                        cb.equal(root.get("type"), com.primepay.transaction.TxnType.WALLET_LOAD),
                        cb.equal(root.get("status"), com.primepay.transaction.TxnStatus.PENDING)))
                .stream().map(FinancialTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new WalletSummary(w.getId(), w.getWalletCode(), account.getId(), account.getAccountCode(), w.getStatus(),
                w.getAvailableBalance(), w.getLedgerBalance(), w.getBlockedBalance(),
                beforeMonth == null ? BigDecimal.ZERO : beforeMonth.getBalanceAfter(),
                ledger.sumSince(w.getId(), "CR", monthStart), ledger.sumSince(w.getId(), "DR", monthStart),
                w.getTotalCredit(), w.getTotalDebit(), pending, w.getCreatedAt());
    }

    private List<StatementRow> statementRows(Long accountId, LocalDate from, LocalDate to, String direction) {
        AgentAccount me = currentActor.require();
        AgentAccount owner = accountId == null ? me : hierarchy.requireInScope(me, accountId);
        Wallet wallet = walletService.getOrCreate(owner);
        Specification<LedgerEntry> spec = (root, q, cb) -> cb.equal(root.get("walletId"), wallet.getId());
        if (from != null) {
            OffsetDateTime start = from.atStartOfDay(zone).toOffsetDateTime();
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), start));
        }
        if (to != null) {
            OffsetDateTime end = to.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
            spec = spec.and((root, q, cb) -> cb.lessThan(root.get("createdAt"), end));
        }
        if ("CR".equals(direction) || "DR".equals(direction)) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("direction"), direction));
        }
        List<LedgerEntry> rows = ledger.findAll(spec, Sort.by(Sort.Direction.DESC, "id"));
        Map<Long, FinancialTransaction> txns = transactions.findAllById(rows.stream().map(LedgerEntry::getTransactionId).distinct().toList())
                .stream().collect(Collectors.toMap(FinancialTransaction::getId, Function.identity()));
        return rows.stream().limit(500).map(e -> {
            FinancialTransaction t = txns.get(e.getTransactionId());
            return new StatementRow(e.getId(), e.getCreatedAt(), t.getReferenceNo(), t.getId(), t.getType().label(),
                    e.getDescription(), e.getDirection(), e.getAmount(), e.getBalanceAfter());
        }).toList();
    }
}
