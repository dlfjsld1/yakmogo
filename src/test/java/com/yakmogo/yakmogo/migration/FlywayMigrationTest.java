package com.yakmogo.yakmogo.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class FlywayMigrationTest {

	@Test
	void migratesEmptyDatabaseAndIsRepeatable() throws SQLException {
		Flyway flyway = Flyway.configure()
			.dataSource("jdbc:h2:mem:flyway-migration;MODE=MariaDB;DB_CLOSE_DELAY=-1", "sa", "")
			.load();

		assertEquals(2, flyway.migrate().migrationsExecuted);
		assertTrue(flyway.validateWithResult().validationSuccessful);
		try (var connection = flyway.getConfiguration().getDataSource().getConnection();
			 var result = connection.getMetaData().getTables(null, null, "NOTIFICATION_DELIVERY", null)) {
			assertTrue(result.next());
		}
		assertEquals(0, flyway.migrate().migrationsExecuted);
	}
}
