# Security Rules (steering file)

- Never commit credentials, keys, or `.env` files. Local values come from env vars; AWS values from Secrets Manager.
- Authorization is enforced in the backend (Spring Security), never only in the UI.
- `RESIDENT` access is filtered by data ownership on every query, not just by role.
- Only `health` and `info` actuator endpoints are exposed.
- Error responses never leak stack traces or SQL.
- Database is never publicly reachable outside local development.
- Least-privilege IAM per task role; encryption at rest with KMS; TLS everywhere in AWS.
- Payments are simulated records only — no real card/bank data is ever accepted or stored.
- Dependencies are scanned in CI (SAST, Stage 17).
