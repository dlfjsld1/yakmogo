package com.yakmogo.yakmogo.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.yakmogo.yakmogo.auth.AuthTokenService;
import com.yakmogo.yakmogo.domain.Guardian;
import com.yakmogo.yakmogo.domain.IntakeLog;
import com.yakmogo.yakmogo.domain.IntakeStatus;
import com.yakmogo.yakmogo.domain.MedicineGroup;
import com.yakmogo.yakmogo.domain.ScheduleType;
import com.yakmogo.yakmogo.domain.User;
import com.yakmogo.yakmogo.service.MedicationAlert.AlertLevel;

class TelegramServiceTest {
	private final AuthTokenService authTokenService = mock(AuthTokenService.class);
	private final RestTemplate restTemplate = mock(RestTemplate.class);
	private final TelegramService telegramService = new TelegramService(authTokenService, restTemplate);
	private IntakeLog intakeLog;
	private Guardian guardian;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(telegramService, "botToken", "test-token-not-sent");
		ReflectionTestUtils.setField(telegramService, "frontendUrl", "http://localhost:8081");
		when(authTokenService.issueLoginProof(anyString())).thenReturn("signed-test-proof");

		User user = User.builder().name("테스트 사용자").build();
		MedicineGroup medicine = MedicineGroup.builder()
			.user(user)
			.name("테스트 약")
			.scheduleType(ScheduleType.DAILY)
			.startDate(LocalDate.of(2026, 7, 14))
			.intakeTime(LocalTime.of(9, 0))
			.build();
		guardian = Guardian.builder().user(user).name("보호자").chatId("test-chat").build();
		intakeLog = IntakeLog.builder()
			.user(user)
			.medicineGroup(medicine)
			.intakeDate(LocalDate.of(2026, 7, 14))
			.intakeTime(LocalTime.of(9, 0))
			.status(IntakeStatus.PENDING)
			.build();
		ReflectionTestUtils.setField(intakeLog, "id", 11L);
	}

	@Test
	void returnsSuccessOnlyWhenTelegramApiAcceptsTheRequest() {
		when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
			.thenReturn(ResponseEntity.ok("ok"));

		TelegramDeliveryResult result = telegramService.sendMedicationAlert(
			intakeLog,
			guardian,
			new MedicationAlert("ON_TIME", AlertLevel.ON_TIME, 0)
		);

		assertTrue(result.delivered());
	}

	@Test
	void returnsSanitizedFailureInsteadOfPretendingDeliverySucceeded() {
		when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
			.thenThrow(new IllegalStateException("response contained a sensitive value"));

		TelegramDeliveryResult result = telegramService.sendMedicationAlert(
			intakeLog,
			guardian,
			new MedicationAlert("ON_TIME", AlertLevel.ON_TIME, 0)
		);

		assertFalse(result.delivered());
		assertTrue(result.failureReason().equals("IllegalStateException"));
	}
}
