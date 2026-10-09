package com.propertytax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import java.math.BigDecimal;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.propertytax.assessment.Assessment;
import com.propertytax.assessment.AssessmentRepository;
import com.propertytax.owner.Owner;
import com.propertytax.owner.OwnerRepository;
import com.propertytax.payment.Payment;
import com.propertytax.payment.PaymentRepository;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;

/**
 * Stage 8: authentication, role boundaries, and resident data ownership, with real signed tokens over HTTP.
 * Two residents each own one property (fictional data, tax year 2032 so nothing collides with other classes).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecurityIntegrationTest {

	@Autowired MockMvcTester mvc;
	@Autowired DevTokenIssuer issuer;
	@Autowired UserRepository users;
	@Autowired OwnerRepository owners;
	@Autowired PropertyRepository properties;
	@Autowired PropertyOwnerRepository propertyOwners;
	@Autowired AssessmentRepository assessments;
	@Autowired TaxRateRepository taxRates;
	@Autowired TaxAccountRepository taxAccounts;
	@Autowired TaxBillRepository taxBills;
	@Autowired PaymentRepository payments;

	record Holding(String token, long ownerId, long propertyId, long assessmentId, long accountId, long billId, long paymentId) {}

	Holding alice;
	Holding bob;
	String officer;
	String admin;
	User officerUser;

	@BeforeAll
	void setUp() {
		officer = TestAuth.bearer(users, issuer, User.Role.TAX_OFFICER);
		admin = TestAuth.bearer(users, issuer, User.Role.ADMIN);
		officerUser = users.findByEmail("test-tax-officer@demo.test").orElseThrow();
		var rate = taxRates.save(new TaxRate(2032, PropertyClass.RESIDENTIAL, new BigDecimal("0.0100"), "Demo (fictional)"));
		alice = holding("sec-alice@demo.test", "SEC-A", rate);
		bob = holding("sec-bob@demo.test", "SEC-B", rate);
	}

	Holding holding(String email, String roll, TaxRate rate) {
		var user = users.save(new User(email, "Resident " + roll, User.Role.RESIDENT));
		var owner = owners.save(new Owner(user, "Resident " + roll, email, null, null));
		var property = properties.save(new Property(roll, "1 Secure St", "Demo City", "L5B 6F6", PropertyClass.RESIDENTIAL));
		propertyOwners.save(new PropertyOwner(property, owner, new BigDecimal("100")));
		var assessment = assessments.save(new Assessment(property, 2032, new BigDecimal("100000.00"), LocalDate.of(2032, 1, 1)));
		var account = taxAccounts.save(new TaxAccount("TA-" + roll, property));
		var bill = taxBills.save(new TaxBill(roll + "-B", account, assessment, rate, 2032, TaxBill.Type.FINAL,
				new BigDecimal("1000.00"), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1)));
		var payment = payments.save(new Payment(bill, new BigDecimal("10.00"), LocalDate.of(2026, 6, 2), Payment.Method.COUNTER,
				roll + "-P", officerUser));
		return new Holding(TestAuth.bearer(users, issuer, User.Role.RESIDENT, email), owner.getId(), property.getId(),
				assessment.getId(), account.getId(), bill.getId(), payment.getId());
	}

	MockMvcRequestBuilder get(String uri, String token) {
		return mvc.get().uri(uri).header(AUTHORIZATION, token);
	}

	MockMvcRequestBuilder post(String uri, String token, String json) {
		return mvc.post().uri(uri).header(AUTHORIZATION, token).contentType(APPLICATION_JSON).content(json);
	}

	/** Every resident-readable URL for one holding. */
	static List<String> readableUrls(Holding h) {
		return List.of(
				"/api/owners/" + h.ownerId(), "/api/owners/" + h.ownerId() + "/properties",
				"/api/properties/" + h.propertyId(), "/api/properties/" + h.propertyId() + "/owners",
				"/api/properties/" + h.propertyId() + "/assessments", "/api/properties/" + h.propertyId() + "/tax-account",
				"/api/assessments/" + h.assessmentId(),
				"/api/tax-accounts/" + h.accountId(), "/api/tax-accounts/" + h.accountId() + "/bills",
				"/api/tax-accounts/" + h.accountId() + "/balance",
				"/api/tax-bills/" + h.billId(), "/api/tax-bills/" + h.billId() + "/payments",
				"/api/payments/" + h.paymentId());
	}

	@Test
	void missingForgedOrGarbageTokensAre401AndHealthStaysPublic() throws Exception {
		assertThat(mvc.get().uri("/api/properties")).hasStatus(UNAUTHORIZED);
		assertThat(mvc.get().uri("/api/me")).hasStatus(UNAUTHORIZED);
		assertThat(get("/api/me", "Bearer not-a-jwt")).hasStatus(UNAUTHORIZED);
		assertThat(get("/api/me", "Bearer " + forgedTokenFor("sec-alice@demo.test"))).hasStatus(UNAUTHORIZED);
		assertThat(mvc.get().uri("/actuator/health")).hasStatus(OK);
	}

	@Test
	void deactivatedUserIsRejectedEvenWithAValidToken() {
		var user = users.save(new User("sec-gone@demo.test", "Gone", User.Role.TAX_OFFICER));
		var token = "Bearer " + issuer.issue(user).accessToken();
		assertThat(get("/api/me", token)).hasStatus(OK);

		user.update("Gone", User.Role.TAX_OFFICER, false);
		users.save(user);
		assertThat(get("/api/me", token)).hasStatus(UNAUTHORIZED);
	}

	@Test
	void residentReadsOwnRecordsAndIsForbiddenFromAnotherResidents() {
		for (var url : readableUrls(alice)) {
			assertThat(get(url, alice.token())).as(url).hasStatus(OK);
		}
		for (var url : readableUrls(bob)) {
			assertThat(get(url, alice.token())).as(url).hasStatus(FORBIDDEN);
		}
		assertThat(get("/api/properties/999999", alice.token())).hasStatus(FORBIDDEN); // no existence leak
	}

	@Test
	void residentCannotListOrChangeAnything() {
		for (var url : List.of("/api/properties", "/api/owners", "/api/tax-accounts", "/api/users")) {
			assertThat(get(url, alice.token())).as(url).hasStatus(FORBIDDEN);
		}
		assertThat(get("/api/tax-rates", alice.token())).hasStatus(OK); // published rates are public to residents
		assertThat(post("/api/properties", alice.token(), "{}")).hasStatus(FORBIDDEN);
		assertThat(post("/api/tax-bills/" + alice.billId() + "/payments", alice.token(), """
				{"amount":1.00,"paymentDate":"2026-06-15","method":"COUNTER","referenceNumber":"SEC-SELF"}"""))
				.hasStatus(FORBIDDEN);
	}

	@Test
	void adminsManageUsersAndOfficersManageTaxData() {
		assertThat(post("/api/users", officer, """
				{"email":"sec-new@demo.test","fullName":"New","role":"RESIDENT"}""")).hasStatus(FORBIDDEN);
		assertThat(get("/api/users", officer)).hasStatus(OK); // officers read users to link owners
		assertThat(post("/api/properties", admin, "{}")).hasStatus(FORBIDDEN);
		assertThat(get("/api/properties", admin)).hasStatus(OK); // admins have read-only oversight
		assertThat(get("/api/tax-bills/" + bob.billId(), officer)).hasStatus(OK);
	}

	@Test
	void meReportsTheOwnerLink() {
		assertThat(get("/api/me", alice.token())).hasStatus(OK).bodyJson().satisfies(json -> {
			assertThat(json).extractingPath("$.role").isEqualTo("RESIDENT");
			assertThat(json).extractingPath("$.ownerId").isEqualTo((int) alice.ownerId());
		});
		assertThat(get("/api/me", officer)).bodyJson().extractingPath("$.ownerId").isNull();
	}

	@Test
	void paymentIsRecordedAsTheAuthenticatedOfficer() {
		assertThat(post("/api/tax-bills/" + alice.billId() + "/payments", officer, """
				{"amount":5.00,"paymentDate":"2026-06-15","method":"COUNTER","referenceNumber":"SEC-OFFICER"}"""))
				.hasStatus(CREATED).bodyJson().extractingPath("$.recordedById").isEqualTo(officerUser.getId().intValue());
	}

	/** Correct issuer and claims, but signed with a key the server has never seen. */
	static String forgedTokenFor(String email) throws Exception {
		var generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		var pair = generator.generateKeyPair();
		var key = new RSAKey.Builder((RSAPublicKey) pair.getPublic()).privateKey((RSAPrivateKey) pair.getPrivate()).keyID("forged").build();
		var claims = JwtClaimsSet.builder().issuer("civictax-dev").subject("forged").claim("email", email)
				.issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600)).build();
		return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key))).encode(JwtEncoderParameters.from(claims)).getTokenValue();
	}

}
