package com.yakmogo.yakmogo.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import com.yakmogo.yakmogo.domain.MedicineGroup;
import com.yakmogo.yakmogo.domain.ScheduleType;

class MedicineSchedulePolicyTest {
	private final MedicineSchedulePolicy policy = new MedicineSchedulePolicy();
	private final LocalDate monday = LocalDate.of(2026, 7, 13);

	@Test
	void dailyStartsOnStartDateAndRunsEveryDay() {
		MedicineGroup group = medicine(ScheduleType.DAILY, null, monday);

		assertFalse(policy.shouldTakeOn(group, monday.minusDays(1)));
		assertTrue(policy.shouldTakeOn(group, monday));
		assertTrue(policy.shouldTakeOn(group, monday.plusDays(4)));
	}

	@Test
	void weeklyMatchesExactCommaSeparatedDaysCaseInsensitively() {
		MedicineGroup group = medicine(ScheduleType.WEEKLY, "Monday,Wednesday", monday);

		assertTrue(policy.shouldTakeOn(group, monday));
		assertTrue(policy.shouldTakeOn(group, monday.plusDays(2)));
		assertFalse(policy.shouldTakeOn(group, monday.plusDays(1)));
		assertFalse(policy.shouldTakeOn(medicine(ScheduleType.WEEKLY, "MONDAYX", monday), monday));
	}

	@Test
	void intervalUsesStartDateAsItsStableAnchor() {
		MedicineGroup group = medicine(ScheduleType.INTERVAL, "3", monday);

		assertTrue(policy.shouldTakeOn(group, monday));
		assertFalse(policy.shouldTakeOn(group, monday.plusDays(1)));
		assertTrue(policy.shouldTakeOn(group, monday.plusDays(3)));
		assertThrows(IllegalArgumentException.class,
			() -> policy.shouldTakeOn(medicine(ScheduleType.INTERVAL, "0", monday), monday));
	}

	@Test
	void weeklyCalculationCrossesMonthYearAndLeapDayBoundaries() {
		LocalDate leapDay = LocalDate.of(2024, 2, 29);
		MedicineGroup weekly = medicine(ScheduleType.WEEKLY, "Thursday", leapDay.minusMonths(1));

		assertTrue(policy.shouldTakeOn(weekly, leapDay));
		assertFalse(policy.shouldTakeOn(weekly, leapDay.plusDays(1)));
		assertTrue(policy.shouldTakeOn(
			medicine(ScheduleType.WEEKLY, "Wednesday", LocalDate.of(2025, 12, 1)),
			LocalDate.of(2025, 12, 31)
		));
	}

	@Test
	void intervalCalculationUsesCalendarDaysAcrossLeapDayAndNewYear() {
		MedicineGroup leapInterval = medicine(ScheduleType.INTERVAL, "2", LocalDate.of(2024, 2, 28));
		MedicineGroup yearInterval = medicine(ScheduleType.INTERVAL, "3", LocalDate.of(2025, 12, 30));

		assertTrue(policy.shouldTakeOn(leapInterval, LocalDate.of(2024, 3, 1)));
		assertFalse(policy.shouldTakeOn(leapInterval, LocalDate.of(2024, 2, 29)));
		assertTrue(policy.shouldTakeOn(yearInterval, LocalDate.of(2026, 1, 2)));
	}

	private MedicineGroup medicine(ScheduleType type, String value, LocalDate startDate) {
		return MedicineGroup.builder()
			.name("테스트 약")
			.scheduleType(type)
			.scheduleValue(value)
			.startDate(startDate)
			.intakeTime(LocalTime.of(9, 0))
			.build();
	}
}
