package com.propertytax.taxbill;

import java.util.List;

import com.propertytax.taxbill.TaxBillDtos.TaxBillResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "6. Tax bills")
@RestController
class TaxBillController {

	private final TaxBillService service;

	TaxBillController(TaxBillService service) {
		this.service = service;
	}

	@GetMapping("/api/tax-accounts/{taxAccountId}/bills")
	List<TaxBillResponse> forAccount(@PathVariable Long taxAccountId) {
		return service.forAccount(taxAccountId);
	}

	@GetMapping("/api/tax-bills/{id}")
	TaxBillResponse get(@PathVariable Long id) {
		return service.get(id);
	}

}
