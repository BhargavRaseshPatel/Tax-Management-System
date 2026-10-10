# Build stage: compile the jar. Tests run in CI (they need Docker for Testcontainers), not during the image build.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline          # cached layer: re-runs only when pom.xml changes
COPY src src
RUN ./mvnw -q -B -DskipTests package && cp target/backend-*.jar app.jar

# Runtime stage: JRE only, non-root user.
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY --from=build /src/app.jar app.jar
# Amazon's public RDS certificate authority bundle, so the app can verify Aurora's TLS certificate (sslmode=verify-full).
ADD --chmod=444 https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem /app/rds-ca.pem
USER app
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
