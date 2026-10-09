# Property Tax Management (demo)

Portfolio demo of a municipal property tax system modernized from a legacy Oracle Forms/PL/SQL style
into Spring Boot + React on AWS. **All data and tax rates are fictional.** Not affiliated with any municipality.

## Run the backend
Requires Java 21+ and Docker.
```bash
cp .env.example .env              # then set DB_PASSWORD
docker compose up -d --wait       # PostgreSQL 17 on localhost:5433
set -a; . ./.env; set +a          # export DB_* for Spring
cd backend && ./mvnw spring-boot:run
curl localhost:8080/actuator/health   # {"status":"UP"} — Flyway migrates on startup
```

Tests start their own throwaway PostgreSQL via Testcontainers (Docker must be running):
```bash
cd backend && ./mvnw test
```

Stop the database (data is kept in a Docker volume): `docker compose stop`.
Delete it entirely: `docker compose down -v`.

See [docs/](docs/) for requirements, architecture, database design, and standards.
