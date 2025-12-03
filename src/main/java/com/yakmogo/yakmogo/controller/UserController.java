package com.yakmogo.yakmogo.controller;

import java.util.List;

import com.yakmogo.yakmogo.domain.User;
import com.yakmogo.yakmogo.dto.ReceiverCreateRequest;
import com.yakmogo.yakmogo.dto.UserCreateRequest;
import com.yakmogo.yakmogo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	// 유저 등록 API
	@PostMapping
	public ResponseEntity<String> registerUser(@RequestBody UserCreateRequest request) {
		Long userId = userService.registerUser(request);
		return ResponseEntity.ok("유저 등록 완료! ID: " + userId);
	}

	// 수신자 추가 API
	@PostMapping("/{userId}/receivers")
	public ResponseEntity<String> addReceiver(
		@PathVariable Long userId,
		@RequestBody ReceiverCreateRequest request
	) {
		userService.addReceiver(userId, request);
		return ResponseEntity.ok("알림 수신자가 추가되었습니다!");
	}

	// 유저 상세 조회 API
	@GetMapping("/{userId}")
	public ResponseEntity<User> getUser(@PathVariable Long userId) {
		return ResponseEntity.ok(userService.getUser(userId));
	}

	// 알림 수신자 목록 조회 API
	@GetMapping
	public ResponseEntity<List<User>> getAllUsers() {
		return ResponseEntity.ok(userService.getAllUsers());
	}

	// 복용자 삭제 API
	@DeleteMapping("/{userId}")
	public ResponseEntity<String> deleteUser(@PathVariable Long userId) {
		userService.deleteUser(userId);
		return ResponseEntity.ok("복용자 정보가 완전히 삭제되었습니다.");
	}

	// 수신자 삭제 API
	@DeleteMapping("/{userId}/receivers/{receiverId}")
	public ResponseEntity<String> deleteReceiver(
		@PathVariable Long userId,
		@PathVariable Long receiverId
	) {
		userService.deleteReceiver(userId, receiverId);
		return ResponseEntity.ok("알림 수신자가 삭제되었습니다.");
	}
}