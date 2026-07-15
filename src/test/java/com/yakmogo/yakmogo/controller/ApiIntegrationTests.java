package com.yakmogo.yakmogo.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.yakmogo.yakmogo.auth.AuthTokenService;
import com.yakmogo.yakmogo.domain.Guardian;
import com.yakmogo.yakmogo.domain.IntakeLog;
import com.yakmogo.yakmogo.domain.IntakeStatus;
import com.yakmogo.yakmogo.domain.MedicineGroup;
import com.yakmogo.yakmogo.domain.ScheduleType;
import com.yakmogo.yakmogo.domain.User;
import com.yakmogo.yakmogo.repository.GuardianRepository;
import com.yakmogo.yakmogo.repository.IntakeLogRepository;
import com.yakmogo.yakmogo.repository.MedicineGroupRepository;
import com.yakmogo.yakmogo.repository.UserRepository;

@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:api-tests;MODE=MariaDB;DB_CLOSE_DELAY=-1",
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
@AutoConfigureMockMvc
@Transactional
class ApiIntegrationTests {
	private static final String ADMIN_HEADER = "x-admin-password";
	private static final String ADMIN_PASSWORD = "test-admin";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private GuardianRepository guardianRepository;

	@Autowired
	private MedicineGroupRepository medicineGroupRepository;

	@Autowired
	private IntakeLogRepository intakeLogRepository;

	@Autowired
	private AuthTokenService authTokenService;

	@Test
	void healthExposesOnlyOverallStatusAndKeepsSensitiveActuatorEndpointsClosed() throws Exception {
		mockMvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.components").doesNotExist());

		mockMvc.perform(get("/actuator"))
			.andExpect(handler().handlerType(SpaController.class));
		mockMvc.perform(get("/actuator/env"))
			.andExpect(handler().handlerType(SpaController.class));
		mockMvc.perform(get("/actuator/configprops"))
			.andExpect(handler().handlerType(SpaController.class));
		mockMvc.perform(get("/actuator/heapdump"))
			.andExpect(handler().handlerType(SpaController.class));
	}

