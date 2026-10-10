# REST API

Interactive docs: **http://localhost:8080/swagger-ui.html** · OpenAPI JSON: `/v3/api-docs`. All data is fictional.

## Authentication & access
Send `Authorization: Bearer <JWT>`. The token proves identity (`email` claim); the **role comes from the users table**.
Locally get a token from `POST /api/dev/token {"email": "..."}` (only when `DEV_AUTH=true`); deployed, from Cognito.

| Who | Can |
|---|---|
| Anyone | `GET /actuator/health`, API docs; `GET /api/dev/users`, `POST /api/dev/token` (local only) |
| Any signed-in user | `GET /api/me` (incl. `ownerId`), `GET /api/tax-rates` |
| RESIDENT | `GET` single records tied to a property they own: owner, property (+owners, assessments, tax-account), assessment, tax account (+bills, balance), bill (+payments, document), payment. Anything else → 403 (unknown ids too, so existence never leaks) |
| TAX_OFFICER | read everything; create/change all tax data; read users (to link owners) |
| ADMIN | create/change users; read-only on tax data |

`401` = missing, invalid, expired, or deactivated-user token. `403` = signed in but not allowed.
Payments are recorded as the authenticated officer (no recorder in the request body).

## Conventions
- JSON in/out; request DTOs validated with Bean Validation. Entities are never serialized.
- Lists that can grow are paged: `?page=0&size=20&sort=field,desc` (max size 100) →
  `{"content":[…],"page":{"size","number","totalElements","totalPages"}}`.
- `PUT` replaces the whole resource (omitted optional fields become null).
- No delete for records with financial history (users are deactivated; payments will be reversed).
- Errors are RFC 9457 `ProblemDetail`:

| Status | When | Extra fields |
|---|---|---|
| 400 | Validation, malformed JSON, bad enum, bad id type, unknown sort field, business-rule input error | `errors: {field: message}` for validation |
| 404 | Unknown id | |
| 405 | Unsupported method | |
| 409 | Unique/foreign-key conflict, ownership > 100%, billing/payment rule violated | `constraint` (DB constraint name, when from the DB) |

## Endpoints
| # | Method & path | Notes |
|---|---|---|
| 1 | `GET /api/me` | current user + linked `ownerId` |
| 1 | `POST/GET /api/users`, `GET/PUT /api/users/{id}` | email stored lowercase; deactivate via `active=false` (blocks sign-in immediately) |
| 1 | `POST/GET /api/owners?name=`, `GET/PUT /api/owners/{id}` | optional `userId` must be a RESIDENT |
| 2 | `POST/GET /api/properties?q=`, `GET/PUT /api/properties/{id}` | `q` matches roll number or address |
| 2 | `GET/POST /api/properties/{id}/owners`, `DELETE /api/properties/{id}/owners/{ownerId}` | shares ≤ 100% |
| 2 | `GET /api/owners/{ownerId}/properties` | |
| 3 | `POST/GET /api/properties/{id}/assessments`, `GET/PUT /api/assessments/{id}` | one per property per year |
| 4 | `POST/GET /api/tax-rates?taxYear=`, `GET/PUT /api/tax-rates/{id}` | rate is a fraction (0.0075 = 0.75%); year/class immutable |
| 5 | `POST/GET /api/tax-accounts`, `GET /api/tax-accounts/{id}`, `GET /api/properties/{id}/tax-account` | one per property; number `TA-<propertyId>` |
| 6 | `POST/GET /api/tax-accounts/{id}/bills`, `GET /api/tax-bills/{id}`, `POST /api/tax-bills/{id}/cancel` | amount calculated server-side; see [business-flow.md](business-flow.md) |
| 6 | `GET /api/tax-accounts/{id}/balance` | live bills − recorded payments |
| 6 | `GET /api/tax-bills/{id}/document` | the bill as `application/pdf` (attachment, `no-store`); generated and stored in S3 on first request; staff or owning resident; 503 if storage is down |
| 7 | `POST/GET /api/tax-bills/{id}/payments`, `GET /api/payments/{id}`, `POST /api/payments/{id}/reverse` | simulated; ≤ outstanding; unique reference |
