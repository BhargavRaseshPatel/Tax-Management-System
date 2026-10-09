package com.propertytax.taxaccount;

import com.propertytax.taxaccount.TaxAccountDtos.TaxAccountRequest;
import com.propertytax.taxaccount.TaxAccountDtos.TaxAccountResponse;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "5. Tax accounts")
@RestController
class TaxAccountController {

	private final TaxAccountService service;

	TaxAccountController(TaxAccountService service) {
		this.service = service;
	}

	@Operation(summary = "Open the tax account for a property (one per property)")
	@PostMapping("/api/tax-accounts")
	@ResponseStatus(HttpStatus.CREATED)
	TaxAccountResponse open(@Valid @RequestBody TaxAccountRequest request) {
		return service.open(request);
	}

	@GetMapping("/api/tax-accounts")
	PagedModel<TaxAccountResponse> list(@ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
		return service.list(pageable);
	}

	@GetMapping("/api/tax-accounts/{id}")
	TaxAccountResponse get(@PathVariable Long id) {
		return service.get(id);
	}

	@GetMapping("/api/properties/{propertyId}/tax-account")
	TaxAccountResponse forProperty(@PathVariable Long propertyId) {
		return service.forProperty(propertyId);
	}

}
