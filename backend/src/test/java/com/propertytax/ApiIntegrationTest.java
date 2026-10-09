package com.propertytax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.METHOD_NOT_ALLOWED;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** HTTP contract: status codes, ProblemDetail bodies, and the ownership-share rule. Fictional data. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {

	@Autowired
	MockMvcTester mvc;

	MvcTestResult post(String uri, String json) {
		return mvc.post().uri(uri).contentType(APPLICATION_JSON).content(json).exchange();
	}

	MvcTestResult put(String uri, String json) {
		return mvc.put().uri(uri).contentType(APPLICATION_JSON).content(json).exchange();
	}

	long id(MvcTestResult result) throws Exception {
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
	}

	@Test
	void createReturns201AndDuplicateReturns409WithConstraintName() {
		var user = """
				{"email":"Api.Test@demo.test","fullName":"Api Test","role":"RESIDENT"}""";

		assertThat(post("/api/users", user)).hasStatus(CREATED)
				.bodyJson().extractingPath("$.email").isEqualTo("api.test@demo.test");
		assertThat(post("/api/users", user)).hasStatus(CONFLICT)
				.bodyJson().extractingPath("$.constraint").isEqualTo("users_email_key");
	}

	@Test
	void validationFailureListsEachInvalidField() {
		assertThat(post("/api/users", """
				{"email":"not-an-email","fullName":"","role":"RESIDENT"}"""))
				.hasStatus(BAD_REQUEST)
				.bodyJson().satisfies(json -> {
					assertThat(json).extractingPath("$.errors.email").isEqualTo("must be a well-formed email address");
					assertThat(json).extractingPath("$.errors.fullName").isEqualTo("must not be blank");
				});
	}

	@Test
	void unknownIdReturns404ProblemDetail() {
		assertThat(mvc.get().uri("/api/properties/999999")).hasStatus(NOT_FOUND)
				.bodyJson().extractingPath("$.detail").isEqualTo("Property 999999 not found");
	}

	@Test
	void ownershipSharesCannotExceed100Percent() throws Exception {
		long property = id(post("/api/properties", """
				{"rollNumber":"API-0001","streetAddress":"1 Test Way","city":"Demo City","postalCode":"L5B 3C3","propertyClass":"RESIDENTIAL"}"""));
		long first = id(post("/api/owners", """
				{"fullName":"First Owner"}"""));
		long second = id(post("/api/owners", """
				{"fullName":"Second Owner"}"""));
		var uri = "/api/properties/" + property + "/owners";

		assertThat(post(uri, "{\"ownerId\":" + first + ",\"ownershipPercent\":70}")).hasStatus(CREATED);
		assertThat(post(uri, "{\"ownerId\":" + second + ",\"ownershipPercent\":40}")).hasStatus(CONFLICT)
				.bodyJson().extractingPath("$.detail").asString().contains("110.00%");
		assertThat(post(uri, "{\"ownerId\":" + second + ",\"ownershipPercent\":30}")).hasStatus(CREATED);
	}

	@Test
	void malformedJsonBadEnumAndWrongMethodFailCleanlyWithoutLeakingInternals() {
		var malformed = post("/api/users", "{not json");
		assertThat(malformed).hasStatus(BAD_REQUEST);
		assertThat(malformed).bodyText().doesNotContain("Exception").doesNotContain("at com.").doesNotContain("SQL");

		assertThat(post("/api/users", """
				{"email":"enum@demo.test","fullName":"Enum","role":"SUPERUSER"}""")).hasStatus(BAD_REQUEST);
		assertThat(mvc.get().uri("/api/users/not-a-number")).hasStatus(BAD_REQUEST);
		assertThat(mvc.delete().uri("/api/users/1")).hasStatus(METHOD_NOT_ALLOWED);
	}

	/** Touches every read/update/delete endpoint not exercised elsewhere (creates covered above and in billing tests). */
	@Test
	void everyReadUpdateAndDeleteEndpointResponds() throws Exception {
		long user = id(post("/api/users", """
				{"email":"walk@demo.test","fullName":"Walk Resident","role":"RESIDENT"}"""));
		assertThat(mvc.get().uri("/api/users?size=1&sort=email,desc")).hasStatusOk();
		assertThat(mvc.get().uri("/api/users/" + user)).hasStatusOk();
		assertThat(put("/api/users/" + user, """
				{"fullName":"Walk R. Resident","role":"RESIDENT","active":true}""")).hasStatusOk()
				.bodyJson().extractingPath("$.fullName").isEqualTo("Walk R. Resident");

		long owner = id(post("/api/owners", "{\"userId\":" + user + ",\"fullName\":\"Walk Owner\"}"));
		assertThat(mvc.get().uri("/api/owners?name=WALK")).hasStatusOk()
				.bodyJson().extractingPath("$.page.totalElements").isEqualTo(1);
		assertThat(mvc.get().uri("/api/owners/" + owner)).hasStatusOk();
		assertThat(put("/api/owners/" + owner, "{\"userId\":" + user + ",\"fullName\":\"Walk Owner\",\"phone\":\"555-0199\"}"))
				.hasStatusOk().bodyJson().extractingPath("$.phone").isEqualTo("555-0199");

		long property = id(post("/api/properties", """
				{"rollNumber":"WALK-0001","streetAddress":"7 Walk St","city":"Demo City","postalCode":"L5B 5E5","propertyClass":"COMMERCIAL"}"""));
		assertThat(mvc.get().uri("/api/properties?q=walk-0001")).hasStatusOk()
				.bodyJson().extractingPath("$.content.length()").isEqualTo(1);
		assertThat(mvc.get().uri("/api/properties/" + property)).hasStatusOk();
		assertThat(put("/api/properties/" + property, """
				{"rollNumber":"WALK-0001","streetAddress":"7 Walk Street","city":"Demo City","postalCode":"L5B 5E5","propertyClass":"COMMERCIAL"}"""))
				.hasStatusOk();

		var owners = "/api/properties/" + property + "/owners";
		assertThat(post(owners, "{\"ownerId\":" + owner + ",\"ownershipPercent\":100}")).hasStatus(CREATED);
		assertThat(mvc.get().uri(owners)).bodyJson().extractingPath("$.length()").isEqualTo(1);
		assertThat(mvc.get().uri("/api/owners/" + owner + "/properties")).bodyJson().extractingPath("$[0].id").isEqualTo((int) property);
		assertThat(mvc.delete().uri(owners + "/" + owner)).hasStatus(NO_CONTENT);
		assertThat(mvc.get().uri(owners)).bodyJson().extractingPath("$.length()").isEqualTo(0);

		long assessment = id(post("/api/properties/" + property + "/assessments", """
				{"taxYear":2031,"assessedValue":900000.00,"assessmentDate":"2031-01-01"}"""));
		assertThat(mvc.get().uri("/api/properties/" + property + "/assessments")).bodyJson().extractingPath("$.length()").isEqualTo(1);
		assertThat(mvc.get().uri("/api/assessments/" + assessment)).hasStatusOk();

		long rate = id(post("/api/tax-rates", """
				{"taxYear":2031,"propertyClass":"COMMERCIAL","rate":0.012,"description":"Demo rate (fictional)"}"""));
		assertThat(mvc.get().uri("/api/tax-rates")).hasStatusOk();
		assertThat(mvc.get().uri("/api/tax-rates/" + rate)).hasStatusOk();
		assertThat(put("/api/tax-rates/" + rate, """
				{"rate":0.0125,"description":"Demo rate (fictional, revised)"}""")).hasStatusOk();

		long account = id(post("/api/tax-accounts", "{\"propertyId\":" + property + "}"));
		assertThat(mvc.get().uri("/api/tax-accounts?size=1")).hasStatusOk();
		assertThat(mvc.get().uri("/api/tax-accounts/" + account)).hasStatusOk();
		assertThat(mvc.get().uri("/api/properties/" + property + "/tax-account")).bodyJson()
				.extractingPath("$.id").isEqualTo((int) account);
		assertThat(mvc.get().uri("/api/tax-accounts/" + account + "/bills")).bodyJson().extractingPath("$.length()").isEqualTo(0);
		assertThat(mvc.get().uri("/api/tax-accounts/" + account + "/balance")).bodyJson().extractingPath("$.outstanding").isEqualTo(0.00);
	}

}
