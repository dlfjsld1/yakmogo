package com.yakmogo.yakmogo.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "MARIADB_TEST_URL", matches = ".+")
class FlywayMariaDbIntegrationTest {

	@Test
	void migratesEmptyMariaDbAndIsRepeatable() {
		Flyway flyway = Flyway.configure()
			.dataSource(
				System.getenv("MARIADB_TEST_URL"),
				System.getenv("MARIADB_TEST_USER"),
				System.getenv("MARIADB_TEST_PASSWORD")
			)
			.cleanDisabled(false)
			.load();

		flyway.clean();
		assertEquals(1, flyway.migrate().migrationsExecuted);
		assertTrue(flyway.validateWithResult().validationSuccessful);
		assertEquals(0, flyway.migrate().migrationsExecuted);
	}
}
