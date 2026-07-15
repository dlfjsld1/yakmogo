package com.yakmogo.yakmogo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.yakmogo.yakmogo.auth.ForbiddenException;
import com.yakmogo.yakmogo.domain.Guardian;
import com.yakmogo.yakmogo.domain.IntakeLog;
import com.yakmogo.yakmogo.domain.IntakeStatus;
import com.yakmogo.yakmogo.domain.MedicineGroup;
import com.yakmogo.yakmogo.domain.ScheduleType;
import com.yakmogo.yakmogo.domain.User;
import com.yakmogo.yakmogo.repository.IntakeLogRepository;
import com.yakmogo.yakmogo.repository.MedicineGroupRepository;
import com.yakmogo.yakmogo.repository.UserRepository;

@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:intake-command-tests;MODE=MariaDB;DB_CLOSE_DELAY=-1",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa",
	"spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=validate",
	"scheduling.enabled=false",
	"telegram.bot.enabled=false",
	"telegram.bot.token=test-token",
	"telegram.bot.username=test-bot",
	"telegram.bot.chat-id=test-chat",
	"admin.password=test-admin",
	"auth.token.secret=test-auth-secret-with-at-least-32-bytes",
	"app.frontend.url=http://localhost"
})
@Transactional
class IntakeCommandServiceIntegrationTest {
	@Autowired
	private IntakeCommandService intakeCommandService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private MedicineGroupRepository medicineGroupRepository;

	@Autowired
	private IntakeLogRepository intakeLogRepository;

	@Test
	void telegramAndRestShareTheSamePendingOnlyTransition() {
		IntakeLog log = savePendingLog("telegram-chat");

		IntakeCompletion completion = intakeCommandService.completeFromTelegram(log.getId(), "telegram-chat");

		IntakeLog completed = intakeLogRepository.findById(log.getId()).orElseThrow();
		assertEquals(IntakeStatus.TAKEN, completed.getStatus());
		assertNotNull(completed.getActualTakenTime());
		assertEquals(completed.getActualTakenTime(), completion.actualTakenTime());
		assertThrows(InvalidIntakeTransitionException.class,
			() -> intakeCommandService.completeFromTelegram(log.getId(), "telegram-chat"));
	}

	@Test
	void telegramCannotCompleteAnotherGuardiansIntake() {
		IntakeLog log = savePendingLog("owner-chat");

		assertThrows(ForbiddenException.class,
			() -> intakeCommandService.completeFromTelegram(log.getId(), "different-chat"));
		assertEquals(IntakeStatus.PENDING,
			intakeLogRepository.findById(log.getId()).orElseThrow().getStatus());
	}

	private IntakeLog savePendingLog(String chatId) {
		User user = User.builder().name("복용자").build();
		user.addGuardian(Guardian.builder().user(user).name("보호자").chatId(chatId).build());
		userRepository.saveAndFlush(user);
		MedicineGroup medicine = medicineGroupRepository.saveAndFlush(MedicineGroup.builder()
			.user(user)
			.name("아침 약")
			.scheduleType(ScheduleType.DAILY)
			.startDate(LocalDate.now())
			.intakeTime(LocalTime.of(9, 0))
			.build());
		return intakeLogRepository.saveAndFlush(IntakeLog.builder()
			.user(user)
			.medicineGroup(medicine)
			.intakeDate(LocalDate.now())
			.intakeTime(LocalTime.of(9, 0))
			.status(IntakeStatus.PENDING)
			.build());
	}
}
