# PrimePay API

Spring Boot 3 / Java 17 modular monolith. Build with the Maven Wrapper (`mvnw` / `mvnw.cmd`), so no local Maven install is needed.

```powershell
.\mvnw.cmd spring-boot:run        # dev profile: H2 in Oracle mode, generated admin password in the log
.\mvnw.cmd test                   # unit + MockMvc tests
.\mvnw.cmd -DskipTests package    # target/primepay-api-0.1.0-SNAPSHOT.jar
```

Profiles: `dev` (default), `oracle` (needs `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET_BASE64`).

Package layout (`com.primepay`): `config`, `security`, `common` (API envelope, errors, request ID, auditing base), `auth`, `user`, `distributor`. The remaining modules are added in their phases; see [../docs/architecture.md](../docs/architecture.md).

## Endpoints available in Phase 1

| Method | Path | Notes |
|---|---|---|
| POST | `/api/v1/auth/login` | Public. Returns `{accessToken, tokenType, expiresAt, account}` (unwrapped until Phase 3) |
| GET | `/api/v1/auth/me` | Standard envelope; the signed-in account |
| GET/POST | `/api/v1/accounts` | Hierarchy-scoped list and create (role chain enforced) |
| PUT | `/api/v1/accounts/{id}` | Update a downstream account's name and active flag |
| POST | `/api/v1/onboarding/distributors/otp/request`, `/otp/verify`, `/onboarding/distributors` | Existing onboarding API (reworked in Phase 4) |
| GET | `/actuator/health` | Public health check |

Every error, from any endpoint, uses the error envelope:
`{"success":false,"error":{"code":"…","message":"…","fields":[…]},"timestamp":"…","requestId":"…"}`.
