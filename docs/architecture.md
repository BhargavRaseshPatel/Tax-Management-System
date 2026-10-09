# Architecture (steering file)

## Repository layout
```
/backend    Spring Boot 4.1 API (Java 21, Maven wrapper)
/frontend   React + TS + Vite + MUI          (Stage 7)
/docs       Steering files and documentation
compose.yaml  Local PostgreSQL 17 on 127.0.0.1:5433 (5432 is left to any host-installed Postgres)
```

## Backend
Modular monolith, packaged **by feature**, layered inside each feature:
```
com.propertytax
├── BackendApplication
├── common/        BaseEntity (id + timestamps); exception handling later
├── user/          controller · service · repository · dto · mapper · entity
├── owner/
├── property/      (incl. property_owners)
├── assessment/
├── taxrate/
├── taxaccount/
├── taxbill/
└── payment/
```
Packages are created in the stage that first needs them — no empty scaffolding.

Request flow: `Controller (DTO, @Valid) → Service (@Transactional, business rules) → Repository (JPA) → PostgreSQL`.
Entities never leave the service layer; controllers speak DTOs only.

Per feature: `XController` (package-private, HTTP only) · `XService` (transactions, rules, returns DTOs) ·
`XRepository` (`require(id)` → 404) · `XDtos` (request/response records; `Response.from(entity)` is the mapper) · entity.
Shared: `common/ApiErrors` (404/409/400 factories), `common/GlobalExceptionHandler` (ProblemDetail for validation
and DB constraint errors). API reference: [api.md](api.md).

## Frontend (`/frontend`)
React 19 + TypeScript + Vite + MUI 9 + React Router + TanStack Query. Design reference: Google Stitch mock-ups.
```
src/api/        client.ts (the only fetch; ProblemDetail → ApiError; useApi / useApiMutation), types.ts (DTO mirrors)
src/auth/       demo session (pick a user; Stage 8 swaps in Cognito)
src/components/ AppShell (role nav, role preview), ui.tsx (StatCard, StatusChip, SectionCard, FormDialog…), format.ts
src/pages/      resident/ · officer/ · admin/   one file per screen
```
- Every number comes from the API; the UI never calculates tax, balances or statuses (it only sums for display).
- One cache key per URL; any successful write refetches all queries (simple and always consistent for a demo).
- Route guards only steer navigation — they are not security. The backend enforces roles from Stage 8.
- Static mock data only where no backend exists yet (Reports job tracker), always labelled "Mock data".

## Dependency introduction plan
| Stage | Adds |
|---|---|
| 1 | web, validation, actuator |
| 2 | data-jpa, postgresql driver, flyway, testcontainers (tests need a real Postgres from day one) |
| 4 | springdoc-openapi |
| 8 | oauth2-resource-server (Cognito JWT) |
| 11–13 | AWS SDK v2 (S3, DynamoDB, Step Functions) |

## Target AWS shape (Stages 10–17)
CloudFront → S3 (frontend) · ALB → ECS Fargate (API) → Aurora PostgreSQL ·
Step Functions → Fargate worker → S3 reports + DynamoDB `report_jobs` · Cognito for auth.

## Legacy mapping (Stage 18)
Oracle Forms screens → React pages · PL/SQL packages → `@Service` classes · DB triggers/constraints → Flyway constraints + service rules · Oracle Reports → async report worker.
