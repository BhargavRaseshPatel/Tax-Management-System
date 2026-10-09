package com.propertytax;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BackendApplicationTests {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void flywayCreatesExactlyTheNineCoreTables() {
		var tables = jdbc.queryForList("""
				SELECT table_name FROM information_schema.tables
				WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'
				""", String.class);

		assertThat(tables).containsExactlyInAnyOrder(
				"users", "owners", "properties", "property_owners", "assessments",
				"tax_rates", "tax_accounts", "tax_bills", "payments");
	}

}
