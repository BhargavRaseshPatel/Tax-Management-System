package com.propertytax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
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

}
