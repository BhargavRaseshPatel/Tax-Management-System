package com.propertytax.owner;

import com.propertytax.owner.OwnerDtos.OwnerRequest;
import com.propertytax.owner.OwnerDtos.OwnerResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "1. Owners")
@RestController
@RequestMapping("/api/owners")
class OwnerController {

	private final OwnerService service;

	OwnerController(OwnerService service) {
		this.service = service;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	OwnerResponse create(@Valid @RequestBody OwnerRequest request) {
		return service.create(request);
	}

	@GetMapping
	PagedModel<OwnerResponse> list(@RequestParam(required = false) String name,
			@ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
		return service.list(name, pageable);
	}

	@GetMapping("/{id}")
	OwnerResponse get(@PathVariable Long id) {
		return service.get(id);
	}

	@PutMapping("/{id}")
	OwnerResponse update(@PathVariable Long id, @Valid @RequestBody OwnerRequest request) {
		return service.update(id, request);
	}

}
