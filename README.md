# Property Tax Management (demo)

Portfolio demo of a municipal property tax system modernized from a legacy Oracle Forms/PL/SQL style
into Spring Boot + React on AWS. **All data and tax rates are fictional.** Not affiliated with any municipality.
Payments are simulated records only; no money moves.

## Prerequisites
Java 21+ and Docker (running). Maven is not needed — use the wrapper `./mvnw`.

## Run the backend
```bash
cp .env.example .env              # then set DB_PASSWORD
docker compose up -d --wait       # PostgreSQL 17 on localhost:5433
set -a; . ./.env; set +a          # export DB_* for Spring
cd backend && ./mvnw spring-boot:run
```
- Health: `curl localhost:8080/actuator/health` → `{"status":"UP"}` (Flyway migrates on startup)
- API docs: http://localhost:8080/swagger-ui.html — reference in [docs/api.md](docs/api.md), rules in [docs/business-flow.md](docs/business-flow.md)
- Stop: `Ctrl+C`, then `docker compose stop` (data kept) or `docker compose down -v` (data deleted)

## Run the tests
```bash
cd backend && ./mvnw test
```
Tests start their own throwaway PostgreSQL 17 via Testcontainers; they never touch your local database.

| Layer | Classes | What it proves |
|---|---|---|
| Unit (pure) | `TaxCalculatorTest` | formula, half-up rounding, final = annual − interim |
| Unit (Mockito) | `TaxBillServiceTest`, `PaymentServiceTest` | every billing/payment rule; rejections never save |
| Persistence | `BackendApplicationTests`, `PersistenceIntegrationTest` | exactly 9 tables, relationships, lazy loading, constraints |
| API (MockMvc) | `ApiIntegrationTest`, `BillingWorkflowIntegrationTest` | every endpoint, status codes, ProblemDetail bodies, full billing workflow |
| Transactions | `TransactionIntegrationTest` | rollback after a mid-transaction failure; row locks under concurrent requests |

Run one class: `./mvnw test -Dtest=TransactionIntegrationTest`.

**Troubleshooting:** if tests fail with `…class cannot be opened because it does not exist`, an IDE (e.g. VS Code's
Java extension) recompiled into `target/` during the run. Wait for the IDE build to finish and rerun.

See [docs/](docs/) for requirements, architecture, database design, security rules, and standards.
