package com.propertytax.taxbill;

import java.util.List;

import com.propertytax.taxbill.TaxBillDtos.AccountBalanceResponse;
import com.propertytax.taxbill.TaxBillDtos.GenerateBillRequest;
import com.propertytax.taxbill.TaxBillDtos.TaxBillResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "6. Tax bills")
@RestController
class TaxBillController {

	private final TaxBillService service;

	TaxBillController(TaxBillService service) {
		this.service = service;
	}

	@Operation(summary = "Generate an INTERIM (50% of prior-year tax) or FINAL (annual tax minus interim) bill")
	@PostMapping("/api/tax-accounts/{taxAccountId}/bills")
	@ResponseStatus(HttpStatus.CREATED)
	TaxBillResponse generate(@PathVariable Long taxAccountId, @Valid @RequestBody GenerateBillRequest request) {
		return service.generate(taxAccountId, request);
	}

	@GetMapping("/api/tax-accounts/{taxAccountId}/bills")
	List<TaxBillResponse> forAccount(@PathVariable Long taxAccountId) {
		return service.forAccount(taxAccountId);
	}

	@Operation(summary = "Outstanding balance: live bills minus recorded payments")
	@GetMapping("/api/tax-accounts/{taxAccountId}/balance")
	AccountBalanceResponse balance(@PathVariable Long taxAccountId) {
		return service.balance(taxAccountId);
	}

	@GetMapping("/api/tax-bills/{id}")
	TaxBillResponse get(@PathVariable Long id) {
		return service.get(id);
	}

	@Operation(summary = "Cancel a bill with no recorded payments; a new bill of that type can then be issued")
	@PostMapping("/api/tax-bills/{id}/cancel")
	TaxBillResponse cancel(@PathVariable Long id) {
		return service.cancel(id);
	}

}
