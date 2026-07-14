package com.yakmogo.yakmogo.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.yakmogo.yakmogo.domain.NotificationDelivery;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {
	Optional<NotificationDelivery> findByIntakeLogIdAndGuardianIdAndAlertKey(
		Long intakeLogId,
		Long guardianId,
		String alertKey
	);

	@Query("SELECT nd FROM NotificationDelivery nd " +
		"JOIN FETCH nd.intakeLog il " +
		"JOIN FETCH il.user " +
		"JOIN FETCH il.medicineGroup " +
		"JOIN FETCH nd.guardian " +
		"WHERE nd.status = 'RETRY_WAIT' " +
		"AND nd.nextAttemptAt <= :now " +
		"AND il.status = 'PENDING'")
	List<NotificationDelivery> findDueRetries(@Param("now") LocalDateTime now);
}
