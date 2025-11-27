package com.yakmogo.yakmogo.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.yakmogo.yakmogo.dto.MedicineRequest;
import com.yakmogo.yakmogo.service.MedicineGroupService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MedicineGroupController {
	private final MedicineGroupService medicineGroupService;

	// 약 등록 api
	@PostMapping("/users/{userId}/medicines")
	public ResponseEntity<String> registerMedicine(
		@PathVariable Long userId,
		@RequestBody MedicineRequest request
	) {
		Long groupId = medicineGroupService.register(userId, request);
		return ResponseEntity.ok("약 등록 완료! ID: " + groupId);
	}
}
