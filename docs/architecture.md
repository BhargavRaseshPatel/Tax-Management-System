# Architecture (steering file)

## Repository layout
```
/backend    Spring Boot 4.1 API (Java 21, Maven wrapper)
/frontend   React + TS + Vite + MUI          (Stage 7)
/docs       Steering files and documentation
```

## Backend
Modular monolith, packaged **by feature**, layered inside each feature:
```
com.propertytax
├── BackendApplication
├── common/        exception handling, shared config      (added when first needed)
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

## Dependency introduction plan
| Stage | Adds |
|---|---|
| 1 | web, validation, actuator |
| 2 | data-jpa, postgresql driver, flyway (local Postgres in Docker — no native install) |
| 4 | springdoc-openapi |
| 6 | testcontainers |
| 8 | oauth2-resource-server (Cognito JWT) |
| 11–13 | AWS SDK v2 (S3, DynamoDB, Step Functions) |

## Target AWS shape (Stages 10–17)
CloudFront → S3 (frontend) · ALB → ECS Fargate (API) → Aurora PostgreSQL ·
Step Functions → Fargate worker → S3 reports + DynamoDB `report_jobs` · Cognito for auth.

## Legacy mapping (Stage 18)
Oracle Forms screens → React pages · PL/SQL packages → `@Service` classes · DB triggers/constraints → Flyway constraints + service rules · Oracle Reports → async report worker.