	@Test
	void userApiPreservesSuccessResponsesAndSeparates401403404And400() throws Exception {
		mockMvc.perform(get("/api/v1/users"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
			.andExpect(jsonPath("$.error").exists());

		mockMvc.perform(post("/api/v1/users")
			.header(ADMIN_HEADER, ADMIN_PASSWORD)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\" \",\"guardians\":[]}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors.name").exists());

		mockMvc.perform(post("/api/v1/users")
			.header(ADMIN_HEADER, ADMIN_PASSWORD)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"새 복용자\",\"guardians\":[{\"name\":\"보호자\",\"chatId\":\"12345\"}]}"))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.startsWith("유저 등록 완료! ID: ")));

		mockMvc.perform(get("/api/v1/users/{userId}", 999999L)
			.header(ADMIN_HEADER, ADMIN_PASSWORD))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		mockMvc.perform(get("/api/v1/users/{userId}", -1L)
			.header(ADMIN_HEADER, ADMIN_PASSWORD))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		User managed = saveUser("관리 대상", "managed-chat");
		String userToken = tokenFor(managed);
		mockMvc.perform(delete("/api/v1/users/{userId}", managed.getId())
			.header("x-magic-token", userToken))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		assertTrue(userRepository.existsById(managed.getId()));

		mockMvc.perform(delete("/api/v1/users/{userId}", managed.getId())
			.header(ADMIN_HEADER, ADMIN_PASSWORD))
			.andExpect(status().isOk())
			.andExpect(content().string("복용자 정보가 완전히 삭제되었습니다."));
		assertFalse(userRepository.existsById(managed.getId()));
	}

	@Test
	void guardianApiAddsAndDeletesOnlyWithinTheRequestedUser() throws Exception {
		User owner = saveUser("소유자", "owner-chat");
		User other = saveUser("다른 사용자", "other-chat");
		Guardian otherGuardian = other.getGuardians().getFirst();
		String token = tokenFor(owner);

		mockMvc.perform(post("/api/v1/users/{userId}/receivers", owner.getId())
			.header("x-magic-token", token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"추가 보호자\",\"chatId\":\"new-chat\"}"))
			.andExpect(status().isOk());
		Guardian added = guardianRepository.findAll().stream()
			.filter(guardian -> "new-chat".equals(guardian.getChatId()))
			.findFirst()
			.orElseThrow();

		mockMvc.perform(delete("/api/v1/users/{userId}/receivers/{receiverId}", owner.getId(), otherGuardian.getId())
			.header("x-magic-token", token))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		assertTrue(guardianRepository.existsById(otherGuardian.getId()));

		mockMvc.perform(delete("/api/v1/users/{userId}/receivers/{receiverId}", owner.getId(), added.getId())
			.header("x-magic-token", token))
			.andExpect(status().isOk());
		assertFalse(guardianRepository.existsById(added.getId()));
	}

	@Test
	void medicineApiSupportsReactPayloadAndValidatesScheduleSpecificValues() throws Exception {
		User user = saveUser("복용자", "medicine-chat");
		String token = tokenFor(user);
		String weekly = "{\"name\":\"혈압약\",\"scheduleType\":\"WEEKLY\","
			+ "\"scheduleValue\":\"Monday,Wednesday\",\"startDate\":\"2030-01-01\",\"intakeTime\":\"09:00:00\"}";

		mockMvc.perform(post("/api/v1/medicine-groups/users/{userId}/medicines", user.getId())
			.header("x-magic-token", token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(weekly))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.startsWith("약 등록 완료! ID: ")));
		MedicineGroup medicine = medicineGroupRepository.findAllByUserIdAndIsActiveTrue(user.getId()).getFirst();
		assertEquals("Monday,Wednesday", medicine.getScheduleValue());

		String interval = "{\"name\":\"혈압약 수정\",\"scheduleType\":\"INTERVAL\","
			+ "\"scheduleValue\":\"3\",\"startDate\":\"2030-01-01\",\"intakeTime\":\"10:30:00\"}";
		mockMvc.perform(put("/api/v1/medicine-groups/medicines/{groupId}", medicine.getId())
			.header("x-magic-token", token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(interval))
			.andExpect(status().isOk());
		assertEquals(ScheduleType.INTERVAL, medicine.getScheduleType());
		assertEquals("3", medicine.getScheduleValue());

		String invalidInterval = interval.replace("\"3\"", "\"0\"");
		mockMvc.perform(put("/api/v1/medicine-groups/medicines/{groupId}", medicine.getId())
			.header("x-magic-token", token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(invalidInterval))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors.scheduleValue").exists());

		mockMvc.perform(delete("/api/v1/medicine-groups/medicines/{groupId}", medicine.getId())
			.header("x-magic-token", token))
			.andExpect(status().isOk())
			.andExpect(content().string("해당 약을 삭제했습니다."));
		assertFalse(medicine.isActive());
	}

	@Test
	void intakeApiCompletesPendingOnceAndLeavesInvalidStatesUnchanged() throws Exception {
		User user = saveUser("복용자", "intake-chat");
		String token = tokenFor(user);
		MedicineGroup medicine = saveMedicine(user);
		IntakeLog pending = saveIntake(user, medicine, IntakeStatus.PENDING);

		mockMvc.perform(post("/api/v1/intakes/{logId}/complete", pending.getId())
			.header("x-magic-token", token))
			.andExpect(status().isOk())
			.andExpect(content().string("약 복용 처리 완료"));
		assertEquals(IntakeStatus.TAKEN, pending.getStatus());
		assertNotNull(pending.getActualTakenTime());
		var completedAt = pending.getActualTakenTime();

		mockMvc.perform(post("/api/v1/intakes/{logId}/complete", pending.getId())
			.header("x-magic-token", token))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
		assertEquals(completedAt, pending.getActualTakenTime());

		IntakeLog missed = saveIntake(user, medicine, IntakeStatus.MISSED, LocalDate.now().minusDays(1));
		mockMvc.perform(post("/api/v1/intakes/{logId}/complete", missed.getId())
			.header("x-magic-token", token))
			.andExpect(status().isBadRequest());
		assertEquals(IntakeStatus.MISSED, missed.getStatus());
		assertEquals(null, missed.getActualTakenTime());

		mockMvc.perform(post("/api/v1/intakes/{logId}/complete", 999999L)
			.header("x-magic-token", token))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	private User saveUser(String name, String chatId) {
		User user = User.builder().name(name).build();
		user.addGuardian(Guardian.builder().user(user).name("보호자").chatId(chatId).build());
		return userRepository.saveAndFlush(user);
	}

	private String tokenFor(User user) {
		return authTokenService.issueAccessToken(Set.of(user.getId())).token();
	}

	private MedicineGroup saveMedicine(User user) {
		return medicineGroupRepository.saveAndFlush(MedicineGroup.builder()
			.user(user)
			.name("아침 약")
			.scheduleType(ScheduleType.DAILY)
			.startDate(LocalDate.now())
			.intakeTime(LocalTime.of(9, 0))
			.build());
	}

	private IntakeLog saveIntake(User user, MedicineGroup medicine, IntakeStatus status) {
		return saveIntake(user, medicine, status, LocalDate.now());
	}

	private IntakeLog saveIntake(User user, MedicineGroup medicine, IntakeStatus status, LocalDate intakeDate) {
		return intakeLogRepository.saveAndFlush(IntakeLog.builder()
			.user(user)
			.medicineGroup(medicine)
			.intakeDate(intakeDate)
			.intakeTime(LocalTime.of(9, 0))
			.status(status)
			.build());
	}
}
