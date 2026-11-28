package com.primepay.user;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.primepay.common.error.BusinessException;
import com.primepay.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService {
    private final AgentAccountRepository accounts;
    private final AccountUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AccountService(AgentAccountRepository accounts, AccountUserRepository users, PasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listVisibleAccounts(Jwt jwt) {
        AgentAccount actor = requireActiveActor(jwt);
        Set<Long> visibleIds = hierarchyIds(actor.getId());
        return accounts.findAllById(visibleIds).stream()
                .map(AccountResponse::from)
                .toList();
    }

    @Transactional
    public AccountResponse createAccount(Jwt jwt, CreateAccountRequest request) {
        return createAccount(requireActiveActor(jwt), request);
    }

    @Transactional
    public AccountResponse createAccount(AgentAccount actor, CreateAccountRequest request) {
        if (!canCreate(actor.getRole(), request.role())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Your role can't create a " + label(request.role()) + ".");
        }
        // A Platform Admin sits at the top of the hierarchy: no parent. Every other level needs one.
        AgentAccount parent = null;
        if (request.role() != AccountRole.ADMIN) {
            if (request.parentId() == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Choose the parent account.");
            }
            parent = accounts.findById(request.parentId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parent account not found"));
            if (actor.getRole() != AccountRole.ADMIN && !hierarchyIds(actor.getId()).contains(parent.getId())) {
                throw new BusinessException(ErrorCode.OUTSIDE_HIERARCHY, "The parent account is outside your network.");
            }
            if (!request.role().allowedParentRoles().contains(parent.getRole())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "A " + label(request.role()) + " must belong to a "
                        + request.role().allowedParentRoles().stream().sorted(java.util.Comparator.reverseOrder()).map(AccountService::label).collect(Collectors.joining(" or ")) + ".");
            }
            if (!parent.isActive()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The parent account is not active");
            }
        }
        if (accounts.existsByAccountCode(request.accountCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account code already exists");
        }
        String username = request.username().trim().toLowerCase(java.util.Locale.ROOT);
        if (users.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Login username already exists");
        }

        AgentAccount created = accounts.save(new AgentAccount(
                request.accountCode().trim(), request.displayName().trim(), request.role(), parent));
        users.save(new AccountUser(username, passwordEncoder.encode(request.initialPassword()), created));
        return AccountResponse.from(created);
    }

    @Transactional
    public AccountResponse updateAccount(Jwt jwt, Long targetId, UpdateAccountRequest request) {
        AgentAccount actor = requireActiveActor(jwt);
        AgentAccount target = accounts.findById(targetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        if (target.getId().equals(actor.getId()) || !hierarchyIds(actor.getId()).contains(target.getId())) {
            throw new AccessDeniedException("You may only manage downstream accounts");
        }
        if (!canManage(actor.getRole(), target.getRole())) {
            throw new AccessDeniedException("Your role cannot manage this account type");
        }

        target.update(request.displayName().trim(), request.active());
        return AccountResponse.from(target);
    }

    private AgentAccount requireActiveActor(Jwt jwt) {
        Object accountIdClaim = jwt.getClaim("account_id");
        if (accountIdClaim == null) {
            throw new AccessDeniedException("Token is missing the account_id claim");
        }

        final Long accountId;
        try {
            accountId = Long.valueOf(accountIdClaim.toString());
        } catch (NumberFormatException exception) {
            throw new AccessDeniedException("Token account_id claim must be a numeric account id");
        }

        AgentAccount actor = accounts.findById(accountId)
                .orElseThrow(() -> new AccessDeniedException("No account is associated with this token"));
        if (!actor.isActive()) {
            throw new AccessDeniedException("Account is inactive");
        }
        return actor;
    }

    private Set<Long> hierarchyIds(Long rootId) {
        Set<Long> hierarchy = new HashSet<>();
        Set<Long> frontier = Set.of(rootId);
        hierarchy.add(rootId);

        while (!frontier.isEmpty()) {
            Set<Long> next = accounts.findByParent_IdIn(frontier).stream()
                    .map(AgentAccount::getId)
                    .filter(hierarchy::add)
                    .collect(Collectors.toSet());
            frontier = next;
        }
        return hierarchy;
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
    private boolean canCreate(AccountRole actorRole, AccountRole targetRole) {
        return switch (actorRole) {
            case ADMIN -> true; // another Platform Admin, or Super, Master, Distributor, Retailer under a parent of the level above
            case SUPER_DISTRIBUTOR -> targetRole == AccountRole.MASTER_DISTRIBUTOR
                    || targetRole == AccountRole.DISTRIBUTOR
                    || targetRole == AccountRole.RETAILER;
            case MASTER_DISTRIBUTOR -> targetRole == AccountRole.DISTRIBUTOR || targetRole == AccountRole.RETAILER;
            case DISTRIBUTOR -> targetRole == AccountRole.RETAILER;
            case RETAILER -> false;
        };
    }

    public static boolean canManage(AccountRole actorRole, AccountRole targetRole) {
        return switch (actorRole) {
            case ADMIN -> targetRole != AccountRole.ADMIN;
            case SUPER_DISTRIBUTOR -> targetRole == AccountRole.MASTER_DISTRIBUTOR
                    || targetRole == AccountRole.DISTRIBUTOR
                    || targetRole == AccountRole.RETAILER;
            case MASTER_DISTRIBUTOR -> targetRole == AccountRole.DISTRIBUTOR || targetRole == AccountRole.RETAILER;
            case DISTRIBUTOR -> targetRole == AccountRole.RETAILER;
            case RETAILER -> false;
        };
    }
}
