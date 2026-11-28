# PrimePay

A multi-level wallet, distributor-management and commission platform:
**Platform Admin → Super Distributor → Master Distributor → Distributor → Retailer.**

| Part | Stack | Folder |
|---|---|---|
| API | Java 17 · Spring Boot 3.4 · Spring Security (JWT) · JPA/Hibernate · Liquibase · Maven | [`backend/`](backend) |
| Web | React 19 · TypeScript · Vite · MUI 7 · React Router · Axios · React Hook Form + Zod · Vitest | [`frontend/`](frontend) |
| Database | Oracle Database only (tested on Oracle AI Database 26ai Free; native BOOLEAN needs 23ai or later) | Liquibase: one XML change set per file in `backend/src/main/resources/db/changelog/changes` |
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

## Database: Oracle

Oracle Database is the only database (no H2). Every table and data change is plain Oracle SQL in its own `.sql` script; one Liquibase changelog runs them in order:

`db.changelog-master.xml` (change set `NNN`) → `scripts/NNN-name.sql` → Oracle

```
backend/src/main/resources/db/changelog/
├── db.changelog-master.xml                 # one change set per script: id 001…021, <sqlFile> + <rollback><sqlFile>
└── scripts/                                # the actual Oracle SQL (CREATE TABLE, indexes, seed rows)
    ├── 001-create-agent-account-table.sql
    ├── 002-create-account-user-table.sql
    ├── …
    ├── 021-create-beneficiaries-table.sql
    └── rollback/                           # one rollback script per change (DROP / DELETE)
        └── 001-create-agent-account-table-rollback.sql …
```

Rules:
- SQL lives only in `scripts/`; the changelog only declares each change set (`id` = the script number) and points at the script and its rollback script.
- Change set IDs run `001`, `002`, … in dependency order (tables before the tables that reference them).
- Liquibase alone runs the scripts (on application start, or `liquibase update` / `rollback`) and records them in `DATABASECHANGELOG` / `DATABASECHANGELOGLOCK`. Never run a script by hand.
- To change the schema, add `scripts/022-….sql` (and its rollback script) and a change set with `id="022"` at the end of the changelog. Never edit a script or change set that has already run on a shared database: Liquibase checks checksums.

## Logs

The API writes to the console and to rolling files in `logs/` (next to where it starts; set `LOG_DIR` to change it):

| File | Contents | Kept |
|---|---|---|
| `primepay-api.log` | everything at INFO and above | new file daily or at 20 MB, 30 days, 1 GB max, old files gzipped in `logs/archive/` |
| `primepay-api-error.log` | warnings and errors only | 90 days |

Each line carries the request ID (also returned to clients as `X-Request-Id`). `PRIMEPAY_LOG_LEVEL=DEBUG` makes the app's own logs more detailed. Tests log to the console only. Configuration: `backend/src/main/resources/logback-spring.xml`.

Local setup used on the development PC (Oracle AI Database 26ai Free, pluggable database `FREEPDB1`):

| Schema | Used by |
|---|---|
| `PRIMEPAY` | the application (`dev` and `prod` profiles) |
| `PRIMEPAY_TEST` | the automated tests (rebuilt from the change sets on every test run) |

Create them once as SYSDBA (replace the passwords):

```sql
ALTER SESSION SET CONTAINER = FREEPDB1;
CREATE USER PRIMEPAY IDENTIFIED BY "<password>" DEFAULT TABLESPACE USERS QUOTA UNLIMITED ON USERS;
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE VIEW, CREATE PROCEDURE, CREATE TRIGGER TO PRIMEPAY;
CREATE USER PRIMEPAY_TEST IDENTIFIED BY "<password>" DEFAULT TABLESPACE USERS QUOTA UNLIMITED ON USERS;
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE VIEW, CREATE PROCEDURE, CREATE TRIGGER TO PRIMEPAY_TEST;
```

## Configuration (`.env`)

Secrets live in environment variables or in a git-ignored `.env` file in the project root (the API reads it automatically). Never commit it.

| Variable | Purpose |
|---|---|
| `SPRING_DATASOURCE_URL` | e.g. `jdbc:oracle:thin:@//192.168.1.15:1521/FREEPDB1` |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | application schema (`PRIMEPAY`) |
| `TEST_DATASOURCE_USERNAME` / `TEST_DATASOURCE_PASSWORD` | test schema (`PRIMEPAY_TEST`) |
| `JWT_SECRET_BASE64` | token signing key (Base64 of at least 32 random bytes) |
| `FIELD_ENCRYPTION_KEY_BASE64` | AES key for bank account numbers (Base64 of 32 random bytes) |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | SMTP server for emailed sign-in codes (Gmail app password, Outlook, Zoho, SES SMTP …) |
| `MSG91_AUTH_KEY`, `MSG91_TEMPLATE_ID` | MSG91 SMS for mobile sign-in codes; the DLT-approved template must contain `##otp##` |
| `APP_DEV_BOOTSTRAP_PASSWORD`, `APP_DEV_SEED_PASSWORD` | `dev` profile only: fixed passwords for the demo accounts |

