package com.primepay.reports;

import com.primepay.audit.AuditLog;
import com.primepay.audit.AuditService;
import com.primepay.commission.CommissionStats;
import com.primepay.hierarchy.HierarchyService;
import com.primepay.ledger.LedgerEntry;
import com.primepay.ledger.LedgerEntryRepository;
import com.primepay.transaction.FinancialTransaction;
import com.primepay.transaction.FinancialTransactionRepository;
import com.primepay.transaction.TransactionView;
import com.primepay.transaction.TxnStatus;
import com.primepay.transaction.TxnType;
import com.primepay.user.AccountRole;
import com.primepay.user.AccountStatus;
import com.primepay.user.AgentAccount;
import com.primepay.user.AgentAccountRepository;
import com.primepay.wallet.Wallet;
import com.primepay.wallet.WalletRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dashboards, reports and reconciliation computed from Oracle data and always limited to the caller's hierarchy. */
@Service
public class ReportService {
    private static final Set<TxnType> BUSINESS = EnumSet.of(TxnType.PAYOUT, TxnType.BILL_PAYMENT, TxnType.ADD_FUNDS, TxnType.WALLET_TRANSFER, TxnType.WALLET_LOAD);

    private final AgentAccountRepository accounts;
    private final FinancialTransactionRepository transactions;
    private final WalletRepository wallets;
    private final LedgerEntryRepository ledger;
    private final HierarchyService hierarchy;
    private final CommissionStats commission;
    private final AuditService audit;
    private final ZoneId zone;

