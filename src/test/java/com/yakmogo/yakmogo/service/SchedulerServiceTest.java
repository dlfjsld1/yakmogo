package com.yakmogo.yakmogo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.yakmogo.yakmogo.domain.Guardian;
import com.yakmogo.yakmogo.domain.IntakeLog;
import com.yakmogo.yakmogo.domain.IntakeStatus;
import com.yakmogo.yakmogo.domain.MedicineGroup;
import com.yakmogo.yakmogo.domain.NotificationDelivery;
import com.yakmogo.yakmogo.domain.NotificationDeliveryStatus;
import com.yakmogo.yakmogo.domain.ScheduleType;
import com.yakmogo.yakmogo.domain.User;
import com.yakmogo.yakmogo.repository.IntakeLogRepository;
import com.yakmogo.yakmogo.repository.NotificationDeliveryRepository;

class SchedulerServiceTest {
	private final DailyIntakeLogService dailyIntakeLogService = mock(DailyIntakeLogService.class);
	private final IntakeLogRepository intakeLogs = mock(IntakeLogRepository.class);
	private final NotificationDeliveryRepository deliveries = mock(NotificationDeliveryRepository.class);
	private final MedicationAlertPolicy alertPolicy = new MedicationAlertPolicy();
	private final TelegramService telegramService = mock(TelegramService.class);
	private final ZoneId zone = ZoneId.of("Asia/Seoul");

	@Test
	void retriesAfterBackoffThenNeverResendsASuccessfulAlert() {
		IntakeLog intakeLog = intakeLog();
		Guardian guardian = intakeLog.getUser().getGuardians().get(0);
		NotificationDelivery delivery = NotificationDelivery.pending(intakeLog, guardian, "ON_TIME");

		when(intakeLogs.findPendingLogs(any(), any())).thenReturn(List.of(intakeLog));
		when(deliveries.findDueRetries(any())).thenReturn(
			List.of(),
			List.of(),
			List.of(delivery),
			List.of()
		);
		when(deliveries.findByIntakeLogIdAndGuardianIdAndAlertKey(any(), any(), anyString()))
			.thenReturn(Optional.empty(), Optional.of(delivery), Optional.of(delivery));
		when(deliveries.save(any())).thenReturn(delivery);
		when(telegramService.sendMedicationAlert(any(), any(), any()))
			.thenReturn(TelegramDeliveryResult.failure("timeout"), TelegramDeliveryResult.success());

		schedulerAt("2026-07-14T00:00:00Z").checkMissedDose();
		assertEquals(NotificationDeliveryStatus.RETRY_WAIT, delivery.getStatus());

		schedulerAt("2026-07-14T00:00:30Z").checkMissedDose();
		verify(telegramService, times(1)).sendMedicationAlert(any(), any(), any());

		schedulerAt("2026-07-14T00:01:00Z").checkMissedDose();
		assertEquals(NotificationDeliveryStatus.SENT, delivery.getStatus());
		assertEquals(2, delivery.getAttemptCount());

		schedulerAt("2026-07-14T00:02:00Z").checkMissedDose();
		verify(telegramService, times(2)).sendMedicationAlert(any(), any(), any());
	}

	@Test
	void retriesPreviousStageAtBoundaryWithoutSendingNewStageInSameRun() {
		IntakeLog intakeLog = intakeLog();
		Guardian guardian = intakeLog.getUser().getGuardians().get(0);
		NotificationDelivery delivery = NotificationDelivery.pending(intakeLog, guardian, "ON_TIME");
		delivery.recordFailure(java.time.LocalDateTime.of(2026, 7, 14, 9, 29), "timeout");

		when(intakeLogs.findPendingLogs(any(), any())).thenReturn(List.of(intakeLog));
		when(deliveries.findDueRetries(any())).thenReturn(List.of(delivery));
		when(telegramService.sendMedicationAlert(any(), any(), any()))
			.thenReturn(TelegramDeliveryResult.success());

		schedulerAt("2026-07-14T00:30:00Z").checkMissedDose();

		assertEquals(NotificationDeliveryStatus.SENT, delivery.getStatus());
		verify(telegramService, times(1)).sendMedicationAlert(any(), any(), any());
		verify(deliveries, never()).findByIntakeLogIdAndGuardianIdAndAlertKey(any(), any(), anyString());
	}

	private SchedulerService schedulerAt(String instant) {
		return new SchedulerService(
			dailyIntakeLogService,
			intakeLogs,
			deliveries,
			alertPolicy,
			telegramService,
			Clock.fixed(Instant.parse(instant), zone)
		);
	}

	private IntakeLog intakeLog() {
		User user = User.builder().name("테스트 사용자").build();
		Guardian guardian = Guardian.builder().user(user).name("보호자").chatId("chat").build();
		user.addGuardian(guardian);
		MedicineGroup medicine = MedicineGroup.builder()
			.user(user)
			.name("테스트 약")
			.scheduleType(ScheduleType.DAILY)
			.startDate(LocalDate.of(2026, 7, 14))
			.intakeTime(LocalTime.of(9, 0))
			.build();
		IntakeLog log = IntakeLog.builder()
			.user(user)
			.medicineGroup(medicine)
			.intakeDate(LocalDate.of(2026, 7, 14))
			.intakeTime(LocalTime.of(9, 0))
			.status(IntakeStatus.PENDING)
			.build();
		ReflectionTestUtils.setField(log, "id", 10L);
		ReflectionTestUtils.setField(guardian, "id", 20L);
		return log;
	}
}
