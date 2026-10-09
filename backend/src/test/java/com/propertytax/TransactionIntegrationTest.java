package com.propertytax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.propertytax.assessment.Assessment;
import com.propertytax.assessment.AssessmentRepository;
import com.propertytax.payment.Payment;
import com.propertytax.payment.PaymentDtos.RecordPaymentRequest;
import com.propertytax.payment.PaymentRepository;
import com.propertytax.payment.PaymentService;
import com.propertytax.property.Property;
import com.propertytax.property.PropertyClass;
import com.propertytax.property.PropertyRepository;
import com.propertytax.taxaccount.TaxAccount;
import com.propertytax.taxaccount.TaxAccountRepository;
import com.propertytax.taxbill.TaxBill;
import com.propertytax.taxbill.TaxBillDtos.GenerateBillRequest;
import com.propertytax.taxbill.TaxBillRepository;
import com.propertytax.taxbill.TaxBillService;
import com.propertytax.taxrate.TaxRate;
import com.propertytax.taxrate.TaxRateRepository;
import com.propertytax.user.User;
import com.propertytax.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;

/**
 * Real threads, real PostgreSQL, no test transaction (each service call commits on its own).
 * Proves atomic rollback, and that row locks and constraints hold under concurrent requests.
 * Fictional data; rates use year 2030 so they don't collide with other test classes.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TransactionIntegrationTest {

	static final int THREADS = 6;
	static final AtomicInteger SEQ = new AtomicInteger();

	@Autowired TaxBillService billService;
	@Autowired PaymentService paymentService;
	@Autowired UserRepository users;
	@Autowired PropertyRepository properties;
	@Autowired AssessmentRepository assessments;
	@Autowired TaxRateRepository taxRates;
	@Autowired TaxAccountRepository taxAccounts;
	@Autowired TaxBillRepository taxBills;
	@MockitoSpyBean PaymentRepository payments; // real bean; one test injects a failure

	/** Runs every task at the same instant; returns how many succeeded. Non-HTTP-rule failures fail the test. */
	static int raceAll(List<Callable<?>> tasks) throws Exception {
		var start = new CountDownLatch(1);
		try (var pool = Executors.newFixedThreadPool(tasks.size())) {
			var futures = new ArrayList<Future<Boolean>>();
			for (var task : tasks) {
				futures.add(pool.submit(() -> {
					start.await();
					try {
						task.call();
						return true;
					} catch (ResponseStatusException | org.springframework.dao.DataIntegrityViolationException rejected) {
						return false; // expected rule/constraint rejection
					}
				}));
			}
			start.countDown();
			int ok = 0;
			for (var f : futures) {
				ok += f.get() ? 1 : 0;
			}
			return ok;
		}
	}

	long accountWith2030Inputs() {
		if (taxRates.findByTaxYearAndPropertyClass(2030, PropertyClass.INDUSTRIAL).isEmpty()) {
			taxRates.save(new TaxRate(2030, PropertyClass.INDUSTRIAL, new BigDecimal("0.0100"), "Demo rate (fictional)"));
		}
		var property = properties.save(new Property("CONC-" + SEQ.incrementAndGet(), "9 Race Rd", "Demo City",
				"L5B 9Z9", PropertyClass.INDUSTRIAL));
		assessments.save(new Assessment(property, 2030, new BigDecimal("100000.00"), LocalDate.of(2030, 1, 1)));
		return taxAccounts.save(new TaxAccount("TA-C" + SEQ.incrementAndGet(), property)).getId();
	}

	long officer() {
		return users.save(new User("conc-" + SEQ.incrementAndGet() + "@demo.test", "Race Officer", User.Role.TAX_OFFICER))
				.getId();
	}

	GenerateBillRequest finalBill() {
		return new GenerateBillRequest(2030, TaxBill.Type.FINAL, LocalDate.of(2030, 2, 1), LocalDate.of(2030, 3, 1));
	}

	RecordPaymentRequest pay(String amount, String reference, long officer) {
		return new RecordPaymentRequest(new BigDecimal(amount), LocalDate.of(2026, 1, 1), Payment.Method.COUNTER,
				reference, officer);
	}

	@Test
	void concurrentGenerationIssuesExactlyOneBill() throws Exception {
		long account = accountWith2030Inputs();
		var tasks = new ArrayList<Callable<?>>();
		for (int i = 0; i < THREADS; i++) {
			tasks.add(() -> billService.generate(account, finalBill()));
		}

		assertThat(raceAll(tasks)).isEqualTo(1);
		assertThat(taxBills.findByTaxAccountIdOrderByTaxYearDescBillTypeAsc(account)).hasSize(1);
	}

	@Test
	void concurrentPaymentsCannotJointlyOverpay() throws Exception {
		long officer = officer();
		long billId = billService.generate(accountWith2030Inputs(), finalBill()).id(); // 100,000 × 0.01 = 1,000.00
		var tasks = new ArrayList<Callable<?>>();
		for (int i = 0; i < THREADS; i++) {
			var reference = "RACE-PAY-" + SEQ.incrementAndGet();
			tasks.add(() -> paymentService.record(billId, pay("400.00", reference, officer)));
		}

		assertThat(raceAll(tasks)).isEqualTo(2); // 2 × 400 fits in 1,000; a third would overpay
		assertThat(taxBills.paidAmount(billId)).isEqualByComparingTo("800.00");
		assertThat(taxBills.findById(billId).orElseThrow().getStatus()).isEqualTo(TaxBill.Status.PARTIALLY_PAID);
	}

	@Test
	void duplicateReferenceRaceRecordsExactlyOnePayment() throws Exception {
		long officer = officer();
		long billA = billService.generate(accountWith2030Inputs(), finalBill()).id();
		long billB = billService.generate(accountWith2030Inputs(), finalBill()).id();
		var reference = "RACE-DUP-" + SEQ.incrementAndGet();

		// Different bills → different row locks, so the DB unique constraint is what stops the second insert.
		assertThat(raceAll(List.of(
				() -> paymentService.record(billA, pay("100.00", reference, officer)),
				() -> paymentService.record(billB, pay("100.00", reference, officer))))).isEqualTo(1);

		var statuses = List.of(taxBills.findById(billA).orElseThrow().getStatus(),
				taxBills.findById(billB).orElseThrow().getStatus());
		assertThat(statuses).containsExactlyInAnyOrder(TaxBill.Status.PARTIALLY_PAID, TaxBill.Status.ISSUED);
		assertThat(payments.existsByReferenceNumber(reference)).isTrue();
		assertThat(taxBills.paidAmount(billA).add(taxBills.paidAmount(billB))).isEqualByComparingTo("100.00");
	}

	@Test
	void failureAfterInsertRollsBackPaymentAndBillStatus() {
		long officer = officer();
		long billId = billService.generate(accountWith2030Inputs(), finalBill()).id();
		var reference = "ROLLBACK-" + SEQ.incrementAndGet();
		doThrow(new IllegalStateException("simulated crash after insert")).when(payments).flush();

		// The payment row is inserted (IDENTITY) and the bill status changed before flush() fails.
		assertThatThrownBy(() -> paymentService.record(billId, pay("100.00", reference, officer)))
				.hasMessageContaining("simulated crash");

		assertThat(payments.existsByReferenceNumber(reference)).isFalse();
		assertThat(taxBills.findById(billId).orElseThrow().getStatus()).isEqualTo(TaxBill.Status.ISSUED);
	}

}