    public ReportService(AgentAccountRepository accounts, FinancialTransactionRepository transactions, WalletRepository wallets,
                         LedgerEntryRepository ledger, HierarchyService hierarchy, CommissionStats commission, AuditService audit,
                         @Value("${app.business-timezone:Asia/Kolkata}") String zone) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.wallets = wallets;
        this.ledger = ledger;
        this.hierarchy = hierarchy;
        this.commission = commission;
        this.audit = audit;
        this.zone = ZoneId.of(zone);
    }

    // ------------------------------------------------------------------ dashboard

    public record Kpi(String label, BigDecimal value, String format, String hint, String tone) {
    }

    public record SeriesPoint(LocalDate date, long success, long failed, BigDecimal amount) {
    }

    public record BreakdownRow(String label, BigDecimal value, String format) {
    }

    public record Dashboard(String role, String scope, List<Kpi> kpis, List<SeriesPoint> series, String breakdownTitle,
                            List<BreakdownRow> breakdown, List<TransactionView> recent, BigDecimal commissionTotal,
                            List<BreakdownRow> commissionLines, List<BreakdownRow> network) {
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(AgentAccount actor) {
        LocalDate today = LocalDate.now(zone);
        OffsetDateTime dayStart = start(today), dayEnd = start(today.plusDays(1));
        OffsetDateTime monthStart = start(today.withDayOfMonth(1));
        OffsetDateTime seriesStart = start(today.minusDays(13));
        Map<Long, AgentAccount> byId = accounts.findAll().stream().collect(Collectors.toMap(AgentAccount::getId, Function.identity()));
        Set<Long> scope = actor.getRole() == AccountRole.RETAILER ? Set.of(actor.getId()) : hierarchy.scopeIds(actor);

        List<FinancialTransaction> recentWindow = inScope(actor, scope, seriesStart, dayEnd);
        List<FinancialTransaction> month = inScope(actor, scope, monthStart, dayEnd);
        List<FinancialTransaction> todayTx = recentWindow.stream().filter(t -> !t.getCreatedAt().isBefore(dayStart)).toList();
        List<SeriesPoint> series = series(recentWindow, today);
        List<TransactionView> recent = recentWindow.stream().filter(t -> t.getType() != TxnType.COMMISSION)
                .sorted(Comparator.comparing(FinancialTransaction::getId).reversed()).limit(actor.getRole() == AccountRole.RETAILER ? 15 : 6)
                .map(t -> TransactionView.of(t, byId)).toList();

        List<Kpi> kpis = new ArrayList<>();
        String breakdownTitle;
        List<BreakdownRow> breakdown = new ArrayList<>();
        BigDecimal commissionTotal;
        List<BreakdownRow> commissionLines = new ArrayList<>();

        if (actor.getRole() == AccountRole.ADMIN) {
            BigDecimal allBalances = wallets.findAll().stream().map(Wallet::getLedgerBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
            List<AgentAccount> users = byId.values().stream().filter(a -> a.getRole() != AccountRole.ADMIN).toList();
            long active = users.stream().filter(AgentAccount::isActive).count();
            kpis.add(new Kpi("Wallet balances (all users)", allBalances, "money", wallets.count() + " wallets", "dark"));
            kpis.add(new Kpi("Business volume today", volume(todayTx), "money", count(todayTx, TxnStatus.SUCCESS) + " successful transactions", null));
            kpis.add(new Kpi("Success / failed / pending today", null, "text",
                    count(todayTx, TxnStatus.SUCCESS) + " / " + count(todayTx, TxnStatus.FAILED) + " / " + count(todayTx, TxnStatus.PENDING), null));
            kpis.add(new Kpi("Active accounts", BigDecimal.valueOf(active), "count", (users.size() - active) + " inactive, suspended or pending", null));
            breakdownTitle = "Users by hierarchy level";
            for (AccountRole role : List.of(AccountRole.SUPER_DISTRIBUTOR, AccountRole.MASTER_DISTRIBUTOR, AccountRole.DISTRIBUTOR, AccountRole.RETAILER)) {
                breakdown.add(new BreakdownRow(label(role), BigDecimal.valueOf(users.stream().filter(u -> u.getRole() == role).count()), "count"));
            }
            CommissionStats.Totals totals = commission.platformTotals(monthStart);
            commissionTotal = totals.paid().add(totals.platform());
            commissionLines.add(new BreakdownRow("Paid to the network", totals.paid(), "money"));
            commissionLines.add(new BreakdownRow("Platform share", totals.platform(), "money"));
            commissionLines.add(new BreakdownRow("Reversed", totals.reversed(), "money"));
        } else {
            Wallet wallet = wallets.findByAccountId(actor.getId()).orElse(null);
            BigDecimal available = wallet == null ? BigDecimal.ZERO : wallet.getAvailableBalance();
            BigDecimal ledgerBalance = wallet == null ? BigDecimal.ZERO : wallet.getLedgerBalance();
            if (actor.getRole() == AccountRole.RETAILER) {
                long failed = count(todayTx, TxnStatus.FAILED);
                kpis.add(new Kpi("Main wallet", available, "money", "Available for bill payments and withdrawals", "dark"));
                List<FinancialTransaction> billsToday = ofType(todayTx, TxnType.BILL_PAYMENT);
                kpis.add(new Kpi("Bill payments today", volume(billsToday), "money", billsToday.size() + " payments · " + count(billsToday, TxnStatus.SUCCESS) + " successful", null));
                List<FinancialTransaction> addedToday = ofType(todayTx, TxnType.ADD_FUNDS);
                kpis.add(new Kpi("Funds added today", volume(addedToday), "money", addedToday.size() + " attempts · " + count(addedToday, TxnStatus.SUCCESS) + " successful", null));
                List<FinancialTransaction> withdrawnToday = ofType(todayTx, TxnType.PAYOUT);
                kpis.add(new Kpi("Withdrawals today", volume(withdrawnToday), "money", withdrawnToday.size() + " transfers · " + count(withdrawnToday, TxnStatus.SUCCESS) + " successful", null));
                kpis.add(new Kpi("Failed today", BigDecimal.valueOf(failed), "count", failed == 0 ? "None" : "No money was lost", failed > 0 ? "danger" : null));
                breakdownTitle = "This month by service";
                breakdown.add(new BreakdownRow("Bill payments", volume(ofType(month, TxnType.BILL_PAYMENT)), "money"));
                breakdown.add(new BreakdownRow("Funds added", volume(ofType(month, TxnType.ADD_FUNDS)), "money"));
                breakdown.add(new BreakdownRow("Withdrawals", volume(ofType(month, TxnType.PAYOUT)), "money"));
                if (wallet != null) {
                    breakdown.add(new BreakdownRow("Credits to wallet", ledger.sumSince(wallet.getId(), "CR", monthStart), "money"));
                    breakdown.add(new BreakdownRow("Debits from wallet", ledger.sumSince(wallet.getId(), "DR", monthStart), "money"));
                }
            } else {
                kpis.add(new Kpi("My wallet · available", available, "money", "Ledger " + ledgerBalance.toPlainString(), "dark"));
                kpis.add(new Kpi("Business today", volume(todayTx), "money", count(todayTx, TxnStatus.SUCCESS) + " successful transactions", null));
                kpis.add(new Kpi("Business this month", volume(month), "money", count(month, TxnStatus.SUCCESS) + " successful transactions", null));
                Map<AccountRole, Long> network = scope.stream().filter(id -> !id.equals(actor.getId())).map(byId::get)
                        .collect(Collectors.groupingBy(AgentAccount::getRole, Collectors.counting()));
                kpis.add(new Kpi("Network", BigDecimal.valueOf(scope.size() - 1L), "count", networkHint(network), null));
                breakdownTitle = "Direct " + (actor.getRole() == AccountRole.DISTRIBUTOR ? "retailers" : "network") + " · business this month";
                for (AgentAccount child : byId.values().stream().filter(a -> a.getParent() != null && a.getParent().getId().equals(actor.getId())).toList()) {
                    Set<Long> sub = hierarchy.descendantsIncludingSelf(child.getId());
                    BigDecimal v = month.stream().filter(t -> isBusiness(t) && touches(t, sub)).map(FinancialTransaction::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    breakdown.add(new BreakdownRow(child.getDisplayName() + " · " + child.getAccountCode(), v, "money"));
                }
                breakdown.sort(Comparator.comparing(BreakdownRow::value).reversed());
                if (breakdown.size() > 6) breakdown = new ArrayList<>(breakdown.subList(0, 6));
            }
            CommissionStats.Totals mine = commission.earnerTotals(actor.getId(), monthStart);
            commissionTotal = mine.paid().add(mine.reversed());
            commissionLines.add(new BreakdownRow("Credited", mine.paid(), "money"));
            commissionLines.add(new BreakdownRow("Reversed", mine.reversed(), "money"));
            commissionLines.add(new BreakdownRow("Transactions earning commission", BigDecimal.valueOf(mine.count()), "count"));
        }
        return new Dashboard(actor.getRole().name(), actor.getRole() == AccountRole.ADMIN ? "Whole platform"
                : actor.getRole() == AccountRole.RETAILER ? "Your account" : "Your network",
                kpis, series, breakdownTitle, breakdown, recent, commissionTotal, commissionLines, networkCounts(actor, scope, byId));
    }

    // ------------------------------------------------------------------ reports

    public record Column(String key, String label, boolean numeric) {
    }

    public record Report(String type, String title, String description, List<Kpi> summary, List<Column> columns, List<Map<String, Object>> rows) {
    }

    @Transactional(readOnly = true)
    public Report report(AgentAccount actor, String type, LocalDate from, LocalDate to, String q) {
        LocalDate end = to == null ? LocalDate.now(zone) : to;
        LocalDate begin = from == null ? end.withDayOfMonth(1) : from;
        Map<Long, AgentAccount> byId = accounts.findAll().stream().collect(Collectors.toMap(AgentAccount::getId, Function.identity()));
        Set<Long> scope = actor.getRole() == AccountRole.RETAILER ? Set.of(actor.getId()) : hierarchy.scopeIds(actor);
        String needle = q == null ? "" : q.trim().toLowerCase();
        return switch (type) {
            case "wallet-load" -> txnReport(actor, scope, begin, end, needle, byId, "wallet-load", "Wallet load report",
                    "Every wallet load request and how it was settled.", t -> t.getType() == TxnType.WALLET_LOAD);
            case "wallet-transfer" -> txnReport(actor, scope, begin, end, needle, byId, "wallet-transfer", "Wallet transfer report",
                    "Transfers between wallets in your network.", t -> t.getType() == TxnType.WALLET_TRANSFER);
            case "transactions" -> txnReport(actor, scope, begin, end, needle, byId, "transactions", "Transaction report",
                    "All transaction types with status and amounts.", t -> true);
            case "bill-payment" -> txnReport(actor, scope, begin, end, needle, byId, "bill-payment", "Bill payment report",
                    "Credit card bill payments with biller, card, amount, charge and status.", t -> t.getType() == TxnType.BILL_PAYMENT);
            case "add-funds" -> txnReport(actor, scope, begin, end, needle, byId, "add-funds", "Add funds report",
                    "Gateway top-ups to the main wallet with category, card or UPI, charge and status.", t -> t.getType() == TxnType.ADD_FUNDS);
            case "payout" -> txnReport(actor, scope, begin, end, needle, byId, "payout", "Withdrawal report",
                    "Withdrawals to saved bank accounts with status and bank reference.", t -> t.getType() == TxnType.PAYOUT);
            case "failed" -> txnReport(actor, scope, begin, end, needle, byId, "failed", "Failed transaction report",
                    "Failed transactions and why they failed.", t -> t.getStatus() == TxnStatus.FAILED);
            case "commission" -> commissionReport(actor, begin, end, byId);
            case "retailer" -> userReport(actor, scope, byId, AccountRole.RETAILER, begin, end, needle);
            case "distributor" -> userReport(actor, scope, byId, AccountRole.DISTRIBUTOR, begin, end, needle);
            case "master" -> userReport(actor, scope, byId, AccountRole.MASTER_DISTRIBUTOR, begin, end, needle);
            case "super" -> userReport(actor, scope, byId, AccountRole.SUPER_DISTRIBUTOR, begin, end, needle);
            case "business" -> businessReport(actor, scope, begin, end);
            case "balances" -> balanceReport(actor, scope, byId, needle);
            case "audit" -> auditReport(actor, needle);
            default -> throw new com.primepay.common.error.BusinessException(com.primepay.common.error.ErrorCode.NOT_FOUND, "Unknown report " + type + ".");
        };
    }

    private Report txnReport(AgentAccount actor, Set<Long> scope, LocalDate begin, LocalDate end, String needle, Map<Long, AgentAccount> byId,
                             String type, String title, String description, Predicate<FinancialTransaction> filter) {
        List<FinancialTransaction> list = inScope(actor, scope, start(begin), start(end.plusDays(1))).stream()
                .filter(t -> t.getType() != TxnType.COMMISSION || "transactions".equals(type))
                .filter(filter)
                .filter(t -> needle.isEmpty() || t.getReferenceNo().toLowerCase().contains(needle)
                        || code(byId, t.getInitiatorAccountId()).toLowerCase().contains(needle))
                .sorted(Comparator.comparing(FinancialTransaction::getId).reversed())
                .toList();
        List<Map<String, Object>> rows = list.stream().limit(2000).map(t -> {
            TransactionView v = TransactionView.of(t, byId);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("reference", v.referenceNo());
            row.put("date", v.createdAt());
            row.put("type", v.typeLabel());
            row.put("from", v.fromCode());
            row.put("to", v.toCode());
            row.put("amount", v.amount());
            row.put("charge", v.fee());
            row.put("status", v.status());
            row.put("note", v.failureReason() != null ? v.failureReason() : v.remarks());
            row.put("_id", v.id());
            return row;
        }).toList();
        long ok = count(list, TxnStatus.SUCCESS);
        List<Kpi> summary = List.of(
                new Kpi("Records", BigDecimal.valueOf(list.size()), "count", null, null),
                new Kpi("Total amount", list.stream().map(FinancialTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add), "money", null, null),
                new Kpi("Successful", BigDecimal.valueOf(ok), "count", list.isEmpty() ? null : pct(ok, list.size()) + "%", null),
                new Kpi("Pending", BigDecimal.valueOf(count(list, TxnStatus.PENDING)), "count", null, null));
        return new Report(type, title, description, summary, List.of(new Column("reference", "Reference", false), new Column("date", "Date", false),
                new Column("type", "Type", false), new Column("from", "From", false), new Column("to", "To", false),
                new Column("amount", "Amount (₹)", true), new Column("charge", "Charge (₹)", true), new Column("status", "Status", false), new Column("note", "Note", false)), rows);
    }

    private Report commissionReport(AgentAccount actor, LocalDate begin, LocalDate end, Map<Long, AgentAccount> byId) {
        List<CommissionStats.Row> list = commission.rows(actor.getRole() == AccountRole.ADMIN ? null : hierarchy.scopeIds(actor),
                start(begin), start(end.plusDays(1)));
        List<Map<String, Object>> rows = list.stream().map(r -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("reference", r.transactionRef());
            row.put("date", r.createdAt());
            row.put("earner", r.earnerAccountId() == null ? "Platform" : code(byId, r.earnerAccountId()));
            row.put("level", r.levelRole());
            row.put("base", r.baseAmount());
            row.put("commission", r.amount());
            row.put("status", r.status());
            row.put("_id", r.transactionId());
            return row;
        }).toList();
        BigDecimal paid = list.stream().filter(r -> "CREDITED".equals(r.status())).map(CommissionStats.Row::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal platform = list.stream().filter(r -> "PLATFORM".equals(r.status())).map(CommissionStats.Row::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal reversed = list.stream().filter(r -> "REVERSED".equals(r.status())).map(CommissionStats.Row::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Report("commission", "Commission report", "Commission per transaction and hierarchy level.",
                List.of(new Kpi("Paid to network", paid, "money", null, null), new Kpi("Platform share", platform, "money", null, null),
                        new Kpi("Reversed", reversed, "money", null, null), new Kpi("Entries", BigDecimal.valueOf(list.size()), "count", null, null)),
                List.of(new Column("reference", "Transaction", false), new Column("date", "Date", false), new Column("earner", "Earner", false),
                        new Column("level", "Level", false), new Column("base", "Txn amount (₹)", true), new Column("commission", "Commission (₹)", true),
                        new Column("status", "Status", false)), rows);
    }

    private Report userReport(AgentAccount actor, Set<Long> scope, Map<Long, AgentAccount> byId, AccountRole role, LocalDate begin, LocalDate end, String needle) {
        List<AgentAccount> list = scope.stream().map(byId::get).filter(a -> a.getRole() == role)
                .filter(a -> needle.isEmpty() || a.getAccountCode().toLowerCase().contains(needle) || a.getDisplayName().toLowerCase().contains(needle))
                .sorted(Comparator.comparing(AgentAccount::getAccountCode)).toList();
        List<FinancialTransaction> period = inScope(actor, scope, start(begin), start(end.plusDays(1)));
        Map<Long, BigDecimal> balances = new HashMap<>();
        wallets.findByAccountIdIn(list.stream().map(AgentAccount::getId).toList()).forEach(w -> balances.put(w.getAccountId(), w.getAvailableBalance()));
        List<Map<String, Object>> rows = list.stream().map(a -> {
            Set<Long> sub = hierarchy.descendantsIncludingSelf(a.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", a.getAccountCode());
            row.put("name", a.getDisplayName());
            row.put("parent", a.getParent() == null ? null : a.getParent().getAccountCode());
            row.put("status", a.getStatus());
            row.put("network", sub.size() - 1);
            row.put("wallet", balances.getOrDefault(a.getId(), BigDecimal.ZERO));
            row.put("business", period.stream().filter(t -> isBusiness(t) && touches(t, sub)).map(FinancialTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
            row.put("_id", a.getId());
            return row;
        }).toList();
        return new Report(role.name().toLowerCase(), label(role) + " report", label(role) + "s in your network with status, balance and business.",
                List.of(new Kpi("Accounts", BigDecimal.valueOf(list.size()), "count", null, null),
                        new Kpi("Active", BigDecimal.valueOf(list.stream().filter(AgentAccount::isActive).count()), "count", null, null),
                        new Kpi("Suspended", BigDecimal.valueOf(list.stream().filter(a -> a.getStatus() == AccountStatus.SUSPENDED).count()), "count", null, null),
                        new Kpi("Wallet balances", balances.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add), "money", null, null)),
                List.of(new Column("userId", "User ID", false), new Column("name", "Name", false), new Column("parent", "Parent", false),
                        new Column("status", "Status", false), new Column("network", "Accounts below", true), new Column("wallet", "Wallet (₹)", true),
                        new Column("business", "Business in period (₹)", true)), rows);
    }

    private Report businessReport(AgentAccount actor, Set<Long> scope, LocalDate begin, LocalDate end) {
        List<FinancialTransaction> list = inScope(actor, scope, start(begin), start(end.plusDays(1)));
        Map<LocalDate, List<FinancialTransaction>> byDay = list.stream()
                .collect(Collectors.groupingBy(t -> t.getCreatedAt().atZoneSameInstant(zone).toLocalDate()));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (LocalDate d = end; !d.isBefore(begin); d = d.minusDays(1)) {
            List<FinancialTransaction> day = byDay.getOrDefault(d, List.of());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", d);
            row.put("count", day.stream().filter(ReportService::isBusiness).count());
            row.put("volume", volume(day));
            row.put("successRate", day.isEmpty() ? null : pct(count(day, TxnStatus.SUCCESS) + count(day, TxnStatus.REVERSED), day.size()) + "%");
            row.put("failed", count(day, TxnStatus.FAILED));
            row.put("commission", day.stream().filter(t -> t.getType() == TxnType.COMMISSION && t.getStatus() == TxnStatus.SUCCESS)
                    .map(FinancialTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
            rows.add(row);
        }
        return new Report("business", "Daily business report", "Volume, success rate and commission per day. The total row covers the whole period.",
                List.of(new Kpi("Volume", volume(list), "money", null, null),
                        new Kpi("Transactions", BigDecimal.valueOf(list.stream().filter(ReportService::isBusiness).count()), "count", null, null),
                        new Kpi("Success rate", null, "text", list.isEmpty() ? "–" : pct(count(list, TxnStatus.SUCCESS) + count(list, TxnStatus.REVERSED), list.size()) + "%", null),
                        new Kpi("Failed", BigDecimal.valueOf(count(list, TxnStatus.FAILED)), "count", null, null)),
                List.of(new Column("date", "Date", false), new Column("count", "Transactions", true), new Column("volume", "Volume (₹)", true),
                        new Column("successRate", "Success rate", true), new Column("failed", "Failed", true), new Column("commission", "Commission (₹)", true)), rows);
    }

    private Report balanceReport(AgentAccount actor, Set<Long> scope, Map<Long, AgentAccount> byId, String needle) {
        List<Wallet> list = wallets.findByAccountIdIn(scope).stream()
                .filter(w -> needle.isEmpty() || w.getWalletCode().toLowerCase().contains(needle))
                .sorted(Comparator.comparing(Wallet::getWalletCode)).toList();
        List<Map<String, Object>> rows = list.stream().map(w -> {
            BigDecimal net = ledger.netForWallet(w.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("wallet", w.getWalletCode());
            row.put("user", code(byId, w.getAccountId()));
            row.put("role", label(byId.get(w.getAccountId()).getRole()));
            row.put("available", w.getAvailableBalance());
            row.put("blocked", w.getBlockedBalance());
            row.put("ledger", w.getLedgerBalance());
            row.put("reconciled", net.compareTo(w.getLedgerBalance()) == 0 ? "Match" : "Mismatch " + net.subtract(w.getLedgerBalance()).toPlainString());
            row.put("_id", w.getAccountId());
            return row;
        }).toList();
        return new Report("balances", "Wallet balance report", "Current balances checked against the sum of each wallet's ledger.",
                List.of(new Kpi("Wallets", BigDecimal.valueOf(list.size()), "count", null, null),
                        new Kpi("Total available", list.stream().map(Wallet::getAvailableBalance).reduce(BigDecimal.ZERO, BigDecimal::add), "money", null, null),
                        new Kpi("Blocked", list.stream().map(Wallet::getBlockedBalance).reduce(BigDecimal.ZERO, BigDecimal::add), "money", null, null),
                        new Kpi("Mismatches", BigDecimal.valueOf(rows.stream().filter(r -> !"Match".equals(r.get("reconciled"))).count()), "count", null, null)),
                List.of(new Column("wallet", "Wallet", false), new Column("user", "User", false), new Column("role", "Role", false),
                        new Column("available", "Available (₹)", true), new Column("blocked", "Blocked (₹)", true), new Column("ledger", "Ledger (₹)", true),
                        new Column("reconciled", "Ledger check", false)), rows);
    }

    private Report auditReport(AgentAccount actor, String needle) {
        if (actor.getRole() != AccountRole.ADMIN) {
            throw new com.primepay.common.error.BusinessException(com.primepay.common.error.ErrorCode.ACCESS_DENIED, "Only Platform Admin can view the audit report.");
        }
        List<AuditLog> list = audit.search(needle, null, PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "occurredAt"))).getContent();
        List<Map<String, Object>> rows = list.stream().map(a -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("time", a.getOccurredAt());
            row.put("actor", a.getActorLabel());
            row.put("action", a.getAction());
            row.put("target", a.getTargetId());
            row.put("ip", a.getIpAddress());
            row.put("result", a.getResult());
            return row;
        }).toList();
        return new Report("audit", "Audit report", "Who did what, when and from where.",
                List.of(new Kpi("Events", BigDecimal.valueOf(list.size()), "count", null, null),
                        new Kpi("Money actions", BigDecimal.valueOf(list.stream().filter(a -> a.getAction().startsWith("WALLET") || a.getAction().startsWith("PAYOUT") || a.getAction().startsWith("TRANSACTION")).count()), "count", null, null),
                        new Kpi("Denied", BigDecimal.valueOf(list.stream().filter(a -> AuditService.DENIED.equals(a.getResult())).count()), "count", null, null),
                        new Kpi("Failed", BigDecimal.valueOf(list.stream().filter(a -> AuditService.FAILED.equals(a.getResult())).count()), "count", null, null)),
                List.of(new Column("time", "Time", false), new Column("actor", "Actor", false), new Column("action", "Action", false),
                        new Column("target", "Target", false), new Column("ip", "IP", false), new Column("result", "Result", false)), rows);
    }

    // ------------------------------------------------------------------ reconciliation

    public record Check(String title, boolean passed, String detail) {
    }

    public record Exception(String reference, Long transactionId, String issue, BigDecimal amount, OffsetDateTime createdAt) {
    }

    public record Reconciliation(OffsetDateTime ranAt, BigDecimal walletTotal, BigDecimal ledgerTotal, boolean matched,
                                 List<Check> checks, List<Exception> exceptions) {
    }

    @Transactional(readOnly = true)
    public Reconciliation reconcile() {
        List<Wallet> all = wallets.findAll();
        BigDecimal walletTotal = BigDecimal.ZERO, ledgerTotal = BigDecimal.ZERO;
        int mismatched = 0, negative = 0;
        List<Exception> exceptions = new ArrayList<>();
        for (Wallet w : all) {
            BigDecimal net = ledger.netForWallet(w.getId());
            walletTotal = walletTotal.add(w.getLedgerBalance());
            ledgerTotal = ledgerTotal.add(net);
            if (net.compareTo(w.getLedgerBalance()) != 0) {
                mismatched++;
                exceptions.add(new Exception(w.getWalletCode(), null, "Wallet balance differs from its ledger by " + net.subtract(w.getLedgerBalance()).toPlainString(),
                        w.getLedgerBalance(), w.getCreatedAt()));
            }
            if (w.getAvailableBalance().signum() < 0) negative++;
        }
        List<FinancialTransaction> transfers = transactions.findAll().stream().filter(t -> t.getType() == TxnType.WALLET_TRANSFER).toList();
        int unbalanced = 0;
        for (FinancialTransaction t : transfers) {
            BigDecimal dr = BigDecimal.ZERO, cr = BigDecimal.ZERO;
            for (LedgerEntry e : ledger.findByTransactionIdOrderByIdAsc(t.getId())) {
                if ("FEE".equals(e.getEntryType())) continue;
                if ("DR".equals(e.getDirection())) dr = dr.add(e.getAmount()); else cr = cr.add(e.getAmount());
            }
            if (dr.compareTo(cr) != 0) {
                unbalanced++;
                exceptions.add(new Exception(t.getReferenceNo(), t.getId(), "Debits " + dr.toPlainString() + " ≠ credits " + cr.toPlainString(), t.getAmount(), t.getCreatedAt()));
            }
        }
        OffsetDateTime stale = OffsetDateTime.now().minusHours(1);
        List<FinancialTransaction> pending = transactions.findAll().stream()
                .filter(t -> t.getStatus() == TxnStatus.PENDING && t.getCreatedAt().isBefore(stale)).toList();
        pending.forEach(t -> exceptions.add(new Exception(t.getReferenceNo(), t.getId(),
                t.getType().label() + " pending for more than an hour", t.getAmount(), t.getCreatedAt())));
        BigDecimal commissionLedger = ledger.netOfType("COMMISSION").subtract(ledger.netOfType("COMMISSION_REVERSAL").negate());
        BigDecimal commissionEntries = commission.creditedNet();
        boolean commissionOk = commissionLedger.compareTo(commissionEntries) == 0;
        if (!commissionOk) {
            exceptions.add(new Exception("COMMISSION", null, "Commission ledger " + commissionLedger.toPlainString() + " ≠ commission records " + commissionEntries.toPlainString(), commissionLedger, OffsetDateTime.now()));
        }
        List<Check> checks = List.of(
                new Check("Wallet balances equal ledger totals", mismatched == 0, all.size() + " wallets · " + mismatched + " differ"),
                new Check("Every transfer has matching debits and credits", unbalanced == 0, transfers.size() + " transfers · " + unbalanced + " unbalanced"),
                new Check("No wallet below zero", negative == 0, "Negative balances: " + negative),
                new Check("No transaction stuck in pending", pending.isEmpty(), pending.size() + " pending for over an hour"),
                new Check("Commission credits match commission records", commissionOk, "Ledger " + commissionLedger.toPlainString() + " · records " + commissionEntries.toPlainString()));
        return new Reconciliation(OffsetDateTime.now(), walletTotal, ledgerTotal, exceptions.isEmpty(), checks, exceptions);
    }

    // ------------------------------------------------------------------ helpers

    private List<FinancialTransaction> inScope(AgentAccount actor, Set<Long> scope, OffsetDateTime from, OffsetDateTime to) {
        if (actor.getRole() == AccountRole.ADMIN) {
            return transactions.findAll((root, q, cb) -> cb.and(cb.greaterThanOrEqualTo(root.get("createdAt"), from), cb.lessThan(root.get("createdAt"), to)));
        }
        return transactions.findInScopeBetween(scope, from, to);
    }

    private List<SeriesPoint> series(List<FinancialTransaction> window, LocalDate today) {
        Map<LocalDate, List<FinancialTransaction>> byDay = window.stream().filter(ReportService::isBusiness)
                .collect(Collectors.groupingBy(t -> t.getCreatedAt().atZoneSameInstant(zone).toLocalDate()));
        List<SeriesPoint> points = new ArrayList<>();
        for (LocalDate d = today.minusDays(13); !d.isAfter(today); d = d.plusDays(1)) {
            List<FinancialTransaction> day = byDay.getOrDefault(d, List.of());
            points.add(new SeriesPoint(d, count(day, TxnStatus.SUCCESS) + count(day, TxnStatus.REVERSED), count(day, TxnStatus.FAILED), volume(day)));
        }
        return points;
    }

    private static boolean isBusiness(FinancialTransaction t) {
        return BUSINESS.contains(t.getType());
    }


    /** Accounts below the actor, by level (all accounts for the Platform Admin). */
    private List<BreakdownRow> networkCounts(AgentAccount actor, Set<Long> scope, Map<Long, AgentAccount> byId) {
        List<BreakdownRow> rows = new ArrayList<>();
        List<AgentAccount> below = scope.stream().filter(id -> !id.equals(actor.getId())).map(byId::get)
                .filter(a -> a != null && a.getRole() != AccountRole.ADMIN).toList();
        if (actor.getRole() == AccountRole.RETAILER) {
            return rows;
        }
        rows.add(new BreakdownRow("Total users", BigDecimal.valueOf(below.size()), "count"));
        for (AccountRole role : List.of(AccountRole.SUPER_DISTRIBUTOR, AccountRole.MASTER_DISTRIBUTOR, AccountRole.DISTRIBUTOR, AccountRole.RETAILER)) {
            if (role.ordinal() > actor.getRole().ordinal()) {
                rows.add(new BreakdownRow(label(role), BigDecimal.valueOf(below.stream().filter(a -> a.getRole() == role).count()), "count"));
            }
        }
        return rows;
    }
    private static boolean touches(FinancialTransaction t, Set<Long> ids) {
        return ids.contains(t.getInitiatorAccountId()) || (t.getSourceAccountId() != null && ids.contains(t.getSourceAccountId()))
                || (t.getTargetAccountId() != null && ids.contains(t.getTargetAccountId()));
    }

    private static BigDecimal volume(List<FinancialTransaction> list) {
        return list.stream().filter(t -> isBusiness(t) && (t.getStatus() == TxnStatus.SUCCESS || t.getStatus() == TxnStatus.REVERSED))
                .map(FinancialTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static List<FinancialTransaction> ofType(List<FinancialTransaction> list, TxnType type) {
        return list.stream().filter(t -> t.getType() == type).toList();
    }

    private static long count(List<FinancialTransaction> list, TxnStatus status) {
        return list.stream().filter(t -> isBusiness(t) && t.getStatus() == status).count();
    }

    private static String pct(long part, long whole) {
        return whole == 0 ? "0" : BigDecimal.valueOf(part * 100.0 / whole).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private OffsetDateTime start(LocalDate day) {
        return day.atStartOfDay(zone).toOffsetDateTime();
    }

    private static String code(Map<Long, AgentAccount> byId, Long id) {
        AgentAccount a = id == null ? null : byId.get(id);
        return a == null ? "" : a.getAccountCode();
    }

    static String label(AccountRole role) {
        return switch (role) {
            case ADMIN -> "Platform Admin";
            case SUPER_DISTRIBUTOR -> "Super Distributor";
            case MASTER_DISTRIBUTOR -> "Master Distributor";
            case DISTRIBUTOR -> "Distributor";
            case RETAILER -> "Retailer";
        };
    }

    private static String networkHint(Map<AccountRole, Long> network) {
        List<String> parts = new ArrayList<>();
        if (network.containsKey(AccountRole.MASTER_DISTRIBUTOR)) parts.add(network.get(AccountRole.MASTER_DISTRIBUTOR) + " Master");
        if (network.containsKey(AccountRole.DISTRIBUTOR)) parts.add(network.get(AccountRole.DISTRIBUTOR) + " Distributor");
        if (network.containsKey(AccountRole.RETAILER)) parts.add(network.get(AccountRole.RETAILER) + " Retailer");
        return parts.isEmpty() ? "No accounts below you yet" : String.join(" · ", parts);
    }
}
