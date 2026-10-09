# REST API

Interactive docs: **http://localhost:8080/swagger-ui.html** · OpenAPI JSON: `/v3/api-docs`.
All data is fictional. No authentication yet (Stage 8 adds Cognito JWT + role checks).

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
| 409 | Unique/foreign-key conflict, ownership > 100% | `constraint` (DB constraint name) |

## Endpoints
| # | Method & path | Notes |
|---|---|---|
| 1 | `POST/GET /api/users`, `GET/PUT /api/users/{id}` | email stored lowercase; deactivate via `active=false` |
| 1 | `POST/GET /api/owners?name=`, `GET/PUT /api/owners/{id}` | optional `userId` must be a RESIDENT |
| 2 | `POST/GET /api/properties?q=`, `GET/PUT /api/properties/{id}` | `q` matches roll number or address |
| 2 | `GET/POST /api/properties/{id}/owners`, `DELETE /api/properties/{id}/owners/{ownerId}` | shares ≤ 100% |
| 2 | `GET /api/owners/{ownerId}/properties` | |
| 3 | `POST/GET /api/properties/{id}/assessments`, `GET/PUT /api/assessments/{id}` | one per property per year |
| 4 | `POST/GET /api/tax-rates?taxYear=`, `GET/PUT /api/tax-rates/{id}` | rate is a fraction (0.0075 = 0.75%); year/class immutable |
| 5 | `POST/GET /api/tax-accounts`, `GET /api/tax-accounts/{id}`, `GET /api/properties/{id}/tax-account` | one per property; number `TA-<propertyId>` |
| 6 | `GET /api/tax-accounts/{id}/bills`, `GET /api/tax-bills/{id}` | read-only until Stage 5 (generation) |
| 7 | `GET /api/tax-bills/{id}/payments`, `GET /api/payments/{id}` | read-only until Stage 5 (recording) |
