package com.propertytax.report;

import java.time.Instant;

/**
 * State of one asynchronous report job, stored in the DynamoDB table {@code report_jobs}.
 * This is job bookkeeping only: properties, assessments, bills and payments stay in PostgreSQL.
 */
public record ReportJob(
		String jobId,
		Type type,
		Status status,
		Long requestedBy, // users.id of the requester
		Integer taxYear,
		Instant requestedAt,
		Instant updatedAt,
		Instant completedAt, // set when COMPLETED or FAILED
		String outputKey, // S3 key of the finished report, when COMPLETED
		String errorMessage) { // why it FAILED

	public enum Type { TAX_ROLL_SUMMARY, OUTSTANDING_BALANCES, PAYMENTS_RECONCILIATION }

	/** QUEUED → PROCESSING → COMPLETED, or FAILED from QUEUED/PROCESSING. Enforced by conditional writes. */
	public enum Status { QUEUED, PROCESSING, COMPLETED, FAILED }

}
