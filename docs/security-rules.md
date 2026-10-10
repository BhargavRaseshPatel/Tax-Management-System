# Security Rules (steering file)

- Never commit credentials, keys, or `.env` files. Local values come from env vars; AWS values from Secrets Manager.
- Authorization is enforced in the backend (Spring Security), never only in the UI.
- `RESIDENT` access is filtered by data ownership on every query, not just by role.
- Only `health` and `info` actuator endpoints are exposed.
- Swagger UI / `/v3/api-docs` are dev conveniences: disable (`springdoc.*.enabled=false`) or restrict in production (Stage 16).
- Every `/api/**` call requires a valid JWT (Spring Security resource server); all rules live in `SecurityConfig`
  and default to deny. Roles are read from the database per request, so deactivation/role changes apply at once.
- Deployed: the only public entry is CloudFront over HTTPS (HSTS). The load balancer accepts CloudFront only
  (address list + secret header) and API docs/actuator are not routed.
- Deployed: tokens come from the Cognito user pool (no self sign-up); the backend validates issuer and audience.
  `BOOTSTRAP_ADMIN_EMAIL` only acts on an empty users table.
- `DEV_AUTH=true` (local token issuer) must never be set in a deployed environment; it logs a WARN at startup.
  Production uses the Cognito issuer URI. With no issuer configured the app fails to start (fail closed).
- Residents are restricted by data ownership (via `property_owners`), not just role. Denials are 403 even for
  unknown ids, so record existence never leaks.
- Frontend route guards are UX only. The token lives in `sessionStorage` (per tab, cleared on close), never
  `localStorage`; any 401 clears it.
- Error responses never leak stack traces or SQL.
- Database is never publicly reachable outside local development. In AWS it sits in isolated subnets, accepts 5432
  only from the application security group, requires TLS (`rds.force_ssl=1`), and the app connects with
  `sslmode=verify-full`. The master password is generated and held by Secrets Manager; it is never typed or stored.
- AWS credentials stay in `~/.aws` on the developer's machine: never in the repo, `.env`, CI logs, or chat.
- Documents: the S3 bucket is never public and object keys/URLs are never sent to clients; downloads go through the
  API (authorized, `no-store`). Keys contain no personal data. The app's IAM policy allows only Get/Put on its prefixes.
- Reports are staff-only, may contain officer email addresses, and are downloaded through the API (`no-store`).
  CSV cells that a spreadsheet would run as a formula (`= + - @`) are neutralised.
- DynamoDB holds report-job state only, never business or personal data beyond the requester's numeric user id.
- AWS access uses the task role (default credential chain). The only credentials in code are placeholders for the
  local emulator, used solely when `S3_ENDPOINT` is set.
- Least-privilege IAM per task role; encryption at rest with KMS; TLS everywhere in AWS.
- Payments are simulated records only — no real card/bank data is ever accepted or stored.
- Dependencies are scanned in CI (SAST, Stage 17).
