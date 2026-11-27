# API contracts (v1)

Conventions (envelope, errors, status codes, idempotency) are in [architecture.md](architecture.md#api-conventions). Swagger UI: `/swagger-ui/index.html`. Paging parameters: `page` (0-based), `size` (max 100), `sort=field,asc|desc`.

Status: ✅ available now · 🔁 exists, being reshaped · ⏳ planned (phase number).

## Authentication

| Method | Path | Auth | Status | Notes |
|---|---|---|---|---|
| POST | `/auth/login` | public | ✅ | `{username, password}` → access token, expiry, account |
| GET | `/auth/me` | any | ✅ (Phase 1) | Current user, role and permissions |
| POST | `/auth/refresh` | refresh token | ⏳ 3 | Rotates the refresh token; reuse revokes the whole token family |
| POST | `/auth/logout` | any | ⏳ 3 | Revokes the refresh token family |
| POST | `/auth/forgot-password` | public | ⏳ 3 | Always returns 202, whether or not the user exists |
| POST | `/auth/reset-password` | public | ⏳ 3 | `{username, otp, newPassword}` |
| POST | `/auth/change-password` | any | ⏳ 3 | |

## Users and hierarchy

| Method | Path | Permission | Status |
|---|---|---|---|
| GET | `/accounts` | USER_VIEW | 🔁 → `/users` in Phase 4 |
| POST | `/accounts` | USER_CREATE | 🔁 → `/users` |
| PUT | `/accounts/{id}` | USER_UPDATE | 🔁 → `/users/{id}` |
| POST | `/users` · GET `/users` · GET/PUT `/users/{id}` · PATCH `/users/{id}/status` | USER_* | ⏳ 4 |
| GET | `/hierarchy`, `/hierarchy/me`, `/hierarchy/children`, `/hierarchy/{userId}/downstream` | HIERARCHY_VIEW | ⏳ 2 |
| POST | `/onboarding/distributors/otp/request`, `/otp/verify`, `/onboarding/distributors` | ONBOARD_SUBMIT | 🔁 → 5-step draft API in Phase 4 |

## Wallet and transactions

| Method | Path | Permission | Status |
|---|---|---|---|
| GET | `/wallets/me` · `/wallets/{userId}` | WALLET_VIEW | ⏳ 6 |
| POST | `/wallets/load` | WALLET_LOAD_REQUEST | ⏳ 6 |
| POST | `/wallets/transfer` | WALLET_TRANSFER (`Idempotency-Key` required) | ⏳ 6 |
| GET | `/wallets/transactions` · `/wallets/statement` | WALLET_VIEW | ⏳ 6 |
| POST | `/transactions` | TXN_CREATE (`Idempotency-Key`) | ⏳ 7 |
| GET | `/transactions` · `/transactions/{id}` | TXN_VIEW | ⏳ 7 |
| POST | `/transactions/{id}/reverse` | TXN_REVERSE | ⏳ 7 |

## Commission, limits, reports, admin

| Method | Path | Permission | Status |
|---|---|---|---|
| GET | `/commissions` · `/commissions/statement` | COMMISSION_VIEW | ⏳ 8 |
| POST | `/commissions/config` | COMMISSION_CONFIG | ⏳ 8 |
| GET | `/reports/dashboard` · `/reports/wallet` · `/reports/transactions` · `/reports/commission` · `/reports/business` | REPORT_VIEW | ⏳ 5, 10 |
| GET | `/admin/roles` · `/admin/permissions` · `/admin/velocity-config` · `/admin/commission-config` | ADMIN_CONFIG | ⏳ 3, 8, 9 |

## Example: `GET /api/v1/auth/me`

```json
{
  "success": true,
  "data": {
    "accountId": 1,
    "accountCode": "LOCAL-ADMIN-001",
    "username": "admin",
    "displayName": "Development Admin",
    "role": "ADMIN",
    "parentId": null,
    "active": true,
    "canTransact": false
  },
  "message": null,
  "timestamp": "2026-11-26T10:42:18.120Z",
  "requestId": "3f2c9a1e-…"
}
```

> **Phase 1 compatibility:** the endpoints that already exist (`/auth/login`, `/accounts`, `/onboarding/*`) keep their current response bodies, so nothing that calls them breaks. They move to the envelope when they are reshaped in Phases 3 and 4. Error responses from **all** endpoints use the new error envelope from Phase 1.
