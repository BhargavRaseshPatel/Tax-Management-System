package com.propertytax.taxbill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.propertytax.assessment.Assessment;
import com.propertytax.assessment.AssessmentRepository;
import com.propertytax.property.Property;
import com.propertytax.property.PropertyClass;
import com.propertytax.taxaccount.TaxAccount;
import com.propertytax.taxaccount.TaxAccountRepository;
import com.propertytax.taxbill.TaxBillDtos.GenerateBillRequest;
import com.propertytax.taxrate.TaxRate;
import com.propertytax.taxrate.TaxRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

/** Bill-generation rules with mocked repositories: no Spring context, no database. Fictional values. */
@ExtendWith(MockitoExtension.class)
class TaxBillServiceTest {

	@Mock TaxBillRepository taxBills;
	@Mock TaxAccountRepository taxAccounts;
	@Mock AssessmentRepository assessments;
	@Mock TaxRateRepository taxRates;
	@InjectMocks TaxBillService service;

	Property property;
	TaxAccount account;

	static <T> T withId(T entity, long id) {
		ReflectionTestUtils.setField(entity, "id", id);
		return entity;
	}

	static GenerateBillRequest request(TaxBill.Type type) {
		return new GenerateBillRequest(2026, type, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1));
	}

	static void assertStatus(Throwable thrown, HttpStatus status) {
		assertThat(thrown).isInstanceOfSatisfying(ResponseStatusException.class,
				e -> assertThat(e.getStatusCode()).isEqualTo(status));
	}

	@BeforeEach
	void setUp() {
		property = withId(new Property("RN-1", "1 Demo St", "Demo City", "L5B 1A1", PropertyClass.RESIDENTIAL), 7);
		account = withId(new TaxAccount("TA-00000007", property), 3);
	}

	void stubInputs(int year, String value, String rate) {
		when(assessments.findByPropertyIdAndTaxYear(7L, year))
				.thenReturn(Optional.of(new Assessment(property, year, new BigDecimal(value), LocalDate.of(year, 1, 1))));
		when(taxRates.findByTaxYearAndPropertyClass(year, PropertyClass.RESIDENTIAL))
				.thenReturn(Optional.of(new TaxRate(year, PropertyClass.RESIDENTIAL, new BigDecimal(rate), "demo")));
	}

	TaxBill interimOf(String amount) {
		return new TaxBill("X", account, null, null, 2026, TaxBill.Type.INTERIM, new BigDecimal(amount),
				LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1));
	}

	@Test
	void interimIsHalfOfPreviousYearsTaxAndUsesPreviousYearsInputs() {
		when(taxAccounts.findByIdForUpdate(3L)).thenReturn(Optional.of(account));
		stubInputs(2025, "620000.00", "0.0072");
		when(taxBills.save(any())).thenAnswer(inv -> inv.getArgument(0));

		var bill = service.generate(3L, request(TaxBill.Type.INTERIM));

		assertThat(bill.amount()).isEqualByComparingTo("2232.00");
		assertThat(bill.billNumber()).isEqualTo("TA-00000007-2026-I-1");
		assertThat(bill.status()).isEqualTo(TaxBill.Status.ISSUED);
	}

	@Test
	void finalSubtractsTheLiveInterim() {
		when(taxAccounts.findByIdForUpdate(3L)).thenReturn(Optional.of(account));
		when(taxBills.findByTaxAccountIdAndTaxYearAndStatusNot(3L, 2026, TaxBill.Status.CANCELLED))
				.thenReturn(List.of(interimOf("2232.00")));
		stubInputs(2026, "650000.00", "0.0075");
		when(taxBills.save(any())).thenAnswer(inv -> inv.getArgument(0));

		assertThat(service.generate(3L, request(TaxBill.Type.FINAL)).amount()).isEqualByComparingTo("2643.00");
	}

	@Test
	void finalBelowInterimIsRejectedAndNothingIsSaved() {
		when(taxAccounts.findByIdForUpdate(3L)).thenReturn(Optional.of(account));
		when(taxBills.findByTaxAccountIdAndTaxYearAndStatusNot(3L, 2026, TaxBill.Status.CANCELLED))
				.thenReturn(List.of(interimOf("2232.00")));
		stubInputs(2026, "100000.00", "0.0075"); // annual 750.00 < interim

		assertStatus(catchThrowable(() -> service.generate(3L, request(TaxBill.Type.FINAL))), HttpStatus.CONFLICT);
		verify(taxBills, never()).save(any());
	}

	@Test
	void secondBillOfSameTypeIsRejected() {
		when(taxAccounts.findByIdForUpdate(3L)).thenReturn(Optional.of(account));
		when(taxBills.findByTaxAccountIdAndTaxYearAndStatusNot(3L, 2026, TaxBill.Status.CANCELLED))
				.thenReturn(List.of(interimOf("2232.00")));

		assertStatus(catchThrowable(() -> service.generate(3L, request(TaxBill.Type.INTERIM))), HttpStatus.CONFLICT);
		verifyNoInteractions(assessments, taxRates);
	}

	@Test
	void closedAccountIsRejected() {
		ReflectionTestUtils.setField(account, "status", TaxAccount.Status.CLOSED);
		when(taxAccounts.findByIdForUpdate(3L)).thenReturn(Optional.of(account));

		assertStatus(catchThrowable(() -> service.generate(3L, request(TaxBill.Type.FINAL))), HttpStatus.CONFLICT);
		verify(taxBills, never()).save(any());
	}

	@Test
	void dueDateBeforeIssueDateIsRejectedBeforeAnyDatabaseWork() {
		var bad = new GenerateBillRequest(2026, TaxBill.Type.FINAL, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 2, 1));

		assertStatus(catchThrowable(() -> service.generate(3L, bad)), HttpStatus.BAD_REQUEST);
		verifyNoInteractions(taxAccounts, taxBills, assessments, taxRates);
	}

	@Test
	void missingRateIsReportedAsConflict() {
		when(taxAccounts.findByIdForUpdate(3L)).thenReturn(Optional.of(account));
		when(assessments.findByPropertyIdAndTaxYear(7L, 2026))
				.thenReturn(Optional.of(new Assessment(property, 2026, new BigDecimal("1.00"), LocalDate.of(2026, 1, 1))));
		when(taxRates.findByTaxYearAndPropertyClass(2026, PropertyClass.RESIDENTIAL)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.generate(3L, request(TaxBill.Type.FINAL)))
				.hasMessageContaining("No 2026 tax rate for RESIDENTIAL");
	}

	static Throwable catchThrowable(Runnable r) {
		return org.assertj.core.api.Assertions.catchThrowable(r::run);
	}

}
