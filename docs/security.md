# Security risks and controls

| # | Risk | Where | Control | Phase |
|---|---|---|---|---|
| 1 | Reading or changing another user's data (IDOR) across the hierarchy | Every `{id}` endpoint | Scope check against the closure table in the service layer; 404 for reads outside scope; scenario tests 5–7 | 2, 4 |
| 2 | Double debit from retries or double clicks | Transfer, payout | `Idempotency-Key` stored with a request hash, unique per user | 6, 7 |
| 3 | Race conditions that overdraw a wallet or slip past a limit | Wallet, velocity | `SELECT … FOR UPDATE` in a fixed lock order, version column, usage counters updated in the same transaction | 6, 9 |
| 4 | Balance changed without a trace | Wallet | Balances only change through `LedgerService`; ledger insert-only, enforced by DB grants and a trigger; daily reconciliation | 6, 10 |
| 5 | Floating-point money errors | Everywhere | `BigDecimal` and `NUMBER(19,2)`; amounts are strings in JSON; no `double` for money (enforced in review) | 1 onward |
| 6 | Stolen or replayed tokens | Auth | 15-minute access JWT (HS256 now, RS256 before production); refresh token rotation with reuse detection; logout revokes; JWT secret from env, at least 32 bytes | 3 |
| 7 | Password guessing | Login | BCrypt (strength 12); lockout after 5 failures for 30 min; rate limit per IP and username; same error message for unknown user and wrong password | 1 (BCrypt), 3 |
| 8 | OTP leakage or brute force | OTP | 6 digits, hashed at rest, 5-minute expiry, 5 attempts, resend cooldown; never logged; dev mock provider shows the code only under the `dev` profile | 3, 4 |
| 9 | Sensitive data exposure (Aadhaar, PAN, bank account) | KYC, bank | Encrypted columns, last-4 for display, masked in API/logs/exports; no full values in audit JSON | 4 |
| 10 | SQL injection | Repositories | JPA parameter binding only; no string-built SQL; report filters whitelisted | 1 onward |
| 11 | XSS | Frontend | React escaping; no `dangerouslySetInnerHTML`; strict CSP header in production; templates escape variables | 1, 12 |
| 12 | CSRF | API | Stateless bearer tokens in the `Authorization` header (not cookies), so CSRF protection is off; if refresh moves to an HttpOnly cookie, it gets `SameSite=Strict` plus a CSRF token | 3 |
| 13 | CORS misconfiguration | API | Explicit origin allow-list from config; no wildcard with credentials | 1 |
| 14 | Privilege escalation through role assignment | User create, admin | Role is derived from the allowed child role, never trusted from input; only admin edits role permissions; all changes audited | 2, 3 |
| 15 | Self-approval of money movements | Adjustments, loads | Maker-checker: approver ≠ requester, enforced in service and by a DB `CHECK` | 7 |
| 16 | Audit trail tampering | Audit | Append-only table; app user has `INSERT` only; admins can read, not edit | 3 |
| 17 | Secrets in source control | Repo | `.env` ignored; `.env.example` only; dev password generated at startup, not committed | 1 |
| 18 | Simulated money mistaken for real | UI, receipts | "Simulated" labels from mock providers; real providers only with approved credentials and verified webhook signatures | 7 |
| 19 | Real third-party personal data in demo data | Repo | Removed the copied names, phones and emails from `src/data.js` in Phase 1; all seed data is fictional | 1 |
| 20 | Dependency vulnerabilities | Build | Pinned versions; `npm audit` and OWASP dependency-check in CI | 12 |

## Phase 1 findings in the existing code

- `DevAdminBootstrap` defaulted to the password `Admin@123456`. Phase 1 replaces it with a random password generated at startup and logged once under `dev` only.
- The onboarding OTP service returns the OTP to the client in development. That's acceptable only because it's gated by `app.onboarding.otp.dev-mode`. Phase 4 moves it behind the mock SMS adapter, and the OTP is stored hashed.
- `src/data.js` contained names, phone numbers, emails and addresses copied from another company's portal screenshots. It's removed with the old frontend.
