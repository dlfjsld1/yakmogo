package com.yakmogo.yakmogo.controller;

import com.yakmogo.yakmogo.domain.User;
import com.yakmogo.yakmogo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private final UserRepository userRepository;

	@GetMapping("/telegram")
	public ResponseEntity<?> loginViaTelegram(@RequestParam("chatId") String chatId) {
		log.info("[Technical refresh phase] 텔레그램 인증 시도 - chatId: {}", chatId);

		// 1. findByGuardians_ChatId로 유저들 조회
		List<User> managedUsers = userRepository.findByGuardians_ChatId(chatId);

		// 2. 등록되지 않은 경우 401
		if (managedUsers.isEmpty()) {
			log.warn("[인증 실패] 등록되지 않은 chatId: {}", chatId);
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body("등록되지 않은 사용자입니다. 관리자에게 문의하세요.");
		}

		// 3. 성공 시: 유저 정보(ID, 이름) 반환

		List<Map<String, Object>> userList = managedUsers.stream().map(user -> {
			Map<String, Object> userMap = new HashMap<>();
			userMap.put("id", user.getId());
			userMap.put("name", user.getName());
			return userMap;
		}).collect(Collectors.toList());

		// 임시 OTT 토큰
		String magicToken = UUID.randomUUID().toString();

		Map<String, Object> response = new HashMap<>();
		response.put("token", magicToken);
		response.put("users", userList);

		log.info("[인증 성공] 보호자(chatId: {})가 관리하는 유저 수: {}명", chatId, managedUsers.size());
		return ResponseEntity.ok(response);
	}
}