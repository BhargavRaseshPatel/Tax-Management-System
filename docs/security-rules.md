# Security Rules (steering file)

- Never commit credentials, keys, or `.env` files. Local values come from env vars; AWS values from Secrets Manager.
- Authorization is enforced in the backend (Spring Security), never only in the UI.
- `RESIDENT` access is filtered by data ownership on every query, not just by role.
- Only `health` and `info` actuator endpoints are exposed.
- Swagger UI / `/v3/api-docs` are dev conveniences: disable (`springdoc.*.enabled=false`) or restrict in production (Stage 16).
- Every `/api/**` call requires a valid JWT (Spring Security resource server); all rules live in `SecurityConfig`
  and default to deny. Roles are read from the database per request, so deactivation/role changes apply at once.
- `DEV_AUTH=true` (local token issuer) must never be set in a deployed environment; it logs a WARN at startup.
  Production uses the Cognito issuer URI. With no issuer configured the app fails to start (fail closed).
- Residents are restricted by data ownership (via `property_owners`), not just role. Denials are 403 even for
  unknown ids, so record existence never leaks.
- Frontend route guards are UX only. The token lives in `sessionStorage` (per tab, cleared on close), never
  `localStorage`; any 401 clears it.
- Error responses never leak stack traces or SQL.
- Database is never publicly reachable outside local development.
- Least-privilege IAM per task role; encryption at rest with KMS; TLS everywhere in AWS.
- Payments are simulated records only — no real card/bank data is ever accepted or stored.
- Dependencies are scanned in CI (SAST, Stage 17).
