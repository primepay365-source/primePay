package com.primepay.hierarchy;

import com.primepay.common.error.BusinessException;
import com.primepay.common.error.ErrorCode;
import com.primepay.user.AccountRole;
import com.primepay.user.AgentAccount;
import com.primepay.user.AgentAccountRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hierarchy scope: who is below whom. Every read and write of another account's data goes through
 * {@link #requireInScope}. Platform Admin's scope is the whole platform.
 */
@Service
public class HierarchyService {
    private final AgentAccountRepository accounts;

    public HierarchyService(AgentAccountRepository accounts) {
        this.accounts = accounts;
    }

    /** The actor plus everyone below them. */
    @Transactional(readOnly = true)
    public Set<Long> scopeIds(AgentAccount actor) {
        if (actor.getRole() == AccountRole.ADMIN) {
            return accounts.findAll().stream().map(AgentAccount::getId).collect(Collectors.toSet());
        }
        return descendantsIncludingSelf(actor.getId());
    }

    @Transactional(readOnly = true)
    public Set<Long> descendantsIncludingSelf(Long rootId) {
        Set<Long> result = new HashSet<>();
        result.add(rootId);
        Set<Long> frontier = Set.of(rootId);
        while (!frontier.isEmpty()) {
            frontier = accounts.findByParent_IdIn(frontier).stream()
                    .map(AgentAccount::getId)
                    .filter(result::add)
                    .collect(Collectors.toSet());
        }
        return result;
    }

    public boolean isInScope(AgentAccount actor, Long targetId) {
        return actor.getRole() == AccountRole.ADMIN || descendantsIncludingSelf(actor.getId()).contains(targetId);
    }

    /** Loads the target account if it is the actor or below them; otherwise 404 so existence doesn't leak. */
    @Transactional(readOnly = true)
    public AgentAccount requireInScope(AgentAccount actor, Long targetId) {
        if (!isInScope(actor, targetId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Account not found.");
        }
        return accounts.findById(targetId).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Account not found."));
    }

    /** Nested tree of the actor's network (Admin: whole platform). */
    @Transactional(readOnly = true)
    public List<HierarchyNode> tree(AgentAccount actor, Map<Long, java.math.BigDecimal> balances) {
        List<AgentAccount> all = accounts.findAllById(scopeIds(actor));
        Map<Long, List<AgentAccount>> byParent = new LinkedHashMap<>();
        for (AgentAccount a : all) {
            Long parentId = a.getParent() == null ? null : a.getParent().getId();
            byParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(a);
        }
        byParent.values().forEach(list -> list.sort((x, y) -> x.getDisplayName().compareToIgnoreCase(y.getDisplayName())));
        AgentAccount root = actor;
        if (actor.getRole() == AccountRole.ADMIN) {
            return byParent.getOrDefault(actor.getId(), List.of()).stream()
                    .map(child -> node(child, byParent, balances))
                    .toList();
        }
        return List.of(node(root, byParent, balances));
    }

    private HierarchyNode node(AgentAccount account, Map<Long, List<AgentAccount>> byParent, Map<Long, java.math.BigDecimal> balances) {
        List<HierarchyNode> children = byParent.getOrDefault(account.getId(), List.of()).stream()
                .map(child -> node(child, byParent, balances))
                .toList();
        return new HierarchyNode(account.getId(), account.getAccountCode(), account.getDisplayName(), account.getBusinessName(),
                account.getRole(), account.getStatus().name(), balances.get(account.getId()), children);
    }

    public record HierarchyNode(Long id, String accountCode, String displayName, String businessName, AccountRole role,
                                String status, java.math.BigDecimal walletBalance, List<HierarchyNode> children) {
    }
}
