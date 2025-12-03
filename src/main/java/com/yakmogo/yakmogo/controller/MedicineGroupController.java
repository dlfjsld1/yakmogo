package com.yakmogo.yakmogo.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.yakmogo.yakmogo.domain.MedicineGroup;
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

	// 약 수정 api
	@PutMapping("/medicines/{groupId}") // Put은 전체 수정
	public ResponseEntity<String> updateMedicine(
		@PathVariable Long groupId,
		@RequestBody MedicineRequest request
	) {
		medicineGroupService.update(groupId, request);
		return ResponseEntity.ok("해당 약 정보가 수정되었습니다.");
	}

	// 약 삭제 API
	@DeleteMapping("/medicines/{groupId}")
	public ResponseEntity<String> deleteMedicine(@PathVariable Long groupId) {
		medicineGroupService.delete(groupId);
		return ResponseEntity.ok("해당 약을 삭제했습니다.");
	}

	// 특정 유저의 약 목록 조회 API
	@GetMapping("/users/{userId}/medicines")
	public ResponseEntity<List<MedicineGroup>> getMedicines(@PathVariable Long userId) {
		return ResponseEntity.ok(medicineGroupService.getMedicines(userId));
	}
}
