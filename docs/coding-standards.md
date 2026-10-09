# Coding Standards (steering file)

- Java 21; records for DTOs; constructor injection only (no field `@Autowired`).
- Package by feature; layers inside a feature. No interface unless there are two implementations.
- Money: `BigDecimal` in Java, `NUMERIC(14,2)` in SQL. Never `double`/`float`.
- Schema changes only through Flyway migrations (never edit an applied one; add `V<n+1>__…`); `ddl-auto=validate`.
- JPA relationships are unidirectional child → parent, always `FetchType.LAZY`. No `@OneToMany` collections;
  query children through repositories. Fetch associations a use case needs with `@EntityGraph`/`JOIN FETCH`.
  `open-in-view` is off, so an unplanned lazy load fails in tests instead of silently issuing queries.
- Entities: protected no-arg constructor, public constructor for required fields, getters; setters/behaviour
  methods only when a use case needs to mutate. Enums used by one entity are nested in it (`TaxBill.Status`).
- Controllers return DTOs; validation via Bean Validation at the API boundary.
- Errors via one `@RestControllerAdvice` returning RFC 9457 `ProblemDetail`.
- Business logic lives in services, not controllers or the frontend.
- Every business rule (tax calc, balance, status transitions) has a test.
- Tests: pure logic → plain JUnit; service rules → Mockito (assert rejections never `save`); anything touching SQL,
  locks or transactions → Testcontainers PostgreSQL (never H2). Integration tests use unique fictional data instead of
  cleanup, because test classes share a container. A new safety mechanism (lock, constraint, transaction) needs a
  test that fails when it is removed.
- Config via `application.yml` + env vars; no secrets in the repo.
- Commits: small, one concern each, imperative message.
