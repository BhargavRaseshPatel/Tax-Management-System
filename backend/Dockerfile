# Build stage: compile the jar. Tests run in CI (they need Docker for Testcontainers), not during the image build.
# Runs on the builder's own CPU: Java bytecode is the same everywhere, so an ARM64 image built on an x86 CI runner
# only emulates the short runtime stage below.
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS build
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
USER app
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
