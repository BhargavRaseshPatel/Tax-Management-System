package com.propertytax.payment;

import java.util.List;

import com.propertytax.payment.PaymentDtos.PaymentResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "7. Payments", description = "Simulated payment records only, never real transactions")
@RestController
class PaymentController {

	private final PaymentService service;

	PaymentController(PaymentService service) {
		this.service = service;
	}

	@GetMapping("/api/tax-bills/{taxBillId}/payments")
	List<PaymentResponse> forBill(@PathVariable Long taxBillId) {
		return service.forBill(taxBillId);
	}

	@GetMapping("/api/payments/{id}")
	PaymentResponse get(@PathVariable Long id) {
		return service.get(id);
	}

}
