package com.propertytax;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import com.propertytax.report.ReportJob;
import com.propertytax.report.ReportJob.Status;
import com.propertytax.report.ReportJob.Type;
import com.propertytax.report.ReportJobStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Stage 12: the report_jobs table against Amazon's DynamoDB Local. Each test uses its own requester id. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ReportJobStoreIntegrationTest {

	static final AtomicLong REQUESTER = new AtomicLong(900_000);

	@Autowired
	ReportJobStore jobs;

	@Test
	void newJobIsQueuedAndReadBackWithEveryField() {
		long requester = REQUESTER.incrementAndGet();
		var before = Instant.now().minusSeconds(1);

		var created = jobs.create(Type.OUTSTANDING_BALANCES, requester, 2026);

		var stored = jobs.find(created.jobId()).orElseThrow();
		assertThat(stored).isEqualTo(created);
		assertThat(stored.status()).isEqualTo(Status.QUEUED);
		assertThat(stored.type()).isEqualTo(Type.OUTSTANDING_BALANCES);
		assertThat(stored.requestedBy()).isEqualTo(requester);
		assertThat(stored.taxYear()).isEqualTo(2026);
		assertThat(stored.requestedAt()).isAfter(before);
		assertThat(stored.outputKey()).isNull();
		assertThat(stored.errorMessage()).isNull();
		assertThat(jobs.find("no-such-job")).isEmpty();
	}

	@Test
	void successfulLifecycleRecordsOutputKeyAndCompletionTime() {
		var job = jobs.create(Type.TAX_ROLL_SUMMARY, REQUESTER.incrementAndGet(), 2026);

		assertThat(jobs.markProcessing(job.jobId())).isTrue();
		assertThat(jobs.find(job.jobId()).orElseThrow().status()).isEqualTo(Status.PROCESSING);
		assertThat(jobs.markCompleted(job.jobId(), "reports/2026/" + job.jobId() + ".csv")).isTrue();

		var done = jobs.find(job.jobId()).orElseThrow();
		assertThat(done.status()).isEqualTo(Status.COMPLETED);
		assertThat(done.outputKey()).isEqualTo("reports/2026/" + job.jobId() + ".csv");
		assertThat(done.completedAt()).isNotNull();
		assertThat(done.updatedAt()).isAfterOrEqualTo(done.requestedAt());
	}

	@Test
	void transitionsOutOfOrderAreRefusedAndChangeNothing() {
		var job = jobs.create(Type.TAX_ROLL_SUMMARY, REQUESTER.incrementAndGet(), 2026);

		assertThat(jobs.markCompleted(job.jobId(), "reports/x.csv")).isFalse(); // never started
		assertThat(jobs.markProcessing(job.jobId())).isTrue();
		assertThat(jobs.markProcessing(job.jobId())).isFalse();                  // a second worker can't claim it
		assertThat(jobs.markCompleted(job.jobId(), "reports/x.csv")).isTrue();
		assertThat(jobs.markFailed(job.jobId(), "too late")).isFalse();          // finished jobs stay finished
		assertThat(jobs.markProcessing("no-such-job")).isFalse();

		var stored = jobs.find(job.jobId()).orElseThrow();
		assertThat(stored.status()).isEqualTo(Status.COMPLETED);
		assertThat(stored.errorMessage()).isNull();
	}

	@Test
	void failureKeepsATruncatedErrorMessage() {
		var job = jobs.create(Type.PAYMENTS_RECONCILIATION, REQUESTER.incrementAndGet(), 2025);
		jobs.markProcessing(job.jobId());

		assertThat(jobs.markFailed(job.jobId(), "x".repeat(2000))).isTrue();

		var failed = jobs.find(job.jobId()).orElseThrow();
		assertThat(failed.status()).isEqualTo(Status.FAILED);
		assertThat(failed.errorMessage()).hasSize(500);
		assertThat(failed.completedAt()).isNotNull();
		assertThat(failed.outputKey()).isNull();
	}

	@Test
	void jobsAreListedPerRequesterNewestFirst() throws Exception {
		long alice = REQUESTER.incrementAndGet();
		long bob = REQUESTER.incrementAndGet();
		var first = jobs.create(Type.TAX_ROLL_SUMMARY, alice, 2025);
		Thread.sleep(5); // distinct requestedAt sort keys
		var second = jobs.create(Type.OUTSTANDING_BALANCES, alice, 2026);
		jobs.create(Type.TAX_ROLL_SUMMARY, bob, 2026);

		assertThat(jobs.findByRequester(alice, 10)).extracting(ReportJob::jobId)
				.containsExactly(second.jobId(), first.jobId());
		assertThat(jobs.findByRequester(alice, 1)).extracting(ReportJob::jobId).containsExactly(second.jobId());
		assertThat(jobs.findByRequester(bob, 10)).hasSize(1);
	}

}
