package com.propertytax.property;

import java.util.List;

import com.propertytax.property.PropertyDtos.OwnershipRequest;
import com.propertytax.property.PropertyDtos.OwnershipResponse;
import com.propertytax.property.PropertyDtos.PropertyRequest;
import com.propertytax.property.PropertyDtos.PropertyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Properties have financial history, so there is no delete endpoint. */
@Tag(name = "2. Properties & ownership")
@RestController
class PropertyController {

	private final PropertyService service;

	PropertyController(PropertyService service) {
		this.service = service;
	}

	@PostMapping("/api/properties")
	@ResponseStatus(HttpStatus.CREATED)
	PropertyResponse create(@Valid @RequestBody PropertyRequest request) {
		return service.create(request);
	}

	@Operation(summary = "List properties; q filters by roll number or street address")
	@GetMapping("/api/properties")
	PagedModel<PropertyResponse> search(@RequestParam(required = false) String q,
			@ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
		return service.search(q, pageable);
	}

	@GetMapping("/api/properties/{id}")
	PropertyResponse get(@PathVariable Long id) {
		return service.get(id);
	}

	@PutMapping("/api/properties/{id}")
	PropertyResponse update(@PathVariable Long id, @Valid @RequestBody PropertyRequest request) {
		return service.update(id, request);
	}

	@GetMapping("/api/properties/{id}/owners")
	List<OwnershipResponse> owners(@PathVariable Long id) {
		return service.owners(id);
	}

	@Operation(summary = "Assign an owner; total shares per property may not exceed 100%")
	@PostMapping("/api/properties/{id}/owners")
	@ResponseStatus(HttpStatus.CREATED)
	OwnershipResponse assignOwner(@PathVariable Long id, @Valid @RequestBody OwnershipRequest request) {
		return service.assignOwner(id, request);
	}

	@DeleteMapping("/api/properties/{id}/owners/{ownerId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void removeOwner(@PathVariable Long id, @PathVariable Long ownerId) {
		service.removeOwner(id, ownerId);
	}

	@GetMapping("/api/owners/{ownerId}/properties")
	List<PropertyResponse> propertiesOfOwner(@PathVariable Long ownerId) {
		return service.propertiesOfOwner(ownerId);
	}

}
