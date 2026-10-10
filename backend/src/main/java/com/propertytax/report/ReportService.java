package com.propertytax.report;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import com.propertytax.common.ApiErrors;
import com.propertytax.document.DocumentStore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

	public record ReportRequest(@NotNull ReportJob.Type type, @NotNull @Min(2000) @Max(2100) Integer taxYear) {}

	/** What clients see. The S3 key stays server-side; "downloadable" says whether the file is ready. */
	public record ReportJobResponse(String jobId, ReportJob.Type type, ReportJob.Status status, Integer taxYear,
			Long requestedBy, Instant requestedAt, Instant updatedAt, Instant completedAt, String errorMessage,
			boolean downloadable) {

		static ReportJobResponse from(ReportJob j) {
			return new ReportJobResponse(j.jobId(), j.type(), j.status(), j.taxYear(), j.requestedBy(), j.requestedAt(),
					j.updatedAt(), j.completedAt(), j.errorMessage(), j.status() == ReportJob.Status.COMPLETED);
		}
	}

	public record ReportFile(String fileName, byte[] content) {}

	private final ReportJobStore jobs;
	private final ReportWorkflow workflow;
	private final DocumentStore documents;

	ReportService(ReportJobStore jobs, ReportWorkflow workflow, DocumentStore documents) {
		this.jobs = jobs;
		this.workflow = workflow;
		this.documents = documents;
	}

	/** Creates the job and hands it to Step Functions; returns immediately, before any report work happens. */
	public ReportJobResponse request(ReportRequest request, long userId) {
		var job = jobs.create(request.type(), userId, request.taxYear());
		try {
			workflow.start(job.jobId());
		} catch (RuntimeException e) {
			// Never leave a job QUEUED that nothing will ever process.
			jobs.markFailed(job.jobId(), "The report workflow could not be started");
			throw e;
		}
		return ReportJobResponse.from(job);
	}

	public List<ReportJobResponse> mine(long userId) {
		return jobs.findByRequester(userId, 50).stream().map(ReportJobResponse::from).toList();
	}

	public ReportJobResponse get(String jobId) {
		return ReportJobResponse.from(find(jobId));
	}

	public ReportFile download(String jobId) {
		var job = find(jobId);
		if (job.status() != ReportJob.Status.COMPLETED) {
			throw ApiErrors.conflict("Report " + jobId + " is " + job.status() + ", not ready to download");
		}
		var content = documents.get(job.outputKey())
				.orElseThrow(() -> ApiErrors.notFound("Stored file for report", jobId + " (request the report again)"));
		var name = "%s-%d-%s.csv".formatted(job.type().name().toLowerCase(Locale.ROOT), job.taxYear(), jobId.substring(0, 8));
		return new ReportFile(name, content);
	}

	private ReportJob find(String jobId) {
		return jobs.find(jobId).orElseThrow(() -> ApiErrors.notFound("Report job", jobId));
	}

}
