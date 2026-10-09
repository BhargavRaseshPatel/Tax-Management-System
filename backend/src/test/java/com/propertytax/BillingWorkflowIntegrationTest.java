package com.propertytax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.util.concurrent.atomic.AtomicInteger;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Stage 5 business rules end to end over HTTP + PostgreSQL. Fictional data and rates:
 * 2025: 620,000 × 0.0072 = 4,464.00 → 2026 interim = 2,232.00
 * 2026: 650,000 × 0.0075 = 4,875.00 → 2026 final = 4,875.00 − 2,232.00 = 2,643.00
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class BillingWorkflowIntegrationTest {

	static final AtomicInteger SEQ = new AtomicInteger();

	@Autowired
	MockMvcTester mvc;

	// ---- helpers -------------------------------------------------------------------------------------------

	MvcTestResult post(String uri, String json) {
		return mvc.post().uri(uri).contentType(APPLICATION_JSON).content(json).exchange();
	}

	MvcTestResult put(String uri, String json) {
		return mvc.put().uri(uri).contentType(APPLICATION_JSON).content(json).exchange();
	}

	static <T> T read(MvcTestResult result, String path) {
		try {
			return JsonPath.read(result.getResponse().getContentAsString(), path);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	static long id(MvcTestResult result) {
		return ((Number) read(result, "$.id")).longValue();
	}

	/** Rates are unique per (year, class) across the shared test DB, so create each one only once. */
	void ensureRate(int year, String rate) {
		var existing = mvc.get().uri("/api/tax-rates?taxYear=" + year).exchange();
		if (((java.util.List<?>) read(existing, "$[?(@.propertyClass == 'RESIDENTIAL')]")).isEmpty()) {
			assertThat(post("/api/tax-rates", """
					{"taxYear":%d,"propertyClass":"RESIDENTIAL","rate":%s,"description":"Demo rate (fictional)"}"""
					.formatted(year, rate))).hasStatus(CREATED);
		}
	}

	long user(String role) {
		return id(post("/api/users", """
				{"email":"billing-%d@demo.test","fullName":"Billing Test","role":"%s"}""".formatted(SEQ.incrementAndGet(), role)));
	}

	record Fixture(long accountId, long assessment2026Id) {}

	/** Property with a 2026 assessment (and optionally 2025), plus its tax account. */
	Fixture account(boolean withPriorYear) {
		ensureRate(2025, "0.0072");
		ensureRate(2026, "0.0075");
		long property = id(post("/api/properties", """
				{"rollNumber":"BILL-%04d","streetAddress":"5 Ledger Lane","city":"Demo City","postalCode":"L5B 4D4","propertyClass":"RESIDENTIAL"}"""
				.formatted(SEQ.incrementAndGet())));
		if (withPriorYear) {
			post("/api/properties/" + property + "/assessments", """
					{"taxYear":2025,"assessedValue":620000.00,"assessmentDate":"2025-01-01"}""");
		}
		long assessment2026 = id(post("/api/properties/" + property + "/assessments", """
				{"taxYear":2026,"assessedValue":650000.00,"assessmentDate":"2026-01-01"}"""));
		long account = id(post("/api/tax-accounts", "{\"propertyId\":" + property + "}"));
		return new Fixture(account, assessment2026);
	}

	MvcTestResult bill(long accountId, String type) {
		return post("/api/tax-accounts/" + accountId + "/bills", """
				{"taxYear":2026,"billType":"%s","issueDate":"2026-02-01","dueDate":"2026-03-01"}""".formatted(type));
	}

	MvcTestResult pay(long billId, String amount, String reference, long recorder) {
		return post("/api/tax-bills/" + billId + "/payments", """
				{"amount":%s,"paymentDate":"2026-06-15","method":"CHEQUE","referenceNumber":"%s","recordedByUserId":%d}"""
				.formatted(amount, reference, recorder));
	}

	String ref() {
		return "REF-BILL-" + SEQ.incrementAndGet();
	}

	// ---- tests ---------------------------------------------------------------------------------------------

	@Test
	void interimIsHalfOfPriorYearAndFinalIsAnnualMinusInterim() {
		var f = account(true);

		var interim = bill(f.accountId(), "INTERIM");
		assertThat(interim).hasStatus(CREATED).bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.amount").isEqualTo(2232.00);
			assertThat(json).extractingPath("$.status").isEqualTo("ISSUED");
			assertThat(json).extractingPath("$.billNumber").asString().endsWith("-2026-I-1");
		});
		assertThat(bill(f.accountId(), "FINAL")).hasStatus(CREATED)
				.bodyJson().extractingPath("$.amount").isEqualTo(2643.00);

		assertThat(bill(f.accountId(), "INTERIM")).hasStatus(CONFLICT);
		assertThat(bill(f.accountId(), "FINAL")).hasStatus(CONFLICT);
		assertThat(mvc.get().uri("/api/tax-accounts/" + f.accountId() + "/bills"))
				.bodyJson().extractingPath("$.length()").isEqualTo(2);
		assertThat(mvc.get().uri("/api/tax-accounts/" + f.accountId() + "/balance")).hasStatus(OK)
				.bodyJson().satisfies(json -> {
					assertThat(json).extractingPath("$.totalBilled").isEqualTo(4875.00);
					assertThat(json).extractingPath("$.outstanding").isEqualTo(4875.00);
				});
	}

	@Test
	void interimNeedsPriorYearAssessmentAndMustPrecedeFinal() {
		var f = account(false);

		assertThat(bill(f.accountId(), "INTERIM")).hasStatus(CONFLICT)
				.bodyJson().extractingPath("$.detail").asString().contains("No 2025 assessment");
		assertThat(bill(f.accountId(), "FINAL")).hasStatus(CREATED)
				.bodyJson().extractingPath("$.amount").isEqualTo(4875.00);
		assertThat(bill(f.accountId(), "INTERIM")).hasStatus(CONFLICT)
				.bodyJson().extractingPath("$.detail").asString().contains("after the final");
	}

	@Test
	void paymentsDriveBillStatusAndRejectOverpaymentAndDuplicates() {
		var f = account(false);
		long officer = user("TAX_OFFICER");
		long resident = user("RESIDENT");
		long billId = id(bill(f.accountId(), "FINAL")); // 4,875.00
		var firstRef = ref();

		var first = pay(billId, "1000.00", firstRef, officer);
		assertThat(first).hasStatus(CREATED).bodyJson().extractingPath("$.status").isEqualTo("RECORDED");
		assertThat(mvc.get().uri("/api/tax-bills/" + billId)).bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.status").isEqualTo("PARTIALLY_PAID");
			assertThat(json).extractingPath("$.outstandingAmount").isEqualTo(3875.00);
		});

		assertThat(pay(billId, "3875.01", ref(), officer)).hasStatus(CONFLICT)
				.bodyJson().extractingPath("$.detail").asString().contains("exceeds outstanding 3875.00");
		assertThat(pay(billId, "10.00", firstRef, officer)).hasStatus(CONFLICT);   // duplicate reference
		assertThat(pay(billId, "10.00", ref(), resident)).hasStatus(BAD_REQUEST);  // residents can't record
		assertThat(mvc.get().uri("/api/tax-bills/" + billId + "/payments"))
				.bodyJson().extractingPath("$.length()").isEqualTo(1); // rejected attempts left nothing behind

		assertThat(pay(billId, "3875.00", ref(), officer)).hasStatus(CREATED);
		assertThat(mvc.get().uri("/api/tax-bills/" + billId)).bodyJson().extractingPath("$.status").isEqualTo("PAID");
		assertThat(pay(billId, "1.00", ref(), officer)).hasStatus(CONFLICT); // bill is PAID
		assertThat(mvc.get().uri("/api/tax-accounts/" + f.accountId() + "/balance"))
				.bodyJson().extractingPath("$.outstanding").isEqualTo(0.00);

		long firstId = id(first);
		assertThat(mvc.get().uri("/api/payments/" + firstId)).hasStatusOk()
				.bodyJson().extractingPath("$.referenceNumber").isEqualTo(firstRef);
		assertThat(post("/api/payments/" + firstId + "/reverse", "")).hasStatus(OK)
				.bodyJson().extractingPath("$.status").isEqualTo("REVERSED");
		assertThat(post("/api/payments/" + firstId + "/reverse", "")).hasStatus(CONFLICT);
		assertThat(mvc.get().uri("/api/tax-bills/" + billId)).bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.status").isEqualTo("PARTIALLY_PAID");
			assertThat(json).extractingPath("$.outstandingAmount").isEqualTo(1000.00);
		});
		assertThat(post("/api/tax-bills/" + billId + "/cancel", "")).hasStatus(CONFLICT); // has recorded payments
	}

	@Test
	void billedAssessmentIsFrozenUntilBillIsCancelledThenBillCanBeReissued() {
		var f = account(false);
		long billId = id(bill(f.accountId(), "FINAL"));
		var raised = """
				{"taxYear":2026,"assessedValue":700000.00,"assessmentDate":"2026-01-01"}""";

		assertThat(put("/api/assessments/" + f.assessment2026Id(), raised)).hasStatus(CONFLICT);

		assertThat(post("/api/tax-bills/" + billId + "/cancel", "")).hasStatus(OK)
				.bodyJson().extractingPath("$.status").isEqualTo("CANCELLED");
		assertThat(put("/api/assessments/" + f.assessment2026Id(), raised)).hasStatus(OK);

		assertThat(bill(f.accountId(), "FINAL")).hasStatus(CREATED).bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.amount").isEqualTo(5250.00); // 700,000 × 0.0075
			assertThat(json).extractingPath("$.billNumber").asString().endsWith("-2026-F-2");
		});
		assertThat(mvc.get().uri("/api/tax-accounts/" + f.accountId() + "/balance"))
				.bodyJson().extractingPath("$.totalBilled").isEqualTo(5250.00); // cancelled bill excluded
	}

}
