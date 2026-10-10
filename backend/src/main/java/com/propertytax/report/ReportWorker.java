package com.propertytax.report;

import java.util.Locale;

import com.propertytax.document.DocumentStore;
import com.propertytax.report.ReportJob.Status;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * The report worker: waits for a task from Step Functions, builds the report, uploads it to S3 and records the
 * result in DynamoDB. Runs only where REPORT_WORKER_ENABLED=true: a separate container/ECS service using this same
 * image (or inside the single dev-mode process). Scale by running more copies; Step Functions hands each task to one.
 */
@Component
@ConditionalOnProperty(name = "app.reports.worker-enabled", havingValue = "true")
class ReportWorker {

	private static final Logger log = LoggerFactory.getLogger(ReportWorker.class);

	private final ReportWorkflow workflow;
	private final ReportJobStore jobs;
	private final ReportGenerator generator;
	private final DocumentStore documents;
	private volatile boolean running = true;
	private Thread thread;

	ReportWorker(ReportWorkflow workflow, ReportJobStore jobs, ReportGenerator generator, DocumentStore documents) {
		this.workflow = workflow;
		this.jobs = jobs;
		this.generator = generator;
		this.documents = documents;
	}

	@EventListener(ApplicationReadyEvent.class)
	void start() {
		thread = Thread.ofPlatform().name("report-worker").daemon().start(this::pollUntilStopped);
		log.info("Report worker started");
	}

	@PreDestroy
	void stop() {
		running = false;
		if (thread != null) {
			thread.interrupt();
		}
	}

	private void pollUntilStopped() {
		while (running) {
			try {
				workflow.nextTask("civictax-worker").ifPresent(this::run);
			} catch (Exception e) {
				if (running) {
					log.warn("Could not poll for report tasks; retrying in 5 s: {}", e.getMessage());
					pause();
				}
			}
		}
	}

	private void run(ReportWorkflow.Task task) {
		try {
			workflow.succeed(task, process(task.jobId()));
		} catch (Exception e) {
			// Don't mark the job FAILED here: Step Functions retries first and records the failure after the last try.
			log.warn("Report job {} failed on this attempt: {}", task.jobId(), e.getMessage());
			workflow.fail(task, e.getMessage());
		}
	}

	/** Safe to run more than once for the same job (a retry, or a timed-out attempt that later finishes). */
	String process(String jobId) {
		var job = jobs.find(jobId).orElseThrow(() -> new IllegalStateException("Report job " + jobId + " not found"));
		if (job.status() == Status.COMPLETED) {
			return job.outputKey();
		}
		if (job.status() == Status.FAILED) {
			throw new IllegalStateException("Report job " + jobId + " has already failed");
		}
		jobs.markProcessing(jobId); // no-op on a retry, when it is already PROCESSING
		var csv = generator.generate(job.type(), job.taxYear());
		var key = "reports/%d/%s-%s.csv".formatted(job.taxYear(), job.type().name().toLowerCase(Locale.ROOT), jobId);
		documents.put(key, csv, "text/csv");
		jobs.markCompleted(jobId, key);
		return key;
	}

	private void pause() {
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}
