package com.yakmogo.yakmogo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import com.yakmogo.yakmogo.domain.IntakeLog;
import com.yakmogo.yakmogo.domain.IntakeStatus;

class MedicationAlertPolicyTest {
	private final MedicationAlertPolicy policy = new MedicationAlertPolicy();
	private final LocalDate date = LocalDate.of(2026, 7, 14);

	@Test
	void selectsOneStableStageForEachOverdueWindow() {
		IntakeLog log = intakeAt(LocalTime.of(9, 0));

		assertEquals("ON_TIME", decision(log, 9, 0).key());
		assertEquals("ON_TIME", decision(log, 9, 29).key());
		assertEquals("OVERDUE_30_MINUTES", decision(log, 9, 30).key());
		assertEquals("OVERDUE_1_HOUR", decision(log, 10, 1).key());
		assertEquals("OVERDUE_2_HOURS", decision(log, 11, 59).key());
		assertEquals("OVERDUE_6_HOURS", decision(log, 15, 0).key());
	}

	@Test
	void doesNotAlertBeforeDueTimeAfterSixHoursOrAcrossMidnight() {
		IntakeLog morning = intakeAt(LocalTime.of(9, 0));
		IntakeLog lateNight = intakeAt(LocalTime.of(23, 50));

		assertTrue(policy.currentAlert(morning, LocalDateTime.of(date, LocalTime.of(8, 59))).isEmpty());
		assertTrue(policy.currentAlert(morning, LocalDateTime.of(date, LocalTime.of(15, 1))).isEmpty());
		assertTrue(policy.currentAlert(lateNight, LocalDateTime.of(date.plusDays(1), LocalTime.MIDNIGHT)).isEmpty());
	}

	private MedicationAlert decision(IntakeLog log, int hour, int minute) {
		return policy.currentAlert(log, LocalDateTime.of(date, LocalTime.of(hour, minute))).orElseThrow();
	}

	private IntakeLog intakeAt(LocalTime time) {
		return IntakeLog.builder()
			.intakeDate(date)
			.intakeTime(time)
			.status(IntakeStatus.PENDING)
			.build();
	}
}
