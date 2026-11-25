# PrimePay Java API

Spring Boot 3 / Java 17 REST API with role-aware account hierarchy management. The React/Vite UI now uses the API for sign-in and hierarchy management. Local development defaults to an in-memory H2 database in Oracle compatibility mode; Oracle remains available for non-dev deployments. Transaction, wallet, and report screens remain demo/static UI until their API data contracts are defined.

## Configure Oracle and JWT

For Oracle deployments, select a non-dev profile and set these environment variables before starting the API:

- `SPRING_DATASOURCE_URL` — for example `jdbc:oracle:thin:@//localhost:1521/ORCLPDB1`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `JWT_SECRET_BASE64` — Base64-encoded random secret with at least 32 bytes for HS256 JWT signature validation

Do not commit database credentials or signing keys. `LiquibaseConfig` selects the XML master changelog, which includes formatted SQL script `001-create-agent-account.sql`; its Liquibase changeset IDs and authors are declared in that script. Liquibase applies the changesets at startup. Hibernate is set to validate the schema, not modify it.

The API login endpoint is `POST /api/v1/auth/login`; it checks a BCrypt password hash and returns a 30-minute HS256 bearer token. Protected API routes resolve the account ID from the token, then load the current role and active status from the database. In the local `dev` profile only, startup creates a temporary ADMIN login if needed and prints its generated password once in the backend log. Use that ADMIN login to create the first SUPER_DISTRIBUTOR from the Network page. The in-memory H2 database and generated credentials reset when the API restarts. Never enable the development bootstrap in a shared or production environment.

## Run

Use Gradle with a Java 17 JDK. From this directory, run `gradle bootRun` for local development; it uses an in-memory H2 database and a random, process-local JWT validation secret. H2 data and tokens are lost when the process restarts. For Oracle, set `SPRING_PROFILES_ACTIVE=oracle` and configure the Oracle/JWT environment variables. Build and test with `gradle test`; create an executable JAR with `gradle bootJar`.

Swagger UI is available at `/swagger-ui/index.html` and the OpenAPI document at `/v3/api-docs`. Use the development login through the app or call `POST /api/v1/auth/login`, then enter its bearer token using Swagger's Authorize control.

## Account endpoints

- `GET /api/v1/accounts` — return the caller and accounts in its downstream hierarchy.
- `POST /api/v1/accounts` — create an account permitted for the caller's role; the requested parent must be in the caller's hierarchy and match the fixed role chain.
- `PUT /api/v1/accounts/{accountId}` — update a downstream account's display name and active status, subject to role scope.

Creation chain: `ADMIN → SUPER_DISTRIBUTOR → MASTER_DISTRIBUTOR → DISTRIBUTOR → RETAILER`. Admins can create each non-admin role at the appropriate parent in their tree. Super distributors can create master distributors, distributors, and retailers within their own tree; master distributors can create distributors; distributors can create retailers. Management permissions are narrower for master distributors and distributors, matching the provided requirements.

The API currently establishes account hierarchy and authorization boundaries. Wallet, transaction, and report persistence/endpoints are not present in the existing React-only project, so those need their business data contracts before they can be safely implemented. All such endpoints must scope records by the same Oracle hierarchy IDs; ADMIN must be denied business operations.

## Distributor onboarding

`POST /api/v1/onboarding/distributors/otp/request` and `/otp/verify` support the personal-information step. The final multipart `POST /api/v1/onboarding/distributors` stores the photo, submitted fields, agreement version/acceptance time, and creates a Distributor child account for the authenticated Master Distributor. The PDF only specifies the step headings; this implementation assumes the personal, KYC, bank, velocity, and commission fields shown in the UI. It stores only the last four characters of KYC and bank account numbers. OTP delivery is local-development-only (code shown in the UI); production needs an SMS provider. Replace the placeholder agreement with approved legal text and integrate a KYC provider/bank vault before production.
