package com.propertytax.payment;

import java.util.List;

import com.propertytax.common.ApiErrors;
import com.propertytax.payment.PaymentDtos.PaymentResponse;
import com.propertytax.payment.PaymentDtos.RecordPaymentRequest;
import com.propertytax.taxbill.TaxBill;
import com.propertytax.taxbill.TaxBillRepository;
import com.propertytax.user.User;
import com.propertytax.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Simulated payment records only — never a real financial transaction.
 * Every write locks the bill row, so concurrent payments can't jointly exceed the amount owed.
 */
@Service
@Transactional
public class PaymentService {

	private final PaymentRepository payments;
	private final TaxBillRepository taxBills;
	private final UserRepository users;

	PaymentService(PaymentRepository payments, TaxBillRepository taxBills, UserRepository users) {
		this.payments = payments;
		this.taxBills = taxBills;
		this.users = users;
	}

	/** recorderId is the authenticated user, never taken from the request body. */
	public PaymentResponse record(Long taxBillId, RecordPaymentRequest r, Long recorderId) {
		var bill = taxBills.requireForUpdate(taxBillId);
		if (bill.getStatus() == TaxBill.Status.CANCELLED || bill.getStatus() == TaxBill.Status.PAID) {
			throw ApiErrors.conflict("Tax bill " + taxBillId + " is " + bill.getStatus());
		}
		if (payments.existsByReferenceNumber(r.referenceNumber())) {
			throw ApiErrors.conflict("Payment reference " + r.referenceNumber() + " was already recorded");
		}
		var recorder = users.require(recorderId);
		if (!recorder.isActive() || recorder.getRole() == User.Role.RESIDENT) {
			throw ApiErrors.badRequest("User " + recorder.getId() + " can't record payments");
		}

		var paid = taxBills.paidAmount(taxBillId);
		var outstanding = bill.getAmount().subtract(paid);
		if (r.amount().compareTo(outstanding) > 0) {
			throw ApiErrors.conflict("Payment " + r.amount() + " exceeds outstanding " + outstanding
					+ " on bill " + taxBillId + "; overpayments are not accepted");
		}

		var payment = payments.save(new Payment(bill, r.amount(), r.paymentDate(), r.method(), r.referenceNumber(), recorder));
		bill.applyPaidTotal(paid.add(r.amount()));
		payments.flush();
		return PaymentResponse.from(payment);
	}

	/** Corrections never delete: the payment becomes REVERSED and the bill status is recalculated. */
	public PaymentResponse reverse(Long paymentId) {
		// Lock the bill before loading the payment, so the payment state we check is current.
		var billId = payments.findTaxBillId(paymentId).orElseThrow(() -> ApiErrors.notFound("Payment", paymentId));
		var bill = taxBills.requireForUpdate(billId);
		var payment = payments.require(paymentId);
		if (payment.getStatus() != Payment.Status.RECORDED) {
			throw ApiErrors.conflict("Payment " + paymentId + " is already " + payment.getStatus());
		}
		var paid = taxBills.paidAmount(billId);
		payment.reverse();
		bill.applyPaidTotal(paid.subtract(payment.getAmount()));
		payments.flush();
		return PaymentResponse.from(payment);
	}

	@Transactional(readOnly = true)
	public List<PaymentResponse> forBill(Long taxBillId) {
		taxBills.require(taxBillId);
		return payments.findByTaxBillIdOrderByPaymentDateAscIdAsc(taxBillId).stream().map(PaymentResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public PaymentResponse get(Long id) {
		return PaymentResponse.from(payments.require(id));
	}

}
