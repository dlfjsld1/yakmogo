package com.yakmogo.yakmogo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.yakmogo.yakmogo.domain.MedicineGroup;
import com.yakmogo.yakmogo.domain.ScheduleType;
import com.yakmogo.yakmogo.repository.IntakeLogRepository;
import com.yakmogo.yakmogo.repository.MedicineGroupRepository;

class DailyIntakeLogServiceTest {
	private final MedicineGroupRepository medicineGroups = mock(MedicineGroupRepository.class);
	private final IntakeLogRepository intakeLogs = mock(IntakeLogRepository.class);
	private final MedicineSchedulePolicy schedulePolicy = new MedicineSchedulePolicy();
	private final DailyIntakeLogService service = new DailyIntakeLogService(medicineGroups, intakeLogs, schedulePolicy);
	private final LocalDate date = LocalDate.of(2026, 7, 14);

	@Test
	void createsOneLogAndSkipsAnExistingMedicineDatePair() {
		MedicineGroup group = MedicineGroup.builder()
			.name("테스트 약")
			.scheduleType(ScheduleType.DAILY)
			.startDate(date)
			.intakeTime(LocalTime.of(9, 0))
			.build();
		when(medicineGroups.findAllByIsActiveTrue()).thenReturn(List.of(group));
		when(intakeLogs.existsByMedicineGroupIdAndIntakeDate(group.getId(), date)).thenReturn(false, true);

		assertEquals(1, service.generateFor(date));
		assertEquals(0, service.generateFor(date));
		verify(intakeLogs).save(any());
	}

	@Test
	void doesNotCreateLogOnAnUnscheduledDate() {
		MedicineGroup group = MedicineGroup.builder()
			.name("주간 약")
			.scheduleType(ScheduleType.WEEKLY)
			.scheduleValue("Monday")
			.startDate(date.minusDays(1))
			.intakeTime(LocalTime.of(9, 0))
			.build();
		when(medicineGroups.findAllByIsActiveTrue()).thenReturn(List.of(group));

		assertEquals(0, service.generateFor(date));
		verify(intakeLogs, never()).save(any());
	}
}
