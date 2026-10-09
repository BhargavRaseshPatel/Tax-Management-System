package com.propertytax.payment;

import java.util.List;

import com.propertytax.common.ApiErrors;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

	List<Payment> findByTaxBillIdOrderByPaymentDateAscIdAsc(Long taxBillId);

	default Payment require(Long id) {
		return findById(id).orElseThrow(() -> ApiErrors.notFound("Payment", id));
	}

}