Profiles: `dev` (default; demo accounts; when a channel has no provider the code is written to the API log) and `prod` (`SPRING_PROFILES_ACTIVE=prod`: no demo accounts, every secret and provider required, codes are never logged).

## Sign-in

1. **Email or mobile number + password.** The mobile can be typed as `98765 43210`, `+91 98765 43210` or `09876543210`.
2. **One-time code** sent to that email (SMTP) or by SMS to that mobile (MSG91). The dashboard opens only after the code is verified.

API: `POST /api/v1/auth/login` `{identifier, password}` → `{challengeId, channel, destination, …}` (no token) · `POST /api/v1/auth/login/verify` `{challengeId, otp}` → access token · `POST /api/v1/auth/login/resend` `{challengeId}`.

Until SMTP/MSG91 credentials are added, the `dev` profile writes each code to the API log (`DEV ONLY - no EMAIL provider configured. sign-in code for …`). In `prod` sign-in fails clearly instead of skipping delivery.

## Run locally

Requirements: JDK 17, Node.js 20+, Oracle Database with the two schemas above, and `.env`.

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

```powershell
cd frontend
npm install
npm run dev
```

Open http://localhost:5173 (the web app proxies `/api` to `localhost:8080`). Swagger UI: http://localhost:8080/swagger-ui/index.html.

## Tests

```powershell
cd backend;  .\mvnw.cmd clean test
cd frontend; npm test; npm run typecheck; npm run lint
```

Backend tests run against Oracle in the `PRIMEPAY_TEST` schema, which Liquibase drops and rebuilds for each test context; a test-only sender captures the sign-in codes.

## Development accounts

The `dev` profile creates fictional demo accounts on first start (kept in Oracle across restarts), through the real services: a wallet load, add-funds top-ups, verified saved bank accounts, withdrawals and a credit card bill payment.

| Email | Role |
|---|---|
| `admin@primepay.local` | Platform Admin |
| `admin2@primepay.local` | Platform Admin (second approver for maker-checker adjustments) |
| `super@primepay.local` | Super Distributor (PP-SD-1001) |
| `master@primepay.local` | Master Distributor (PP-MD-2014) |
| `distributor@primepay.local` | Distributor (PP-DT-3307) |
| `retailer@primepay.local`, `retailer2@primepay.local` | Retailers (PP-RT-48210, PP-RT-48177) |

Passwords come from `APP_DEV_BOOTSTRAP_PASSWORD` (admin) and `APP_DEV_SEED_PASSWORD` (the others) in `.env`; if they are empty, passwords are generated and printed once in the API log. Each demo account also has a fictional mobile number (see its profile) for trying mobile sign-in.

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
- Sign-in is two steps: email or mobile + password, then a one-time code sent to that email or mobile. Codes are BCrypt-hashed, expire in 5 minutes, allow 5 attempts and 3 resends (60 s apart), and work once; at most 5 sign-ins per account per 15 minutes reach the code step. Sign-in locks for 30 minutes after 5 wrong passwords; a password reset unlocks it. Unknown accounts and wrong passwords get the same answer.
- Every money action needs an `Idempotency-Key`; repeating a request returns the original result instead of moving money twice.

## Open items

- **OTP providers need credentials.** SMTP and MSG91 are integrated but have no credentials yet, so in `dev` sign-in codes go to the API log. Add the `MAIL_*` and `MSG91_*` values to `.env` to send real emails and SMS.
- **Hosting needs an Oracle database.** The single-image `Dockerfile` and `render.yaml` expect `SPRING_DATASOURCE_*` for a reachable Oracle Database; free hosts like Render don't provide one.
- **Identity tables:** users still live in `agent_account` / `account_user`, not yet in the `PP_USERS` / closure-table design in docs/database.md. Hierarchy scope is computed by walking the parent chain.
- **Refresh tokens** (rotation, logout revoke) are not built; access tokens last 30 minutes.
- **Exports:** CSV everywhere and a printable receipt; Excel and PDF files are not built yet.
- **Providers:** KYC, bank verification, payouts, the bill-payment (BBPS) aggregator and the payment gateway use mock adapters; real providers need approved credentials. Sign-in OTP email (SMTP) and SMS (MSG91) are real integrations.
- Reference platform research (docs/reference-research.md) has not been done.
