# Security Rules (steering file)

- Never commit credentials, keys, or `.env` files. Local values come from env vars; AWS values from Secrets Manager.
- Authorization is enforced in the backend (Spring Security), never only in the UI.
- `RESIDENT` access is filtered by data ownership on every query, not just by role.
- Only `health` and `info` actuator endpoints are exposed.
- Swagger UI / `/v3/api-docs` are dev conveniences: disable (`springdoc.*.enabled=false`) or restrict in production (Stage 16).
- Until Stage 8 every endpoint is unauthenticated — local development only, never deploy before Stage 8.
- The frontend's demo sign-in and route guards are UX only, never a security control. `localStorage` holds only the
  selected demo user, never tokens or secrets; Stage 8 keeps Cognito tokens out of `localStorage`.
- Error responses never leak stack traces or SQL.
- Database is never publicly reachable outside local development.
- Least-privilege IAM per task role; encryption at rest with KMS; TLS everywhere in AWS.
- Payments are simulated records only — no real card/bank data is ever accepted or stored.
- Dependencies are scanned in CI (SAST, Stage 17).
