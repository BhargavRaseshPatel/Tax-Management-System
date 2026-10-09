package com.propertytax.payment;

import java.util.List;

import com.propertytax.payment.PaymentDtos.PaymentResponse;
import com.propertytax.payment.PaymentDtos.RecordPaymentRequest;
import com.propertytax.security.SecurityConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "7. Payments", description = "Simulated payment records only, never real transactions")
@RestController
class PaymentController {

	private final PaymentService service;

	PaymentController(PaymentService service) {
		this.service = service;
	}

	@Operation(summary = "Record a simulated payment (≤ outstanding, unique reference)")
	@PostMapping("/api/tax-bills/{taxBillId}/payments")
	@ResponseStatus(HttpStatus.CREATED)
	PaymentResponse record(@PathVariable Long taxBillId, @Valid @RequestBody RecordPaymentRequest request,
			Authentication auth) {
		return service.record(taxBillId, request, SecurityConfig.userId(auth));
	}

	@GetMapping("/api/tax-bills/{taxBillId}/payments")
	List<PaymentResponse> forBill(@PathVariable Long taxBillId) {
		return service.forBill(taxBillId);
	}

	@GetMapping("/api/payments/{id}")
	PaymentResponse get(@PathVariable Long id) {
		return service.get(id);
	}

	@Operation(summary = "Reverse a recorded payment (never deleted); bill status is recalculated")
	@PostMapping("/api/payments/{id}/reverse")
	PaymentResponse reverse(@PathVariable Long id) {
		return service.reverse(id);
	}

}
