# Security

How the system is protected, what was checked, and what is knowingly left open. Day-to-day rules for contributors
are in [security-rules.md](security-rules.md). **Status: reviewed with automated scanners and local tests; the AWS
parts have never been deployed, so they are unproven in a real account.**

## Controls by layer

| Layer | Control | Where |
|---|---|---|
| Identity | Every `/api/**` call needs a valid JWT (issuer, signature, expiry, audience). Cognito user pool, no self sign-up, 12-character passwords, optional TOTP MFA | `SecurityConfig`, `infra/auth.yaml` |
| Authorization | Default deny. Role read from the database on every request. Residents limited to records of properties they own; denials don't reveal whether a record exists | `SecurityConfig`, `PropertyOwnerRepository` |
| Input and output | Bean Validation on every request body; database constraints as the last line; errors as ProblemDetail without stack traces, SQL or AWS detail; CSV formula neutralising | DTOs, `V1`/`V2` migrations, `GlobalExceptionHandler`, `ReportGenerator` |
| Data integrity | Transactions with row locks; payments never deleted, only reversed; conditional writes for job state | services, `ReportJobStore` |
| Network | Database in isolated subnets, reachable only from the application security group. Tasks reachable only from the load balancer. Load balancer reachable only from CloudFront (address list + secret header) | `infra/network.yaml`, `infra/edge.yaml` |
| In transit | Browser ↔ CloudFront HTTPS with HSTS. Application ↔ Aurora TLS with certificate verification (`verify-full`), and the database rejects non-TLS. Buckets refuse non-TLS requests. AWS SDK calls are HTTPS | `infra/edge.yaml`, `infra/database.yaml`, `infra/storage.yaml`, `application.yml` |
| At rest | One customer-managed KMS key, rotated yearly, usable only through RDS, S3, DynamoDB, Secrets Manager and ECR in this account, plus the one log group. Encrypts Aurora (and backups), secrets, documents, report jobs, images and logs. Uploads cannot downgrade encryption | `infra/security.yaml` |
| Secrets | Database password generated, stored and rotated by AWS; injected into tasks by ECS. No secret in code, templates, images or git. Local `.env` is git-ignored | `infra/database.yaml`, `infra/app.yaml`, `.gitignore` |
| IAM | Separate execution, API and worker roles. Each statement names the exact actions and resources used. The API cannot take report tasks; the worker cannot start them; the state machine can only update one table | `infra/app.yaml`, `storage.yaml`, `report-jobs.yaml`, `report-workflow.yaml` |
| Browser | Content-Security-Policy, no framing, no sniffing, strict referrer. Token in `sessionStorage`, cleared on any 401 | `infra/edge.yaml`, `frontend/nginx.conf` |
| Runtime | Non-root containers; immutable, scanned image tags; health checks with automatic rollback; API docs and Swagger disabled in AWS; only `health` and `info` actuator endpoints | Dockerfiles, `infra/registry.yaml`, `infra/app.yaml` |
| Guard rails | The app refuses to start with no token issuer, and refuses the local dev sign-in if it finds itself on ECS | `SecurityConfig`, `DevTokenIssuer` |

## What was verified, and how to repeat it

| Check | Result | Command |
|---|---|---|
| Backend tests, including authentication, ownership, role boundaries, forged and deactivated tokens | 65 pass | `cd backend && ./mvnw test` |
| Template syntax and schema | clean, 10 templates | `cfn-lint -r ca-central-1 -t infra/*.yaml` |
| Infrastructure security scan (Checkov) | 101 passed, 0 failed, 13 documented exceptions | `checkov -d infra --framework cloudformation` |
| Secrets in git history and working tree (Gitleaks) | none in history; only the git-ignored local `.env` on disk | `docker run --rm -v "$PWD":/repo:ro ghcr.io/gitleaks/gitleaks git /repo` |
| TLS to the database is enforced and verified | proven against a local TLS PostgreSQL (wrong CA is refused) | see Stage 10 notes |

## Known gaps and accepted exceptions

Every scanner exception is recorded next to the resource in its template (`Metadata: checkov: skip`) with the reason.

| Gap | Why it is open | To close it |
|---|---|---|
| HTTP between CloudFront and the load balancer; CloudFront minimum TLS not configurable | AWS issues no certificate without a domain you own | Own domain, ACM certificate, HTTPS listener, `https-only` origin |
| No access logs for the load balancer, CloudFront or the buckets | Needs a dedicated log bucket; demo cost | Log bucket + access logging, or CloudTrail data events |
| No AWS WAF, no rate limiting | Demo cost | WAF managed rules on the distribution |
| Tasks in public subnets with public IPs (inbound closed by security group) | Avoids a NAT gateway | Private subnets with NAT or VPC endpoints |
| The application connects as the database master user | Flyway needs DDL rights | Separate least-privilege runtime user; migrations as their own task |
| KMS key policy uses a wildcard principal | Restricted to this account and five services; AWS's own pattern | Per-role key grants |
| 60-minute sessions without refresh; token readable by page scripts | Simplicity; CSP limits script injection | Refresh tokens, or a backend-for-frontend with HttpOnly cookies |
| No Container Insights, RDS enhanced monitoring, VPC flow logs or GuardDuty | Demo cost | Enable per account policy |
| Step Functions Local and the other emulators accept any credentials | Local development only | Not applicable to AWS |
| **Nothing has been deployed** | No AWS account | Deploy, then run a real penetration test and review IAM Access Analyzer findings |
