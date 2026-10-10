package com.propertytax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;

import com.jayway.jsonpath.JsonPath;
import com.propertytax.assessment.Assessment;
import com.propertytax.assessment.AssessmentRepository;
import com.propertytax.document.DocumentStore;
import com.propertytax.payment.Payment;
import com.propertytax.payment.PaymentRepository;
import com.propertytax.property.Property;
import com.propertytax.property.PropertyClass;
import com.propertytax.property.PropertyRepository;
import com.propertytax.report.ReportGenerator;
import com.propertytax.report.ReportJob;
import com.propertytax.report.ReportJobStore;
import com.propertytax.security.DevTokenIssuer;
import com.propertytax.taxaccount.TaxAccount;
import com.propertytax.taxaccount.TaxAccountRepository;
import com.propertytax.taxbill.TaxBill;
import com.propertytax.taxbill.TaxBillRepository;
import com.propertytax.taxrate.TaxRate;
import com.propertytax.taxrate.TaxRateRepository;
import com.propertytax.user.User;
import com.propertytax.user.UserRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Stage 13 end to end with real emulators: API → DynamoDB job → Step Functions → worker → PostgreSQL query → S3 →
 * DynamoDB status → download. Includes the failure path (retries, then FAILED). Fictional data, tax year 2034.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportWorkflowIntegrationTest {

	static final Duration PATIENCE = Duration.ofSeconds(60);

	@Autowired MockMvcTester mvc;
	@Autowired DevTokenIssuer issuer;
	@Autowired ReportJobStore jobs;
	@Autowired DocumentStore documents;
	@Autowired UserRepository users;
	@Autowired PropertyRepository properties;
	@Autowired AssessmentRepository assessments;
	@Autowired TaxRateRepository taxRates;
	@Autowired TaxAccountRepository taxAccounts;
	@Autowired TaxBillRepository taxBills;
	@Autowired PaymentRepository payments;
	@MockitoSpyBean ReportGenerator generator; // real generator; one test makes it fail

	String officer;
	String resident;

	@BeforeAll
	void setUp() {
		officer = TestAuth.bearer(users, issuer, User.Role.TAX_OFFICER);
		resident = TestAuth.bearer(users, issuer, User.Role.RESIDENT);
		var officerUser = users.findByEmail("test-tax-officer@demo.test").orElseThrow();
		var property = properties.save(new Property("RPT-0001", "5 Ledger Lane, Unit 2", "Demo City", "L5B 8H8", PropertyClass.RESIDENTIAL));
		var assessment = assessments.save(new Assessment(property, 2034, new BigDecimal("400000.00"), LocalDate.of(2034, 1, 1)));
		var rate = taxRates.save(new TaxRate(2034, PropertyClass.RESIDENTIAL, new BigDecimal("0.0025"), "Demo (fictional)"));
		var account = taxAccounts.save(new TaxAccount("TA-RPT-1", property));
		var bill = taxBills.save(new TaxBill("TA-RPT-1-2034-F-1", account, assessment, rate, 2034, TaxBill.Type.FINAL,
				new BigDecimal("1000.00"), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1)));
		bill.applyPaidTotal(new BigDecimal("250.00"));
		taxBills.save(bill);
		payments.save(new Payment(bill, new BigDecimal("250.00"), LocalDate.of(2026, 6, 2), Payment.Method.CHEQUE, "RPT-PAY-1", officerUser));
	}

	MvcTestResult request(String token, String json) {
		var post = mvc.post().uri("/api/reports").contentType(APPLICATION_JSON).content(json);
		return (token == null ? post : post.header(AUTHORIZATION, token)).exchange();
	}

	MvcTestResult get(String uri, String token) {
		return mvc.get().uri(uri).header(AUTHORIZATION, token).exchange();
	}

	static String field(MvcTestResult result, String path) throws Exception {
		return JsonPath.read(result.getResponse().getContentAsString(), path);
	}

	String statusOf(String jobId) throws Exception {
		return field(get("/api/reports/" + jobId, officer), "$.status");
	}

	@Test
	void requestReturnsAtOnceAndTheWorkerDeliversTheReportToS3() throws Exception {
		var accepted = request(officer, """
				{"type":"TAX_ROLL_SUMMARY","taxYear":2034}""");
		assertThat(accepted).hasStatus(ACCEPTED).bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.status").isEqualTo("QUEUED");
			assertThat(json).extractingPath("$.downloadable").isEqualTo(false);
		});
		var jobId = field(accepted, "$.jobId");

		await().atMost(PATIENCE).untilAsserted(() -> assertThat(statusOf(jobId)).isEqualTo("COMPLETED"));

		var download = get("/api/reports/" + jobId + "/download", officer);
		assertThat(download).hasStatus(OK).hasHeader("Cache-Control", "no-store");
		assertThat(download.getResponse().getHeader("Content-Type")).startsWith("text/csv");
		assertThat(download.getResponse().getHeader("Content-Disposition")).startsWith("attachment; filename=\"tax_roll_summary-2034-");
		var csv = download.getResponse().getContentAsString();
		assertThat(csv).startsWith("roll_number,street_address,city,property_class,assessed_value,account_number,billed,paid,outstanding\r\n");
		assertThat(csv).contains("RPT-0001,\"5 Ledger Lane, Unit 2\",Demo City,RESIDENTIAL,400000.00,TA-RPT-1,1000.00,250.00,750.00\r\n");

		var job = jobs.find(jobId).orElseThrow();
		assertThat(job.outputKey()).isEqualTo("reports/2034/tax_roll_summary-" + jobId + ".csv");
		assertThat(job.completedAt()).isNotNull();
		assertThat(documents.get(job.outputKey())).hasValueSatisfying(
				stored -> assertThat(new String(stored, StandardCharsets.UTF_8)).isEqualTo(csv));
		assertThat(get("/api/reports", officer)).bodyJson().extractingPath("$[?(@.jobId == '" + jobId + "')].status")
				.asArray().containsExactly("COMPLETED");
	}

	@Test
	void aFailingReportIsRetriedTwiceThenMarkedFailedByTheWorkflow() throws Exception {
		doThrow(new IllegalStateException("simulated database outage"))
				.when(generator).generate(ReportJob.Type.PAYMENTS_RECONCILIATION, 2099);

		var jobId = field(request(officer, """
				{"type":"PAYMENTS_RECONCILIATION","taxYear":2099}"""), "$.jobId");

		await().atMost(PATIENCE).untilAsserted(() -> assertThat(statusOf(jobId)).isEqualTo("FAILED"));

		verify(generator, times(3)).generate(ReportJob.Type.PAYMENTS_RECONCILIATION, 2099); // first try + 2 retries
		var failed = get("/api/reports/" + jobId, officer);
		assertThat(failed).bodyJson().extractingPath("$.errorMessage").asString().contains("simulated database outage");
		assertThat(failed).bodyJson().extractingPath("$.downloadable").isEqualTo(false);
		assertThat(jobs.find(jobId).orElseThrow().outputKey()).isNull();
		assertThat(get("/api/reports/" + jobId + "/download", officer)).hasStatus(CONFLICT);
	}

	@Test
	void theOtherTwoReportQueriesRunAgainstPostgres() {
		var outstanding = new String(generator.generate(ReportJob.Type.OUTSTANDING_BALANCES, 2034), StandardCharsets.UTF_8);
		assertThat(outstanding).startsWith("bill_number,bill_type,account_number,roll_number,street_address,due_date,amount,paid,outstanding\r\n")
				.contains("TA-RPT-1-2034-F-1,FINAL,TA-RPT-1,RPT-0001,\"5 Ledger Lane, Unit 2\",2026-07-01,1000.00,250.00,750.00");

		var reconciliation = new String(generator.generate(ReportJob.Type.PAYMENTS_RECONCILIATION, 2034), StandardCharsets.UTF_8);
		assertThat(reconciliation).startsWith("reference_number,payment_date,method,amount,status,bill_number,account_number,recorded_by\r\n")
				.contains("RPT-PAY-1,2026-06-02,CHEQUE,250.00,RECORDED,TA-RPT-1-2034-F-1,TA-RPT-1,test-tax-officer@demo.test");
	}

	@Test
	void reportsAreStaffOnlyAndRequestsAreValidated() {
		var valid = """
				{"type":"TAX_ROLL_SUMMARY","taxYear":2034}""";
		assertThat(request(resident, valid)).hasStatus(FORBIDDEN);
		assertThat(get("/api/reports", resident)).hasStatus(FORBIDDEN);
		assertThat(request(null, valid)).hasStatus(UNAUTHORIZED);
		assertThat(request(officer, """
				{"type":"TAX_ROLL_SUMMARY","taxYear":1800}""")).hasStatus(BAD_REQUEST);
		assertThat(request(officer, """
				{"type":"NOT_A_REPORT","taxYear":2034}""")).hasStatus(BAD_REQUEST);
		assertThat(get("/api/reports/no-such-job", officer)).hasStatus(NOT_FOUND);
	}

}
