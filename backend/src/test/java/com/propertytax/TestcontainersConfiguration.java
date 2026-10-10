package com.propertytax;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
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

	/** One private network per Spring context, so the Step Functions emulator can reach "its" DynamoDB by name. */
	@Bean(destroyMethod = "close")
	Network emulatorNetwork() {
		return Network.newNetwork();
	}

	/** Amazon's DynamoDB Local (same image as compose.yaml) for the report_jobs table. */
	@Bean
	GenericContainer<?> dynamoContainer(Network emulatorNetwork) {
		return new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:3.3.1"))
				.withExposedPorts(8000)
				.withNetwork(emulatorNetwork).withNetworkAliases("dynamodb")
				.withCommand("-jar", "DynamoDBLocal.jar", "-sharedDb", "-inMemory")
				.waitingFor(Wait.forListeningPort());
	}

	/** Amazon's Step Functions Local; its "mark job FAILED" step writes to the DynamoDB container above. */
	@Bean
	GenericContainer<?> stepFunctionsContainer(Network emulatorNetwork) {
		return new GenericContainer<>(DockerImageName.parse("amazon/aws-stepfunctions-local:2.0.0"))
				.withExposedPorts(8083)
				.withNetwork(emulatorNetwork)
				.withEnv("DYNAMODB_ENDPOINT", "http://dynamodb:8000")
				.withEnv("AWS_DEFAULT_REGION", "ca-central-1")
				.withEnv("AWS_ACCESS_KEY_ID", "local").withEnv("AWS_SECRET_ACCESS_KEY", "local") // emulator placeholders
				.waitingFor(Wait.forListeningPort());
	}

	@Bean
	DynamicPropertyRegistrar emulatorEndpoints(GenericContainer<?> s3Container, GenericContainer<?> dynamoContainer,
			GenericContainer<?> stepFunctionsContainer) {
		return registry -> {
			registry.add("app.documents.endpoint", () -> url(s3Container, 9090));
			registry.add("app.reports.endpoint", () -> url(dynamoContainer, 8000));
			registry.add("app.reports.workflow.endpoint", () -> url(stepFunctionsContainer, 8083));
		};
	}

	private static String url(GenericContainer<?> container, int port) {
		return "http://" + container.getHost() + ":" + container.getMappedPort(port);
	}

}
