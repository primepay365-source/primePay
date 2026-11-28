# PrimePay

A multi-level wallet, distributor-management and commission platform:
**Platform Admin → Super Distributor → Master Distributor → Distributor → Retailer.**

| Part | Stack | Folder |
|---|---|---|
| API | Java 17 · Spring Boot 3.4 · Spring Security (JWT) · JPA/Hibernate · Liquibase · Maven | [`backend/`](backend) |
| Web | React 19 · TypeScript · Vite · MUI 7 · React Router · Axios · React Hook Form + Zod · Vitest | [`frontend/`](frontend) |
| Database | Oracle (19c/21c in production; Oracle 23ai Free in Docker locally). H2 in Oracle mode for the quick `dev` profile only | Liquibase changelogs in `backend/src/main/resources/db/changelog` |
| Design | PrimePay design canvas: 34 screens, clickable | https://claude.ai/artifact/CgxyRh9mYDPs4guodCBzi3 |

**Status:** Phases 1–10 are built and work end to end on the development database. Every design screen is a working screen backed by the API. Open items are listed at the end of this file. Nothing in development moves real money: payouts, bill payments, the payment gateway and bank verification use simulated providers.

## Documentation

- [Architecture](docs/architecture.md): modules, request flow, API conventions, folder structure
- [Oracle database design and ER diagram](docs/database.md)
- [Roles, permissions and hierarchy scope](docs/access-control.md)
- [Financial transaction lifecycles](docs/financial-lifecycles.md)
- [API contracts](docs/api-contracts.md)
- [Roadmap, screen inventory and user journeys](docs/roadmap.md)
- [Security risks and controls](docs/security.md)
- [Reference platform research](docs/reference-research.md) (not done yet)

## Run locally (no Docker)

Requirements: JDK 17, Node.js 20+.

API, using the `dev` profile (in-memory H2 in Oracle mode; data resets on every restart):

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

The log prints a **development-only** Platform Admin login once at startup:
`DEV ONLY: Platform Admin login username='admin@primepay.local' password='…'`.
A new password is generated on each start; set `DEV_ADMIN_PASSWORD` to keep one fixed.

Web (proxies `/api` to `localhost:8080`):

