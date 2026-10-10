package com.propertytax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.propertytax.assessment.Assessment;
import com.propertytax.assessment.AssessmentRepository;
import com.propertytax.document.DocumentStore;
import com.propertytax.owner.Owner;
import com.propertytax.owner.OwnerRepository;
import com.propertytax.property.Property;
import com.propertytax.property.PropertyClass;
import com.propertytax.property.PropertyOwner;
import com.propertytax.property.PropertyOwnerRepository;
import com.propertytax.property.PropertyRepository;
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
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Stage 11: a bill's PDF is generated, stored in S3 (emulator), recorded in PostgreSQL, and downloadable only by
 * staff or the owning resident. Fictional data, tax year 2033 so nothing collides with other test classes.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentIntegrationTest {

	@Autowired MockMvcTester mvc;
	@Autowired DevTokenIssuer issuer;
	@Autowired DocumentStore store;
	@Autowired UserRepository users;
	@Autowired OwnerRepository owners;
	@Autowired PropertyRepository properties;
	@Autowired PropertyOwnerRepository propertyOwners;
	@Autowired AssessmentRepository assessments;
	@Autowired TaxRateRepository taxRates;
	@Autowired TaxAccountRepository taxAccounts;
	@Autowired TaxBillRepository taxBills;

	String officer;
	String ownerToken;
	String strangerToken;
	long billId;

	@BeforeAll
	void setUp() {
		officer = TestAuth.bearer(users, issuer, User.Role.TAX_OFFICER);
		strangerToken = TestAuth.bearer(users, issuer, User.Role.RESIDENT, "doc-stranger@demo.test");
		var resident = users.save(new User("doc-owner@demo.test", "Dana Document", User.Role.RESIDENT));
		ownerToken = "Bearer " + issuer.issue(resident).accessToken();
		var owner = owners.save(new Owner(resident, "Dana Document", "doc-owner@demo.test", null, null));
		var property = properties.save(new Property("DOC-0001", "9 Paper Lane", "Demo City", "L5B 7G7", PropertyClass.RESIDENTIAL));
		propertyOwners.save(new PropertyOwner(property, owner, new BigDecimal("100.00")));
		var assessment = assessments.save(new Assessment(property, 2033, new BigDecimal("650000.00"), LocalDate.of(2033, 1, 1)));
		var rate = taxRates.save(new TaxRate(2033, PropertyClass.RESIDENTIAL, new BigDecimal("0.00750000"), "Demo (fictional)"));
		var account = taxAccounts.save(new TaxAccount("TA-DOC-1", property));
		billId = taxBills.save(new TaxBill("TA-DOC-1-2033-F-1", account, assessment, rate, 2033, TaxBill.Type.FINAL,
				new BigDecimal("4875.00"), LocalDate.of(2033, 6, 1), LocalDate.of(2033, 7, 1))).getId();
	}

	MvcTestResult download(String token) {
		var request = mvc.get().uri("/api/tax-bills/" + billId + "/document");
		return (token == null ? request : request.header(AUTHORIZATION, token)).exchange();
	}

	@Test
	void pdfIsGeneratedStoredInS3AndRecordedInPostgres() throws Exception {
		var response = download(officer);

		assertThat(response).hasStatus(OK)
				.hasHeader("Content-Type", "application/pdf")
				.hasHeader("Content-Disposition", "attachment; filename=\"TA-DOC-1-2033-F-1.pdf\"")
				.hasHeader("Cache-Control", "no-store");
		byte[] pdf = response.getResponse().getContentAsByteArray();
		var text = new PdfTextExtractor(new PdfReader(pdf)).getTextFromPage(1);
		assertThat(text).contains("TA-DOC-1-2033-F-1", "9 Paper Lane", "Dana Document (100%)", "$650,000.00",
				"0.00750000", "Amount billed: $4,875.00", "DEMONSTRATION DOCUMENT");

		var bill = taxBills.findById(billId).orElseThrow();
		assertThat(bill.getDocumentKey()).isEqualTo("tax-bills/2033/TA-DOC-1-2033-F-1.pdf");
		assertThat(bill.getDocumentSizeBytes()).isEqualTo(pdf.length);
		assertThat(bill.getDocumentGeneratedAt()).isNotNull();
		assertThat(store.get(bill.getDocumentKey())).hasValueSatisfying(stored -> assertThat(stored).isEqualTo(pdf));
	}

	@Test
	void laterDownloadsServeTheStoredObjectInsteadOfRegenerating() {
		byte[] first = download(officer).getResponse().getContentAsByteArray();
		var generatedAt = taxBills.findById(billId).orElseThrow().getDocumentGeneratedAt();

		byte[] second = download(officer).getResponse().getContentAsByteArray();

		assertThat(second).isEqualTo(first); // a re-render would differ (PDFs embed a creation time and id)
		assertThat(taxBills.findById(billId).orElseThrow().getDocumentGeneratedAt()).isEqualTo(generatedAt);
	}

	@Test
	void onlyStaffAndTheOwningResidentCanDownload() {
		assertThat(download(ownerToken)).hasStatus(OK);
		assertThat(download(strangerToken)).hasStatus(FORBIDDEN);
		assertThat(download(null)).hasStatus(UNAUTHORIZED);
	}

}
