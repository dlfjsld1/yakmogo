package com.yakmogo.yakmogo.controller;

import com.yakmogo.yakmogo.auth.AuthTokenService;
import com.yakmogo.yakmogo.auth.IssuedToken;
import com.yakmogo.yakmogo.domain.User;
import com.yakmogo.yakmogo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private final UserRepository userRepository;
	private final AuthTokenService authTokenService;

	@PostMapping("/telegram")
	public ResponseEntity<?> loginViaTelegram(@Valid @RequestBody TelegramLoginRequest request) {
		String chatId = authTokenService.verifyLoginProof(request.proof());

		// 1. findByGuardians_ChatId로 유저들 조회
		List<User> managedUsers = userRepository.findByGuardians_ChatId(chatId);

		// 2. 등록되지 않은 경우 401
		if (managedUsers.isEmpty()) {
			log.warn("[인증 실패] 등록되지 않은 Telegram 사용자");
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(ApiError.of("UNAUTHORIZED", "등록되지 않은 사용자입니다. 관리자에게 문의하세요."));
		}

		// 3. 성공 시: 유저 정보(ID, 이름) 반환

		List<Map<String, Object>> userList = managedUsers.stream().map(user -> {
			Map<String, Object> userMap = new HashMap<>();
			userMap.put("id", user.getId());
			userMap.put("name", user.getName());
			return userMap;
		}).collect(Collectors.toList());

		Set<Long> allowedUserIds = managedUsers.stream()
			.map(User::getId)
			.collect(Collectors.toSet());
		IssuedToken issuedToken = authTokenService.issueAccessToken(allowedUserIds);

		Map<String, Object> response = new HashMap<>();
		response.put("token", issuedToken.token());
		response.put("expiresAt", issuedToken.expiresAt());
		response.put("users", userList);

		log.info("[인증 성공] Telegram 사용자가 관리하는 유저 수: {}명", managedUsers.size());
		return ResponseEntity.ok(response);
	}

	public record TelegramLoginRequest(
		@NotBlank(message = "Telegram 로그인 증명은 필수입니다.")
		String proof
	) {
	}
}
