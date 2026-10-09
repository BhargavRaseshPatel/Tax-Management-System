# Coding Standards (steering file)

- Java 21; records for DTOs; constructor injection only (no field `@Autowired`).
- Package by feature; layers inside a feature. No interface unless there are two implementations.
- Money: `BigDecimal` in Java, `NUMERIC(14,2)` in SQL. Never `double`/`float`.
- Schema changes only through Flyway migrations; `ddl-auto=validate`.
- Controllers return DTOs; validation via Bean Validation at the API boundary.
- Errors via one `@RestControllerAdvice` returning RFC 9457 `ProblemDetail`.
- Business logic lives in services, not controllers or the frontend.
- Every business rule (tax calc, balance, status transitions) has a test.
- Config via `application.yml` + env vars; no secrets in the repo.
- Commits: small, one concern each, imperative message.
