package com.yakmogo.yakmogo.controller;

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
}