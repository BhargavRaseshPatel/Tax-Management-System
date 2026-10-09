package com.propertytax.common;

import java.util.TreeMap;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Inherited handlers cover validation (400), malformed JSON (400), ResponseStatusException, 405, etc.
 * Added here: database constraint violations and bad sort fields. Never echoes SQL or stack traces.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail dataIntegrity(DataIntegrityViolationException ex) {
		var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Request conflicts with existing data");
		if (ex.getCause() instanceof ConstraintViolationException cve && cve.getConstraintName() != null) {
			problem.setProperty("constraint", cve.getConstraintName());
		}
		return problem;
	}

	/** Adds {"errors": {"field": "message"}} so clients can show which inputs failed. */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		var errors = new TreeMap<String, String>();
		ex.getBindingResult().getFieldErrors().forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
		ex.getBody().setDetail("Validation failed");
		ex.getBody().setProperty("errors", errors);
		return handleExceptionInternal(ex, ex.getBody(), headers, status, request);
	}

	@ExceptionHandler(PropertyReferenceException.class)
	ProblemDetail badSort(PropertyReferenceException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Unknown sort property: " + ex.getPropertyName());
	}

}
