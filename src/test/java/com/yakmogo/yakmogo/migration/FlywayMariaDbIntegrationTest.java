package com.yakmogo.yakmogo.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "MARIADB_TEST_URL", matches = ".+")
class FlywayMariaDbIntegrationTest {

	@Test
	void migratesEmptyMariaDbAndVerifiesMariaDbSpecificPersistence() throws SQLException {
		Flyway flyway = Flyway.configure()
			.dataSource(
				System.getenv("MARIADB_TEST_URL"),
				System.getenv("MARIADB_TEST_USER"),
				System.getenv("MARIADB_TEST_PASSWORD")
			)
			.cleanDisabled(false)
			.load();

		flyway.clean();
		assertEquals(2, flyway.migrate().migrationsExecuted);
		assertTrue(flyway.validateWithResult().validationSuccessful);
		verifyRepresentativePersistence();
		assertEquals(0, flyway.migrate().migrationsExecuted);
	}

	private void verifyRepresentativePersistence() throws SQLException {
		try (Connection connection = DriverManager.getConnection(
			System.getenv("MARIADB_TEST_URL"),
			System.getenv("MARIADB_TEST_USER"),
			System.getenv("MARIADB_TEST_PASSWORD")
		)) {
			connection.setAutoCommit(false);
			try {
				long userId = insertAndReturnId(connection, "INSERT INTO users(name) VALUES ('MariaDB 검증 사용자')");
				long medicineId = insertMedicine(connection, userId);
				long intakeId = insertIntake(connection, userId, medicineId);
				long guardianId = insertAndReturnId(connection,
					"INSERT INTO guardian(chat_id,name,user_id) VALUES ('migration-chat','보호자'," + userId + ")");

				try (PreparedStatement statement = connection.prepareStatement(
					"SELECT is_active, schedule_type, intake_time FROM medicine_group WHERE id=?"
				)) {
					statement.setLong(1, medicineId);
					try (ResultSet result = statement.executeQuery()) {
						assertTrue(result.next());
						assertTrue(result.getBoolean("is_active"));
						assertEquals("WEEKLY", result.getString("schedule_type"));
						assertEquals("09:15:30", result.getTime("intake_time").toString());
					}
				}

				try (PreparedStatement statement = connection.prepareStatement(
					"UPDATE intake_log SET status='TAKEN', actual_taken_time=? WHERE id=? AND status='PENDING'"
				)) {
					statement.setTimestamp(1, Timestamp.valueOf(LocalDateTime.of(2026, 7, 14, 9, 16, 30, 123_456_000)));
					statement.setLong(2, intakeId);
					assertEquals(1, statement.executeUpdate());
					assertEquals(0, statement.executeUpdate());
				}

				assertThrows(SQLException.class, () -> {
					try (Statement statement = connection.createStatement()) {
						statement.executeUpdate("INSERT INTO guardian(chat_id,name,user_id) VALUES ('invalid','invalid',-1)");
					}
				});
				assertThrows(SQLException.class, () -> insertIntake(connection, userId, medicineId));

				insertNotificationDelivery(connection, intakeId, guardianId, "ON_TIME");
				assertThrows(SQLException.class,
					() -> insertNotificationDelivery(connection, intakeId, guardianId, "ON_TIME"));
				assertFalse(connection.isClosed());
			} finally {
				connection.rollback();
			}
		}
	}

	private long insertAndReturnId(Connection connection, String sql) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				assertTrue(keys.next());
				return keys.getLong(1);
			}
		}
	}

	private long insertMedicine(Connection connection, long userId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
			"INSERT INTO medicine_group(intake_time,is_active,name,schedule_type,schedule_value,start_date,user_id) "
				+ "VALUES ('09:15:30',?, 'MariaDB 검증 약','WEEKLY','Monday,Wednesday','2026-07-14',?)",
			Statement.RETURN_GENERATED_KEYS
		)) {
			statement.setBoolean(1, true);
			statement.setLong(2, userId);
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				assertTrue(keys.next());
				return keys.getLong(1);
			}
		}
	}

	private long insertIntake(Connection connection, long userId, long medicineId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
			"INSERT INTO intake_log(intake_date,intake_time,notified_count,status,medicine_group_id,user_id) "
				+ "VALUES ('2026-07-14','09:15:30',0,'PENDING',?,?)",
			Statement.RETURN_GENERATED_KEYS
		)) {
			statement.setLong(1, medicineId);
			statement.setLong(2, userId);
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				assertTrue(keys.next());
				return keys.getLong(1);
			}
		}
	}

	private void insertNotificationDelivery(
		Connection connection,
		long intakeId,
		long guardianId,
		String alertKey
	) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
			"INSERT INTO notification_delivery(" +
				"intake_log_id,guardian_id,alert_key,status,attempt_count" +
				") VALUES (?, ?, ?, 'PENDING', 0)"
		)) {
			statement.setLong(1, intakeId);
			statement.setLong(2, guardianId);
			statement.setString(3, alertKey);
			statement.executeUpdate();
		}
	}
}
