package com.yakmogo.yakmogo.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class FlywayMigrationTest {

	@Test
	void migratesEmptyDatabaseAndIsRepeatable() {
		Flyway flyway = Flyway.configure()
			.dataSource("jdbc:h2:mem:flyway-migration;MODE=MariaDB;DB_CLOSE_DELAY=-1", "sa", "")
			.load();

		assertEquals(1, flyway.migrate().migrationsExecuted);
		assertTrue(flyway.validateWithResult().validationSuccessful);
		assertEquals(0, flyway.migrate().migrationsExecuted);
	}
}
