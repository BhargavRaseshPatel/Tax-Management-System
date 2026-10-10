package com.propertytax;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
	}

	/** S3 emulator (same image as compose.yaml) so document tests make real S3 API calls. */
	@Bean
	GenericContainer<?> s3Container() {
		return new GenericContainer<>(DockerImageName.parse("adobe/s3mock:5.2.3"))
				.withExposedPorts(9090)
				.withEnv("COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS", "civictax-documents")
				.waitingFor(Wait.forHttp("/").forPort(9090));
	}

	/** Amazon's DynamoDB Local (same image as compose.yaml) for the report_jobs table. */
	@Bean
	GenericContainer<?> dynamoContainer() {
		return new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:3.3.1"))
				.withExposedPorts(8000)
				.withCommand("-jar", "DynamoDBLocal.jar", "-sharedDb", "-inMemory")
				.waitingFor(Wait.forListeningPort());
	}

	@Bean
	DynamicPropertyRegistrar emulatorEndpoints(GenericContainer<?> s3Container, GenericContainer<?> dynamoContainer) {
		return registry -> {
			registry.add("app.documents.endpoint", () -> "http://" + s3Container.getHost() + ":" + s3Container.getMappedPort(9090));
			registry.add("app.reports.endpoint", () -> "http://" + dynamoContainer.getHost() + ":" + dynamoContainer.getMappedPort(8000));
		};
	}

}
