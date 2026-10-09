package com.propertytax.assessment;

import java.util.List;

import com.propertytax.assessment.AssessmentDtos.AssessmentRequest;
import com.propertytax.assessment.AssessmentDtos.AssessmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "3. Assessments")
@RestController
class AssessmentController {

	private final AssessmentService service;

	AssessmentController(AssessmentService service) {
		this.service = service;
	}

	@Operation(summary = "Record a property's assessed value for a tax year (one per year)")
	@PostMapping("/api/properties/{propertyId}/assessments")
	@ResponseStatus(HttpStatus.CREATED)
	AssessmentResponse create(@PathVariable Long propertyId, @Valid @RequestBody AssessmentRequest request) {
		return service.create(propertyId, request);
	}

	@Operation(summary = "Assessment history for a property, newest year first")
	@GetMapping("/api/properties/{propertyId}/assessments")
	List<AssessmentResponse> forProperty(@PathVariable Long propertyId) {
		return service.forProperty(propertyId);
	}

	@GetMapping("/api/assessments/{id}")
	AssessmentResponse get(@PathVariable Long id) {
		return service.get(id);
	}

	@PutMapping("/api/assessments/{id}")
	AssessmentResponse update(@PathVariable Long id, @Valid @RequestBody AssessmentRequest request) {
		return service.update(id, request);
	}

}
