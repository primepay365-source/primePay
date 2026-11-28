package com.primepay.user;

import com.primepay.common.ContactNormalizer;

import com.primepay.audit.AuditService;
import com.primepay.common.error.BusinessException;
import com.primepay.common.error.ErrorCode;
import com.primepay.hierarchy.HierarchyService;
import com.primepay.notification.NotificationService;
import com.primepay.wallet.WalletService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** User management: scoped list, details, create, status changes with history, and own profile. */
@Service
public class UserService {
    private static final Set<AccountStatus> SETTABLE = EnumSet.of(AccountStatus.ACTIVE, AccountStatus.SUSPENDED, AccountStatus.INACTIVE);

    private final AgentAccountRepository accounts;
    private final AccountUserRepository users;
    private final UserStatusHistoryRepository statusHistory;
    private final HierarchyService hierarchy;
    private final WalletService wallets;
    private final AccountService accountService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final NotificationService notifications;
    private final ApplicationEventPublisher events;

    public UserService(AgentAccountRepository accounts, AccountUserRepository users, UserStatusHistoryRepository statusHistory,
                       HierarchyService hierarchy, WalletService wallets, AccountService accountService,
                       PasswordEncoder passwordEncoder, AuditService audit, NotificationService notifications,
                       ApplicationEventPublisher events) {
        this.accounts = accounts;
        this.users = users;
        this.statusHistory = statusHistory;
        this.hierarchy = hierarchy;
        this.wallets = wallets;
        this.accountService = accountService;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.notifications = notifications;
        this.events = events;
    }

    public record UserRow(Long id, String accountCode, String username, String displayName, String businessName, AccountRole role,
                          Long parentId, String parentCode, AccountStatus status, String email, String mobile, String address,
                          BigDecimal walletBalance, OffsetDateTime createdAt, OffsetDateTime lastLoginAt) {
    }

    public record StatusChange(AccountStatus fromStatus, AccountStatus toStatus, String reason, String changedBy, OffsetDateTime changedAt) {
    }

    public record RoleCount(AccountRole role, long count) {
    }

    @Transactional(readOnly = true)
    public List<UserRow> list(AgentAccount actor, AccountRole role, AccountStatus status, String q) {
        Set<Long> scope = hierarchy.scopeIds(actor);
        scope.remove(actor.getId());
        List<AgentAccount> found = accounts.findAllById(scope).stream()
                .filter(a -> role == null || a.getRole() == role)
                .filter(a -> status == null || a.getStatus() == status)
                .filter(a -> matches(a, q))
                .sorted(Comparator.comparing(AgentAccount::getCreatedAt).reversed())
                .toList();
        return toRows(found);
    }

    @Transactional(readOnly = true)
    public Map<AccountRole, Long> countByRole(AgentAccount actor) {
        Set<Long> scope = hierarchy.scopeIds(actor);
        scope.remove(actor.getId());
        return accounts.findAllById(scope).stream().collect(Collectors.groupingBy(AgentAccount::getRole, Collectors.counting()));
    }

    @Transactional(readOnly = true)
    public UserRow get(AgentAccount actor, Long id) {
        return toRows(List.of(hierarchy.requireInScope(actor, id))).get(0);
    }

    @Transactional(readOnly = true)
    public List<StatusChange> history(AgentAccount actor, Long id) {
        hierarchy.requireInScope(actor, id);
        Map<Long, String> codes = accounts.findAll().stream().collect(Collectors.toMap(AgentAccount::getId, AgentAccount::getAccountCode));
        return statusHistory.findByAccountIdOrderByIdDesc(id).stream()
                .map(h -> new StatusChange(h.getFromStatus(), h.getToStatus(), h.getReason(),
                        h.getChangedByAccountId() == null ? "system" : codes.getOrDefault(h.getChangedByAccountId(), "?"), h.getChangedAt()))
                .toList();
    }

    /** Creates the user and emails them their sign-in details (role, username, password) once the account is saved. */
    @Transactional
    public UserRow create(AgentAccount actor, CreateAccountRequest request, String email, String mobile, String businessName, String address) {
        return create(actor, request, email, mobile, businessName, address, true);
    }

    @Transactional
    public UserRow create(AgentAccount actor, CreateAccountRequest request, String email, String mobile, String businessName, String address,
                          boolean sendWelcomeEmail) {
        AccountResponse created = accountService.createAccount(actor, request);
        AgentAccount account = accounts.findById(created.id()).orElseThrow();
        assignContacts(account, email, mobile);
        account.setBusinessName(blankToNull(businessName));
        account.setAddress(blankToNull(address));
        if (account.getRole().canTransact()) {
            wallets.getOrCreate(account); // Platform Admins have no wallet
        }
        statusHistory.save(new UserStatusHistory(account.getId(), null, AccountStatus.ACTIVE, "Created by " + actor.getAccountCode(), actor.getId()));
        audit.record(actor, "USER_CREATE", "USER", account.getAccountCode(), "role=" + account.getRole() + " parent=" + request.parentId());
        notifications.notify(account.getId(), NotificationService.ACCOUNT, "Welcome to PrimePay",
                "Your " + account.getRole().name().replace('_', ' ').toLowerCase(Locale.ROOT) + " account " + account.getAccountCode() + " is active.", "/profile");
        if (sendWelcomeEmail && account.getEmail() != null) {
            // Delivered after the transaction commits (WelcomeMailService), so a mail problem never undoes the creation.
            events.publishEvent(new UserCreatedEvent(account.getId(), account.getAccountCode(), account.getDisplayName(), account.getRole(),
                    account.getEmail(), account.getMobile(), request.username().trim().toLowerCase(Locale.ROOT), request.initialPassword()));
        }
        return toRows(List.of(account)).get(0);
    }

