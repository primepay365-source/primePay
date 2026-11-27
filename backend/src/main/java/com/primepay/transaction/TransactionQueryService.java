package com.primepay.transaction;

import com.primepay.common.error.BusinessException;
import com.primepay.common.error.ErrorCode;
import com.primepay.hierarchy.HierarchyService;
import com.primepay.user.AccountRole;
import com.primepay.user.AgentAccount;
import com.primepay.user.AgentAccountRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Hierarchy-scoped transaction search. Retailers see only their own; others see their network's. */
@Service
public class TransactionQueryService {
    private final FinancialTransactionRepository transactions;
    private final AgentAccountRepository accounts;
    private final HierarchyService hierarchy;
    private final ZoneId zone;

    public TransactionQueryService(FinancialTransactionRepository transactions, AgentAccountRepository accounts,
                                   HierarchyService hierarchy, @Value("${app.business-timezone:Asia/Kolkata}") String zone) {
        this.transactions = transactions;
        this.accounts = accounts;
        this.hierarchy = hierarchy;
        this.zone = ZoneId.of(zone);
    }

    public record Filter(String q, TxnType type, TxnStatus status, LocalDate from, LocalDate to, Long accountId) {
    }

    @Transactional(readOnly = true)
    public Page<TransactionView> search(AgentAccount actor, Filter filter, int page, int size) {
        Page<FinancialTransaction> result = transactions.findAll(spec(actor, filter, true),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200), Sort.by(Sort.Direction.DESC, "id")));
        Map<Long, AgentAccount> byId = accountMap();
        return result.map(t -> TransactionView.of(t, byId));
    }

    @Transactional(readOnly = true)
    public Map<TxnStatus, Long> countsByStatus(AgentAccount actor, Filter filter) {
        Map<TxnStatus, Long> counts = new EnumMap<>(TxnStatus.class);
        for (TxnStatus s : TxnStatus.values()) counts.put(s, 0L);
        transactions.findAll(spec(actor, filter, false)).forEach(t -> counts.merge(t.getStatus(), 1L, Long::sum));
        return counts;
    }

    @Transactional(readOnly = true)
    public FinancialTransaction requireVisible(AgentAccount actor, Long id) {
        FinancialTransaction t = transactions.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Transaction not found."));
        if (actor.getRole() == AccountRole.ADMIN) return t;
        Set<Long> scope = visibleAccounts(actor);
        boolean visible = scope.contains(t.getInitiatorAccountId())
                || (t.getSourceAccountId() != null && scope.contains(t.getSourceAccountId()))
                || (t.getTargetAccountId() != null && scope.contains(t.getTargetAccountId()));
        if (!visible) throw new BusinessException(ErrorCode.NOT_FOUND, "Transaction not found.");
        return t;
    }

    @Transactional(readOnly = true)
    public List<FinancialTransaction> childrenOf(Long id) {
        return transactions.findByOriginalTxnId(id);
    }

    public Map<Long, AgentAccount> accountMap() {
        return accounts.findAll().stream().collect(Collectors.toMap(AgentAccount::getId, Function.identity()));
    }

    private Set<Long> visibleAccounts(AgentAccount actor) {
        return actor.getRole() == AccountRole.RETAILER ? Set.of(actor.getId()) : hierarchy.scopeIds(actor);
    }

    private Specification<FinancialTransaction> spec(AgentAccount actor, Filter f, boolean includeStatus) {
        Specification<FinancialTransaction> spec = (root, q, cb) -> cb.conjunction();
        if (actor.getRole() != AccountRole.ADMIN) {
            List<Long> ids = List.copyOf(visibleAccounts(actor));
            spec = spec.and((root, q, cb) -> cb.or(root.get("initiatorAccountId").in(ids), root.get("sourceAccountId").in(ids),
                    root.get("targetAccountId").in(ids)));
        }
        if (f.accountId() != null) {
            if (!hierarchy.isInScope(actor, f.accountId())) throw new BusinessException(ErrorCode.NOT_FOUND, "Account not found.");
            Long id = f.accountId();
            spec = spec.and((root, q, cb) -> cb.or(cb.equal(root.get("initiatorAccountId"), id), cb.equal(root.get("sourceAccountId"), id),
                    cb.equal(root.get("targetAccountId"), id)));
        }
        if (f.type() != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("type"), f.type()));
        if (includeStatus && f.status() != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), f.status()));
        if (f.from() != null) {
            var start = f.from().atStartOfDay(zone).toOffsetDateTime();
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), start));
        }
        if (f.to() != null) {
            var end = f.to().plusDays(1).atStartOfDay(zone).toOffsetDateTime();
            spec = spec.and((root, q, cb) -> cb.lessThan(root.get("createdAt"), end));
        }
        if (f.q() != null && !f.q().isBlank()) {
            String like = "%" + f.q().trim().toUpperCase(Locale.ROOT) + "%";
            List<Long> matching = accounts.findAll().stream()
                    .filter(a -> a.getAccountCode().toUpperCase(Locale.ROOT).contains(f.q().trim().toUpperCase(Locale.ROOT)))
                    .map(AgentAccount::getId).toList();
            spec = spec.and((root, q, cb) -> {
                var byRef = cb.or(cb.like(cb.upper(root.get("referenceNo")), like), cb.like(cb.upper(root.get("externalRef")), like));
                if (matching.isEmpty()) return byRef;
                return cb.or(byRef, root.get("initiatorAccountId").in(matching), root.get("sourceAccountId").in(matching),
                        root.get("targetAccountId").in(matching));
            });
        }
        return spec;
    }
}
