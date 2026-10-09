package com.propertytax.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Spring renders these as RFC 9457 ProblemDetail responses. */
public final class ApiErrors {

	private ApiErrors() {}

	public static ResponseStatusException notFound(String entity, Object id) {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, entity + " " + id + " not found");
	}

	public static ResponseStatusException conflict(String detail) {
		return new ResponseStatusException(HttpStatus.CONFLICT, detail);
	}

	public static ResponseStatusException badRequest(String detail) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
	}

}
