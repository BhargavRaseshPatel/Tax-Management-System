package com.propertytax.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import com.propertytax.payment.PaymentDtos.RecordPaymentRequest;
import com.propertytax.taxbill.TaxBill;
import com.propertytax.taxbill.TaxBillRepository;
import com.propertytax.user.User;
import com.propertytax.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** Payment rules with mocked repositories. Every rejection must happen before anything is saved. */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

	@Mock PaymentRepository payments;
	@Mock TaxBillRepository taxBills;
	@Mock UserRepository users;
	@InjectMocks PaymentService service;

	TaxBill bill;
	User officer;

	@BeforeEach
	void setUp() {
		bill = new TaxBill("B-1", null, null, null, 2026, TaxBill.Type.FINAL, new BigDecimal("1000.00"),
				LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));
		ReflectionTestUtils.setField(bill, "id", 9L);
		officer = new User("officer@demo.test", "Olivia Officer", User.Role.TAX_OFFICER);
		ReflectionTestUtils.setField(officer, "id", 1L);
	}

	static RecordPaymentRequest pay(String amount) {
		return new RecordPaymentRequest(new BigDecimal(amount), LocalDate.of(2026, 6, 15), Payment.Method.CHEQUE, "REF-1");
	}

	void assertRejected(Runnable call, HttpStatus status) {
		assertThat(catchThrowable(call::run)).isInstanceOfSatisfying(ResponseStatusException.class,
				e -> assertThat(e.getStatusCode()).isEqualTo(status));
		verify(payments, never()).save(any());
	}

	@Test
	void partialPaymentIsSavedAndBillBecomesPartiallyPaid() {
		when(taxBills.requireForUpdate(9L)).thenReturn(bill);
		when(users.require(1L)).thenReturn(officer);
		when(taxBills.paidAmount(9L)).thenReturn(new BigDecimal("200.00"));
		when(payments.save(any())).thenAnswer(inv -> inv.getArgument(0));

		var result = service.record(9L, pay("300.00"), 1L);

		assertThat(result.status()).isEqualTo(Payment.Status.RECORDED);
		assertThat(bill.getStatus()).isEqualTo(TaxBill.Status.PARTIALLY_PAID);
	}

	@Test
	void paymentOfExactOutstandingMarksBillPaid() {
		when(taxBills.requireForUpdate(9L)).thenReturn(bill);
		when(users.require(1L)).thenReturn(officer);
		when(taxBills.paidAmount(9L)).thenReturn(new BigDecimal("200.00"));
		when(payments.save(any())).thenAnswer(inv -> inv.getArgument(0));

		service.record(9L, pay("800.00"), 1L);

		assertThat(bill.getStatus()).isEqualTo(TaxBill.Status.PAID);
	}

	@Test
	void overpaymentByOneCentIsRejected() {
		when(taxBills.requireForUpdate(9L)).thenReturn(bill);
		when(users.require(1L)).thenReturn(officer);
		when(taxBills.paidAmount(9L)).thenReturn(new BigDecimal("200.00"));

		assertRejected(() -> service.record(9L, pay("800.01"), 1L), HttpStatus.CONFLICT);
		assertThat(bill.getStatus()).isEqualTo(TaxBill.Status.ISSUED);
	}

	@Test
	void duplicateReferenceIsRejected() {
		when(taxBills.requireForUpdate(9L)).thenReturn(bill);
		when(payments.existsByReferenceNumber("REF-1")).thenReturn(true);

		assertRejected(() -> service.record(9L, pay("10.00"), 1L), HttpStatus.CONFLICT);
	}

	@Test
	void paidOrCancelledBillAcceptsNoPayment() {
		when(taxBills.requireForUpdate(9L)).thenReturn(bill);
		bill.applyPaidTotal(new BigDecimal("1000.00"));
		assertRejected(() -> service.record(9L, pay("1.00"), 1L), HttpStatus.CONFLICT);

		bill.cancel();
		assertRejected(() -> service.record(9L, pay("1.00"), 1L), HttpStatus.CONFLICT);
	}

	@Test
	void residentOrInactiveUserCannotRecordPayments() {
		when(taxBills.requireForUpdate(9L)).thenReturn(bill);
		var resident = new User("resident@demo.test", "Riley Resident", User.Role.RESIDENT);
		ReflectionTestUtils.setField(resident, "id", 1L);
		when(users.require(1L)).thenReturn(resident);
		assertRejected(() -> service.record(9L, pay("10.00"), 1L), HttpStatus.BAD_REQUEST);

		officer.update("Olivia Officer", User.Role.TAX_OFFICER, false);
		when(users.require(1L)).thenReturn(officer);
		assertRejected(() -> service.record(9L, pay("10.00"), 1L), HttpStatus.BAD_REQUEST);
	}

	@Test
	void reversalRestoresBillStatusAndCannotBeRepeated() {
		var payment = new Payment(bill, new BigDecimal("1000.00"), LocalDate.of(2026, 6, 15), Payment.Method.CHEQUE,
				"REF-1", officer);
		bill.applyPaidTotal(new BigDecimal("1000.00"));
		when(payments.findTaxBillId(5L)).thenReturn(Optional.of(9L));
		when(taxBills.requireForUpdate(9L)).thenReturn(bill);
		when(payments.require(5L)).thenReturn(payment);
		when(taxBills.paidAmount(9L)).thenReturn(new BigDecimal("1000.00"));

		assertThat(service.reverse(5L).status()).isEqualTo(Payment.Status.REVERSED);
		assertThat(bill.getStatus()).isEqualTo(TaxBill.Status.ISSUED);

		assertThat(catchThrowable(() -> service.reverse(5L))).isInstanceOf(ResponseStatusException.class);
	}

}
