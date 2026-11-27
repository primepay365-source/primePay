# Roles, permissions and hierarchy scope

## Hierarchy

```
PLATFORM_ADMIN
└── SUPER_DISTRIBUTOR
    └── MASTER_DISTRIBUTOR
        └── DISTRIBUTOR
            └── RETAILER
```

Every non-admin user has exactly one parent, and the parent's role is fixed by the chain. A Super Distributor may create a Distributor or Retailer deeper in its own tree, but the new user's **direct parent** must still have the role directly above it.

Two checks run on every request, in this order:

1. **Permission:** may this role do this action at all? (`hasPermission`)
2. **Scope:** is the target the caller, or a descendant of the caller? (`PP_USER_HIERARCHY` closure lookup.) Platform Admin's scope is the whole platform.

Out-of-scope reads return 404 so that other users' existence doesn't leak. Out-of-scope writes return 403 `OUTSIDE_HIERARCHY` and are audited.

> **Role name:** the spec says `PLATFORM_ADMIN`; the existing code and table use `ADMIN`. Phase 2 renames it to `PLATFORM_ADMIN` in the new `PP_ROLES` table.

## Permission matrix

✓ = granted, **S** = only within own downstream scope, **O** = own records only, — = never (can't be granted).

| Permission | Admin | Super | Master | Distributor | Retailer |
|---|---|---|---|---|---|
| `USER_CREATE` | ✓ Super only | S Master, Distributor, Retailer | S Distributor | S Retailer | — |
| `USER_VIEW` | ✓ | S | S | S | O |
| `USER_UPDATE` | ✓ | S | S | S | O (profile fields) |
| `USER_STATUS_CHANGE` | ✓ | S | S | S | — |
| `ONBOARD_SUBMIT` | ✓ | S | S | S | — |
| `ONBOARD_APPROVE` | ✓ | — | — | — | — |
| `HIERARCHY_VIEW` | ✓ | S | S | S | — |
| `WALLET_VIEW` | ✓ | S | S | S | O |
| `WALLET_LOAD_REQUEST` | — | O | O | O | — |
| `WALLET_LOAD_APPROVE` | ✓ | — | — | — | — |
| `WALLET_TRANSFER` | — | S direct children | S direct children | S direct children | — |
| `WALLET_ADJUST_REQUEST` / `_APPROVE` | ✓ (maker ≠ checker) | — | — | — | — |
| `TXN_CREATE` (services, e.g. payout) | — | — | — | — | O |
| `TXN_VIEW` | ✓ | S | S | S | O |
| `TXN_REVERSE` | ✓ | — | — | — | — |
| `COMMISSION_CONFIG` | ✓ | — | — | — | — |
| `COMMISSION_VIEW` | ✓ | S | S | S | O |
| `LIMITS_CONFIG` | ✓ | S (≤ own limits) | S (≤ own limits) | — | — |
| `REPORT_VIEW` / `REPORT_EXPORT` | ✓ | S | S | S | O |
| `ADMIN_CONFIG` (roles, services, settings, templates) | ✓ | — | — | — | — |
| `AUDIT_VIEW` | ✓ | — | — | — | — |
| `RECONCILIATION_VIEW` | ✓ | — | — | — | — |

Platform Admin can't hold a wallet or make business transactions (the existing code already enforces this with `AccountRole.canTransact()`).

## Enforcement points

| Layer | How |
|---|---|
| Frontend | The route guard and nav config hide screens the role can't use. This is for convenience only, never for security. |
| Backend, URL | `SecurityFilterChain`: everything under `/api/v1/**` needs a JWT except login, refresh, forgot and reset password. |
| Backend, method | `@PreAuthorize("hasAuthority('PERM_WALLET_TRANSFER')")` on service methods. |
| Backend, data | `HierarchyScopeService.requireInScope(actor, targetUserId)`. Repositories for list endpoints always join on `PP_USER_HIERARCHY` with `ancestor_id = :actor`. |
| Database | The application schema user has no `UPDATE`/`DELETE` on ledger, status history or audit tables. |

## Mandatory scenarios covered by these rules

| # | Scenario | Rule |
|---|---|---|
| 1–4 | Each level creates the level below | `USER_CREATE` and the parent-role chain |
| 5 | Retailer sees only its own dashboard | Scope O on every report query |
| 6 | Super sees only permitted downstream users | Scope S via closure table |
| 7 | Distributor can't access another Distributor's Retailer | 404 on read, 403 and audit on write |
| 17 | Inactive account can't do financial operations | Status check in the JWT filter and again in the transaction service |
