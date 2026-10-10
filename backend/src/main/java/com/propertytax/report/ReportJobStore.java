package com.propertytax.report;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.propertytax.common.AwsClients;
import com.propertytax.report.ReportJob.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/**
 * The only class that talks to DynamoDB. Table: partition key {@code jobId}; global secondary index
 * {@code byRequester} (requestedBy, requestedAt) for "my jobs, newest first"; TTL on {@code expiresAt}.
 * Deployed: created by infra/report-jobs.yaml. Local emulator: created here on startup.
 */
@Component
public class ReportJobStore {

	static final String REQUESTER_INDEX = "byRequester";
	static final Duration RETENTION = Duration.ofDays(90);
	private static final int MAX_ERROR_LENGTH = 500;
	private static final Logger log = LoggerFactory.getLogger(ReportJobStore.class);

	private final DynamoDbClient dynamo;
	private final String table;

	ReportJobStore(@Value("${app.reports.table}") String table, @Value("${app.aws-region}") String region,
			@Value("${app.reports.endpoint:}") String endpoint) {
		this.table = table;
		this.dynamo = AwsClients.configure(DynamoDbClient.builder(), region, endpoint).build();
		if (!endpoint.isBlank()) {
			createTableInEmulator();
		}
	}

	public ReportJob create(ReportJob.Type type, long requestedBy, int taxYear) {
		var now = Instant.now();
		var job = new ReportJob(UUID.randomUUID().toString(), type, Status.QUEUED, requestedBy, taxYear, now, now,
				null, null, null);
		var item = new HashMap<String, AttributeValue>();
		item.put("jobId", s(job.jobId()));
		item.put("jobType", s(type.name()));
		item.put("status", s(Status.QUEUED.name()));
		item.put("requestedBy", n(requestedBy));
		item.put("taxYear", n(taxYear));
		item.put("requestedAt", s(now.toString()));
		item.put("updatedAt", s(now.toString()));
		item.put("expiresAt", n(now.plus(RETENTION).getEpochSecond())); // DynamoDB deletes the item after this
		dynamo.putItem(r -> r.tableName(table).item(item).conditionExpression("attribute_not_exists(jobId)"));
		return job;
	}

	public Optional<ReportJob> find(String jobId) {
		var item = dynamo.getItem(r -> r.tableName(table).key(key(jobId)).consistentRead(true)).item();
		return item == null || item.isEmpty() ? Optional.empty() : Optional.of(fromItem(item));
	}

	/** One user's jobs, newest first (a Query on the index; never a table Scan). */
	public List<ReportJob> findByRequester(long userId, int limit) {
		return dynamo.query(r -> r.tableName(table).indexName(REQUESTER_INDEX)
				.keyConditionExpression("requestedBy = :user")
				.expressionAttributeValues(Map.of(":user", n(userId)))
				.scanIndexForward(false)
				.limit(limit)).items().stream().map(ReportJobStore::fromItem).toList();
	}

	// Each transition is a conditional write: it succeeds only from the expected current status, so two workers
	// (or a retried step) can never both claim a job, and a finished job can never be reopened.
	// Returns false when the job was not in an allowed status (or does not exist).

	public boolean markProcessing(String jobId) {
		return transition(jobId, List.of(Status.QUEUED), Status.PROCESSING, Map.of());
	}

	public boolean markCompleted(String jobId, String outputKey) {
		var now = Instant.now().toString();
		return transition(jobId, List.of(Status.PROCESSING), Status.COMPLETED,
				Map.of("outputKey", s(outputKey), "completedAt", s(now)));
	}

	public boolean markFailed(String jobId, String errorMessage) {
		var message = errorMessage == null || errorMessage.isBlank() ? "Unknown error" : errorMessage;
		var now = Instant.now().toString();
		return transition(jobId, List.of(Status.QUEUED, Status.PROCESSING), Status.FAILED, Map.of(
				"errorMessage", s(message.substring(0, Math.min(message.length(), MAX_ERROR_LENGTH))),
				"completedAt", s(now)));
	}

	private boolean transition(String jobId, List<Status> from, Status to, Map<String, AttributeValue> extra) {
		var values = new HashMap<String, AttributeValue>();
		values.put(":to", s(to.name()));
		values.put(":now", s(Instant.now().toString()));
		var set = new StringBuilder("SET #status = :to, updatedAt = :now");
		extra.forEach((name, value) -> {
			set.append(", ").append(name).append(" = :").append(name);
			values.put(":" + name, value);
		});
		var allowed = new StringBuilder();
		for (int i = 0; i < from.size(); i++) {
			allowed.append(i == 0 ? "" : ", ").append(":from").append(i);
			values.put(":from" + i, s(from.get(i).name()));
		}
		try {
			dynamo.updateItem(r -> r.tableName(table).key(key(jobId))
					.updateExpression(set.toString())
					.conditionExpression("#status IN (" + allowed + ")")
					.expressionAttributeNames(Map.of("#status", "status")) // "status" is a DynamoDB reserved word
					.expressionAttributeValues(values));
			return true;
		} catch (ConditionalCheckFailedException e) {
			return false;
		}
	}

	private static ReportJob fromItem(Map<String, AttributeValue> item) {
		return new ReportJob(
				item.get("jobId").s(),
				ReportJob.Type.valueOf(item.get("jobType").s()),
				Status.valueOf(item.get("status").s()),
				Long.valueOf(item.get("requestedBy").n()),
				Integer.valueOf(item.get("taxYear").n()),
				Instant.parse(item.get("requestedAt").s()),
				Instant.parse(item.get("updatedAt").s()),
				item.containsKey("completedAt") ? Instant.parse(item.get("completedAt").s()) : null,
				item.containsKey("outputKey") ? item.get("outputKey").s() : null,
				item.containsKey("errorMessage") ? item.get("errorMessage").s() : null);
	}

	private static Map<String, AttributeValue> key(String jobId) {
		return Map.of("jobId", s(jobId));
	}

	private static AttributeValue s(String value) {
		return AttributeValue.fromS(value);
	}

	private static AttributeValue n(long value) {
		return AttributeValue.fromN(Long.toString(value));
	}

	/** LOCAL EMULATOR ONLY. Mirrors infra/report-jobs.yaml (keys and index); keep the two in step. */
	private void createTableInEmulator() {
		try {
			dynamo.createTable(r -> r.tableName(table)
					.billingMode(BillingMode.PAY_PER_REQUEST)
					.attributeDefinitions(
							a -> a.attributeName("jobId").attributeType(ScalarAttributeType.S),
							a -> a.attributeName("requestedBy").attributeType(ScalarAttributeType.N),
							a -> a.attributeName("requestedAt").attributeType(ScalarAttributeType.S))
					.keySchema(k -> k.attributeName("jobId").keyType(KeyType.HASH))
					.globalSecondaryIndexes(g -> g.indexName(REQUESTER_INDEX)
							.keySchema(k -> k.attributeName("requestedBy").keyType(KeyType.HASH),
									k -> k.attributeName("requestedAt").keyType(KeyType.RANGE))
							.projection(p -> p.projectionType(ProjectionType.ALL))));
		} catch (ResourceInUseException alreadyExists) {
			// created by an earlier start
		} catch (SdkClientException unreachable) {
			log.warn("DynamoDB emulator not reachable; report jobs will fail until it is running and the backend restarted");
		}
	}

}