    @Transactional
    public UserRow changeStatus(AgentAccount actor, Long id, AccountStatus newStatus, String reason) {
        if (!SETTABLE.contains(newStatus)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Status can be set to ACTIVE, SUSPENDED or INACTIVE.");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Give a reason; it is saved to the audit log.");
        }
        AgentAccount target = hierarchy.requireInScope(actor, id);
        if (target.getId().equals(actor.getId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "You can't change your own status.");
        }
        if (!AccountService.canManage(actor.getRole(), target.getRole())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Your role can't manage this account type.");
        }
        AccountStatus previous = target.getStatus();
        if (previous == newStatus) {
            return toRows(List.of(target)).get(0);
        }
        if (previous == AccountStatus.PENDING_APPROVAL || previous == AccountStatus.REJECTED) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "Use the approvals screen for applications.");
        }
        target.changeStatus(newStatus);
        statusHistory.save(new UserStatusHistory(target.getId(), previous, newStatus, reason, actor.getId()));
        audit.record(actor, "USER_STATUS_CHANGE", "USER", target.getAccountCode(), previous + " -> " + newStatus + ": " + reason);
        notifications.notify(target.getId(), NotificationService.ACCOUNT, "Account " + newStatus.name().toLowerCase(Locale.ROOT),
                actor.getAccountCode() + " changed your account to " + newStatus.name().toLowerCase(Locale.ROOT) + ": " + reason, "/profile");
        if (target.getParent() != null && !target.getParent().getId().equals(actor.getId())) {
            notifications.notify(target.getParent().getId(), NotificationService.ACCOUNT, "Account " + newStatus.name().toLowerCase(Locale.ROOT),
                    actor.getAccountCode() + " changed " + target.getAccountCode() + " to " + newStatus.name().toLowerCase(Locale.ROOT) + ".", "/users/" + target.getId());
        }
        return toRows(List.of(target)).get(0);
    }

    @Transactional
    public UserRow updateProfile(AgentAccount actor, String displayName, String businessName, String email, String address) {
        AgentAccount me = accounts.findById(actor.getId()).orElseThrow();
        if (displayName != null && !displayName.isBlank()) me.setDisplayName(displayName.trim());
        me.setBusinessName(blankToNull(businessName));
        assignContacts(me, email, me.getMobile());
        me.setAddress(blankToNull(address));
        audit.record(me, "PROFILE_UPDATE", "USER", me.getAccountCode(), null);
        return toRows(List.of(me)).get(0);
    }

    /**
     * Email and mobile are sign-in identifiers: stored normalised (lower-case email, +91XXXXXXXXXX), at least one is
     * required, and each may belong to one account only.
     */
    public void assignContacts(AgentAccount account, String rawEmail, String rawMobile) {
        String email = ContactNormalizer.emailOrNull(rawEmail);
        String mobile = ContactNormalizer.mobileOrNull(rawMobile);
        if (email == null && mobile == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Enter an email address or a mobile number; one of them is used to sign in.");
        }
        Long id = account.getId() == null ? -1L : account.getId();
        if (email != null && accounts.existsByEmailAndIdNot(email, id)) {
            throw new BusinessException(ErrorCode.DUPLICATE, "This email address is already used by another account.");
        }
        if (mobile != null && accounts.existsByMobileAndIdNot(mobile, id)) {
            throw new BusinessException(ErrorCode.DUPLICATE, "This mobile number is already used by another account.");
        }
        account.setEmail(email);
        account.setMobile(mobile);
    }

    /** Used by dev seeding and onboarding to keep status history complete. */
    @Transactional
    public void recordStatus(Long accountId, AccountStatus from, AccountStatus to, String reason, Long by) {
        statusHistory.save(new UserStatusHistory(accountId, from, to, reason, by));
    }

    private List<UserRow> toRows(List<AgentAccount> list) {
        Map<Long, BigDecimal> balances = wallets.balancesByAccount(list.stream().map(AgentAccount::getId).toList());
        Map<Long, AccountUser> logins = users.findAll().stream().collect(Collectors.toMap(u -> u.getAccount().getId(), u -> u, (a, b) -> a));
        return list.stream().map(a -> {
            AccountUser login = logins.get(a.getId());
            return new UserRow(a.getId(), a.getAccountCode(), login == null ? null : login.getUsername(), a.getDisplayName(),
                    a.getBusinessName(), a.getRole(), a.getParent() == null ? null : a.getParent().getId(),
                    a.getParent() == null ? null : a.getParent().getAccountCode(), a.getStatus(), a.getEmail(), a.getMobile(),
                    a.getAddress(), balances.get(a.getId()), a.getCreatedAt(), login == null ? null : login.getLastLoginAt());
        }).toList();
    }

    private static boolean matches(AgentAccount a, String q) {
        if (q == null || q.isBlank()) return true;
        String needle = q.trim().toLowerCase(Locale.ROOT);
        return contains(a.getAccountCode(), needle) || contains(a.getDisplayName(), needle) || contains(a.getBusinessName(), needle)
                || contains(a.getEmail(), needle) || contains(a.getMobile(), needle);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    PasswordEncoder passwordEncoder() {
        return passwordEncoder;
    }
}