```powershell
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 and sign in. Swagger UI: http://localhost:8080/swagger-ui/index.html.

## Run with Oracle (Docker)

```powershell
Copy-Item .env.example .env   # then set ORACLE_SYS_PASSWORD, ORACLE_APP_PASSWORD, JWT_SECRET_BASE64
docker compose up --build
```

- Oracle Free on `localhost:1521/FREEPDB1` (app schema `primepay`, created by the container)
- API on http://localhost:8080 (profile `oracle`; Liquibase creates the tables)
- Web on http://localhost:8081

To run only Oracle and keep the API in your IDE: `docker compose up oracle`, then start the API with
`SPRING_PROFILES_ACTIVE=oracle` and the `SPRING_DATASOURCE_*` and `JWT_SECRET_BASE64` variables set.

## Tests

```powershell
cd backend;  .\mvnw.cmd test
cd frontend; npm test; npm run typecheck; npm run lint
```

## Development accounts

Each start of the `dev` profile builds a fresh in-memory database with fictional data, created through the real services: a ₹10 lakh wallet load, transfers down the chain (Super → Master → Distributor → Retailer), three verified saved bank accounts, two successful withdrawals with commission, one declined withdrawal with its refund, and a credit card bill payment.

| Login | Role |
|---|---|
| `admin@primepay.local` | Platform Admin |
| `admin2@primepay.local` | Platform Admin (second approver for maker-checker adjustments) |
| `super@primepay.local` | Super Distributor (PP-SD-1001) |
| `master@primepay.local` | Master Distributor (PP-MD-2014) |
| `distributor@primepay.local` | Distributor (PP-DT-3307) |
| `retailer@primepay.local`, `retailer2@primepay.local` | Retailers (PP-RT-48210, PP-RT-48177) |

Passwords are generated on every start and printed once in the API log, in a block headed `DEV ONLY: test logins`. To keep them fixed, set `DEV_ADMIN_PASSWORD` and `DEV_SEED_PASSWORD` before starting.

Handy test values for the retailer flow are listed in the next section. In development, the forgot-password and onboarding OTP codes are shown on screen because there is no SMS provider.

## Who creates whom, and who does business

| Level | Belongs to | Can create and manage | Can see | Does business* |
|---|---|---|---|---|
| Platform Admin | — | Super, Master, Distributor, Retailer (network levels under the level above; a Retailer under any level) | Everything | No |
| Super Distributor | Platform Admin | Master Distributors, Distributors, Retailers anywhere in its network (including directly under itself) | Its whole downstream network, transactions, wallets, reports | Yes |
| Master Distributor | Super Distributor | Distributors under itself; Retailers under itself or its Distributors | Its Distributors and their Retailers | Yes |
| Distributor | Master Distributor | Retailers under itself | Its own Retailers | Yes |
| Retailer | Distributor, Master, Super or Admin | Nobody | Only its own account | Yes |

\*Business = bill payments, add funds and withdraw funds from the account's own main wallet. Money never moves down the chain (there are no transfers to child accounts); each account funds its own wallet with Add funds.

**Commission goes up the chain only, and stops at the account that transacts.** Add funds of ₹1,000: the gateway keeps 2% (₹980 left), then the Platform Admin and each level above the account keep 0.5% (₹5). A Super Distributor is credited ₹975, a Master Distributor ₹970, a Distributor ₹965, a Retailer ₹960. Bill payments and withdrawals pay a share to the account and each level above it. Rates are set by the Platform Admin (Commission rules; gateway charge under Admin configuration → Payment gateway). The API enforces all of this; the screens only hide what a role can't use.

## Retailer business flow (bill payments)

Everything a retailer does runs through one **main wallet**:

- **Add funds** (`/add-funds`): choose a gateway category (E-commerce, Education, Travel; each has its own amount range and charge %) → card type (Visa, Mastercard, RuPay), card holder, card number, amount → payment page (card or UPI) → OTP (card) or UPI approval → status popup. The amount **minus the category charge** is credited. The full card number, expiry and CVV stay in the browser; PrimePay keeps only the network and last 4 digits.
- **Bill payments** (`/bills`): Credit card → card issuing bank → last 4 digits + card-registered mobile (mandatory) → fetch bill (card holder, total and minimum due) → pay → status popup (success, failed or pending). Bill amount + service charge is debited from the main wallet; a failed payment is refunded in full; a pending one is settled with **Recheck** in the history.
- **Withdraw funds** (`/withdraw`): add a bank account (account number, IFSC, holder name) → the bank verifies it → only verified accounts are listed for a fund transfer from the main wallet. Account numbers are stored encrypted (AES-GCM) and shown only as the last 4 digits.
- **Dashboard** shows today's bill payments, funds added and withdrawals plus recent history; **Reports** has Bill payment, Add funds and Withdrawal reports (CSV).
- Admins edit gateway categories under Admin configuration → Payment gateway, and service fees under Services & fees.

Simulated providers (development only, no real money): add funds declines a card ending `0000` or a UPI ID starting with `fail`, and shows the OTP on screen. Bill fetch has no bill for a card ending `9999`; paying `0000` fails (refunded), `1111` is pending then succeeds on recheck, `2222` is pending then fails on recheck. A bank account ending `9999` can't be verified; one ending `0000` verifies but its transfer is declined (refunded).

## QA pack: hierarchy and commission UI run

`qa/` holds a scripted end-to-end UI run (development only, simulated money) and the reports it produces. It drives the real web app in your installed Chrome and records one narrated MP4 of the whole application: user creation at every level, wallet load and transfers down the chain, add funds (card, UPI, declined), bill payments (success, pending → recheck, failed → refund), withdrawals to verified accounts, commission at every level, role restrictions and the admin screens. Narration uses the Windows built-in voice (offline; set `QA_VOICE` to change it). Every check is written with its expected and actual value.

1. Put dev-only passwords in `qa/.env.qa` (git-ignored): `QA_ADMIN_PASSWORD`, `QA_SEED_PASSWORD`, `QA_NEW_USER_PASSWORD`.
2. Start the API with `APP_DEV_BOOTSTRAP_PASSWORD=$QA_ADMIN_PASSWORD` and `APP_DEV_SEED_PASSWORD=$QA_SEED_PASSWORD`, and start the web app.
3. In `qa/`: `npm install`, then `node run-ui-flow.mjs`, `node encode-video.mjs`, `node build-reports.mjs`.

Output goes to `qa/deliverables/`: the MP4, three Excel workbooks (hierarchy, commission distribution, role permission matrix) and the PDF test report. Commission is configured per service and per level (Commission rules); there are no per-user commission overrides.

## Screens

Dashboard (role-specific, live figures and charts) · Users and user detail · Hierarchy explorer · Onboard distributor (5 steps, Master Distributor) · Onboarding approvals · Profile · Change password · Forgot password with OTP · My wallet with statement, balance chart and load request · Transfer funds · Bill payments · Add funds (gateway, OTP) · Withdraw funds with saved bank accounts (Retailer) · Transactions and detail with receipt, ledger lines, commission split, reversal and load approval · Wallet adjustments (maker-checker) · Commission rules with split preview · Commission statement · Limits (role defaults and user overrides) · Reports (13 types, CSV) · Reconciliation · Admin configuration (permission matrix, services and fees, settings) · Notifications · Audit log.

## Security notes

- Secrets come from environment variables only. `.env` is git-ignored, and `.env.example` holds no values.
- Passwords are hashed with BCrypt (strength 12). Tokens are HS256 JWTs signed with `JWT_SECRET_BASE64`.
- The dev admin bootstrap runs only when `app.dev.bootstrap.enabled=true` (the `dev` profile, or local Docker). Never enable it in a shared environment.
- Sign-in locks for 30 minutes after 5 wrong passwords; a password reset unlocks it. Unknown usernames and wrong passwords get the same answer.
- Every money action needs an `Idempotency-Key`; repeating a request returns the original result instead of moving money twice.

## Open items

- **Oracle via Docker is untested.** Docker isn't installed on the build machine. Liquibase changelog `005-platform-core.xml` uses generic types meant for Oracle and H2, but it has only been run on H2.
- **Identity tables:** users still live in `agent_account` / `account_user`, not yet in the `PP_USERS` / closure-table design in docs/database.md. Hierarchy scope is computed by walking the parent chain.
- **Refresh tokens** (rotation, logout revoke) are not built; access tokens last 30 minutes.
- **Exports:** CSV everywhere and a printable receipt; Excel and PDF files are not built yet.
- **Providers:** OTP, SMS/email, KYC, bank verification, payouts, the bill-payment (BBPS) aggregator and the payment gateway use mock adapters; real providers need approved credentials.
- Reference platform research (docs/reference-research.md) has not been done.
