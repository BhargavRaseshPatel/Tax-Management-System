package com.propertytax.payment;

import java.util.List;

import com.propertytax.payment.PaymentDtos.PaymentResponse;
import com.propertytax.taxbill.TaxBillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side only. Recording/reversing simulated payments arrives in Stage 5. */
@Service
@Transactional(readOnly = true)
public class PaymentService {

	private final PaymentRepository payments;
	private final TaxBillRepository taxBills;

	PaymentService(PaymentRepository payments, TaxBillRepository taxBills) {
		this.payments = payments;
		this.taxBills = taxBills;
	}

	public List<PaymentResponse> forBill(Long taxBillId) {
		taxBills.require(taxBillId);
		return payments.findByTaxBillIdOrderByPaymentDateAscIdAsc(taxBillId).stream().map(PaymentResponse::from).toList();
	}

	public PaymentResponse get(Long id) {
		return PaymentResponse.from(payments.require(id));
	}

}
