package com.propertytax.report;

import java.util.List;

import com.propertytax.report.ReportService.ReportJobResponse;
import com.propertytax.report.ReportService.ReportRequest;
import com.propertytax.security.SecurityConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Staff only (see SecurityConfig). Reports are generated asynchronously; poll the job until it is COMPLETED. */
@Tag(name = "8. Reports", description = "Asynchronous: Step Functions + worker + S3 + DynamoDB job state")
@RestController
@RequestMapping("/api/reports")
class ReportController {

	private final ReportService service;

	ReportController(ReportService service) {
		this.service = service;
	}

	@Operation(summary = "Request a report. Returns the job at once (202); the work happens in the background")
	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	ReportJobResponse request(@Valid @RequestBody ReportRequest request, Authentication auth) {
		return service.request(request, SecurityConfig.userId(auth));
	}

	@Operation(summary = "My report jobs, newest first")
	@GetMapping
	List<ReportJobResponse> mine(Authentication auth) {
		return service.mine(SecurityConfig.userId(auth));
	}

	@GetMapping("/{jobId}")
	ReportJobResponse get(@PathVariable String jobId) {
		return service.get(jobId);
	}

	@Operation(summary = "Download a COMPLETED report as CSV (409 until it is ready)")
	@GetMapping(value = "/{jobId}/download", produces = "text/csv")
	ResponseEntity<byte[]> download(@PathVariable String jobId) {
		var file = service.download(jobId);
		return ResponseEntity.ok()
				.contentType(new MediaType("text", "csv"))
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString())
				.cacheControl(CacheControl.noStore())
				.body(file.content());
	}

}
