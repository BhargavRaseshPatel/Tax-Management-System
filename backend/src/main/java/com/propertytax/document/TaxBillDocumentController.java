package com.propertytax.document;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "6. Tax bills")
@RestController
class TaxBillDocumentController {

	private final TaxBillDocumentService service;

	TaxBillDocumentController(TaxBillDocumentService service) {
		this.service = service;
	}

	/** Access is decided in SecurityConfig: staff, or a resident who owns the bill's property. */
	@Operation(summary = "Download the bill as a PDF (generated and stored in S3 on first request)")
	@GetMapping(value = "/api/tax-bills/{id}/document", produces = MediaType.APPLICATION_PDF_VALUE)
	ResponseEntity<byte[]> download(@PathVariable Long id) {
		var document = service.download(id);
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(document.fileName()).build().toString())
				.cacheControl(CacheControl.noStore()) // personal document: never cache in shared or browser caches
				.body(document.content());
	}

}
