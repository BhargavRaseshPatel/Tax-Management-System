package com.propertytax.payment;

import java.util.List;
import java.util.Optional;

import com.propertytax.common.ApiErrors;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

	List<Payment> findByTaxBillIdOrderByPaymentDateAscIdAsc(Long taxBillId);

	boolean existsByReferenceNumber(String referenceNumber);

	@Query("select p.taxBill.id from Payment p where p.id = :paymentId")
	Optional<Long> findTaxBillId(Long paymentId);

	default Payment require(Long id) {
		return findById(id).orElseThrow(() -> ApiErrors.notFound("Payment", id));
	}

}
