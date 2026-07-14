package com.yakmogo.yakmogo.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yakmogo.yakmogo.domain.Guardian;
import com.yakmogo.yakmogo.domain.IntakeLog;
import com.yakmogo.yakmogo.domain.IntakeStatus;
import com.yakmogo.yakmogo.domain.MedicineGroup;
import com.yakmogo.yakmogo.domain.ScheduleType;
import com.yakmogo.yakmogo.domain.User;
import com.yakmogo.yakmogo.repository.IntakeLogRepository;
import com.yakmogo.yakmogo.repository.MedicineGroupRepository;
import com.yakmogo.yakmogo.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:auth-tests;MODE=MariaDB;DB_CLOSE_DELAY=-1",
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
class AuthIntegrationTests {
	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AuthTokenService authTokenService;

	@Autowired
	private MedicineGroupRepository medicineGroupRepository;

	@Autowired
	private IntakeLogRepository intakeLogRepository;

	@Test
	void rejectsArbitraryMagicToken() throws Exception {
		mockMvc.perform(get("/api/v1/users")
			.header("x-magic-token", "any-non-empty-string"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void allowsConfiguredAdministrator() throws Exception {
		mockMvc.perform(get("/api/v1/users")
			.header("x-admin-password", "test-admin"))
			.andExpect(status().isOk());
	}

	@Test
	void telegramTokenIsLimitedToIssuedUserIds() throws Exception {
		User allowedUser = saveUserWithGuardian("허용 사용자", "telegram-chat");
		User deniedUser = saveUserWithGuardian("다른 사용자", "different-chat");
		String proof = authTokenService.issueLoginProof("telegram-chat");
		String requestBody = objectMapper.writeValueAsString(new ProofRequest(proof));

		String responseBody = mockMvc.perform(post("/api/v1/auth/telegram")
			.contentType(MediaType.APPLICATION_JSON)
			.content(requestBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.expiresAt").exists())
			.andReturn()
			.getResponse()
			.getContentAsString();
		JsonNode response = objectMapper.readTree(responseBody);
		String accessToken = response.get("token").asText();

		mockMvc.perform(get("/api/v1/users/{userId}", allowedUser.getId())
			.header("x-magic-token", accessToken))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/users/{userId}", deniedUser.getId())
			.header("x-magic-token", accessToken))
			.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/users")
			.header("x-magic-token", accessToken))
			.andExpect(status().isForbidden());
	}

	@Test
	void rejectsTamperedLoginProof() throws Exception {
		mockMvc.perform(post("/api/v1/auth/telegram")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"proof\":\"tampered\"}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void rejectsBlankLoginProofAsInvalidInput() throws Exception {
		mockMvc.perform(post("/api/v1/auth/telegram")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"proof\":\" \"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void telegramTokenCannotMutateAnotherUsersMedicineOrIntake() throws Exception {
		User allowedUser = saveUserWithGuardian("허용 사용자", "telegram-chat");
		User deniedUser = saveUserWithGuardian("다른 사용자", "different-chat");
		MedicineGroup deniedMedicine = medicineGroupRepository.saveAndFlush(MedicineGroup.builder()
			.user(deniedUser)
			.name("다른 사용자의 약")
			.scheduleType(ScheduleType.DAILY)
			.startDate(LocalDate.now())
			.intakeTime(LocalTime.of(9, 0))
			.build());
		IntakeLog deniedLog = intakeLogRepository.saveAndFlush(IntakeLog.builder()
			.user(deniedUser)
			.medicineGroup(deniedMedicine)
			.intakeDate(LocalDate.now())
			.intakeTime(LocalTime.of(9, 0))
			.status(IntakeStatus.PENDING)
			.build());
		String accessToken = authTokenService.issueAccessToken(Set.of(allowedUser.getId())).token();

		mockMvc.perform(delete("/api/v1/medicine-groups/medicines/{groupId}", deniedMedicine.getId())
			.header("x-magic-token", accessToken))
			.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/v1/intakes/{logId}/complete", deniedLog.getId())
			.header("x-magic-token", accessToken))
			.andExpect(status().isForbidden());

		assertTrue(medicineGroupRepository.findById(deniedMedicine.getId()).orElseThrow().isActive());
		assertEquals(IntakeStatus.PENDING, intakeLogRepository.findById(deniedLog.getId()).orElseThrow().getStatus());
	}

	private User saveUserWithGuardian(String name, String chatId) {
		User user = User.builder().name(name).build();
		user.addGuardian(Guardian.builder()
			.user(user)
			.name("보호자")
			.chatId(chatId)
			.build());
		return userRepository.saveAndFlush(user);
	}

	private record ProofRequest(String proof) {
	}
}
