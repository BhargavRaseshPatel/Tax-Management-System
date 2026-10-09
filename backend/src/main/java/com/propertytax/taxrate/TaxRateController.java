package com.propertytax.taxrate;

import java.util.List;

import com.propertytax.taxrate.TaxRateDtos.TaxRateRequest;
import com.propertytax.taxrate.TaxRateDtos.TaxRateResponse;
import com.propertytax.taxrate.TaxRateDtos.TaxRateUpdateRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

@Tag(name = "4. Tax rates", description = "Fictional demo rates, not official municipal rates")
@RestController
@RequestMapping("/api/tax-rates")
class TaxRateController {

	private final TaxRateService service;

	TaxRateController(TaxRateService service) {
		this.service = service;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	TaxRateResponse create(@Valid @RequestBody TaxRateRequest request) {
		return service.create(request);
	}

	@GetMapping
	List<TaxRateResponse> list(@RequestParam(required = false) Integer taxYear) {
		return service.list(taxYear);
	}

	@GetMapping("/{id}")
	TaxRateResponse get(@PathVariable Long id) {
		return service.get(id);
	}

	@PutMapping("/{id}")
	TaxRateResponse update(@PathVariable Long id, @Valid @RequestBody TaxRateUpdateRequest request) {
		return service.update(id, request);
	}

}
