package com.propertytax.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import com.propertytax.common.AwsClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.services.sfn.SfnClient;
import software.amazon.awssdk.services.sfn.model.StateMachineAlreadyExistsException;

/**
 * The only class that talks to AWS Step Functions. The state machine has one activity task: the API starts an
 * execution per job, a worker long-polls for the task, and Step Functions owns timeouts, retries and the final
 * "mark the job FAILED" step. Deployed: infra/report-workflow.yaml. Local emulator: created here on startup.
 */
@Component
public class ReportWorkflow {

	/** One unit of work handed to a worker; the token identifies it when reporting the outcome. */
	public record Task(String token, String jobId) {}

	/** Step Functions holds a getActivityTask call open for up to 60 s; the HTTP read timeout must outlast it. */
	private static final Duration LONG_POLL_READ_TIMEOUT = Duration.ofSeconds(70);
	private static final Logger log = LoggerFactory.getLogger(ReportWorkflow.class);

	private final SfnClient sfn;
	private String stateMachineArn;
	private String activityArn;

	ReportWorkflow(@Value("${app.aws-region}") String region,
			@Value("${app.reports.workflow.endpoint:}") String endpoint,
			@Value("${app.reports.workflow.state-machine-arn:}") String stateMachineArn,
			@Value("${app.reports.workflow.activity-arn:}") String activityArn,
			@Value("${app.reports.table}") String jobsTable) {
		this.sfn = AwsClients.configure(SfnClient.builder(), region, endpoint)
				.httpClientBuilder(Apache5HttpClient.builder().socketTimeout(LONG_POLL_READ_TIMEOUT))
				.build();
		this.stateMachineArn = stateMachineArn;
		this.activityArn = activityArn;
		if (!endpoint.isBlank()) {
			createInEmulator(jobsTable);
		}
	}

	/** Starts one execution for the job. The execution name is the job id, so a job can never be started twice. */
	public void start(String jobId) {
		requireConfigured();
		sfn.startExecution(r -> r.stateMachineArn(stateMachineArn).name(jobId).input("{\"jobId\":\"" + jobId + "\"}"));
	}

	/** Waits up to 60 s for work. Empty when nothing arrived in that time. */
	public Optional<Task> nextTask(String workerName) {
		requireConfigured();
		var response = sfn.getActivityTask(r -> r.activityArn(activityArn).workerName(workerName));
		if (response.taskToken() == null || response.taskToken().isEmpty()) {
			return Optional.empty();
		}
		var jobId = (String) JsonParserFactory.getJsonParser().parseMap(response.input()).get("jobId");
		return Optional.of(new Task(response.taskToken(), jobId));
	}

	public void succeed(Task task, String outputKey) {
		sfn.sendTaskSuccess(r -> r.taskToken(task.token()).output("{\"outputKey\":\"" + outputKey + "\"}"));
	}

	/** Step Functions retries the task; after the last attempt it marks the job FAILED with this cause. */
	public void fail(Task task, String cause) {
		var text = cause == null || cause.isBlank() ? "Unknown error" : cause;
		sfn.sendTaskFailure(r -> r.taskToken(task.token()).error("ReportGenerationFailed")
				.cause(text.substring(0, Math.min(text.length(), 500))));
	}

	private void requireConfigured() {
		if (stateMachineArn.isBlank() || activityArn.isBlank()) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Report processing is not available");
		}
	}

	/** LOCAL EMULATOR ONLY: the activity and state machine that CloudFormation creates in AWS. */
	private void createInEmulator(String jobsTable) {
		try {
			activityArn = sfn.createActivity(r -> r.name("civictax-report-worker")).activityArn();
			var definition = new ClassPathResource("report-workflow.asl.json").getContentAsString(StandardCharsets.UTF_8)
					.replace("${ActivityArn}", activityArn).replace("${TableName}", jobsTable);
			try {
				stateMachineArn = sfn.createStateMachine(r -> r.name("civictax-report").definition(definition)
						.roleArn("arn:aws:iam::012345678901:role/local-emulator")).stateMachineArn(); // role is ignored locally
			} catch (StateMachineAlreadyExistsException existing) {
				stateMachineArn = activityArn.replace(":activity:civictax-report-worker", ":stateMachine:civictax-report");
				sfn.updateStateMachine(r -> r.stateMachineArn(stateMachineArn).definition(definition));
			}
		} catch (SdkClientException unreachable) {
			log.warn("Step Functions emulator not reachable; reports will fail until it is running and the backend restarted");
		} catch (IOException e) {
			throw new IllegalStateException("report-workflow.asl.json is missing from the classpath", e);
		}
	}

}
