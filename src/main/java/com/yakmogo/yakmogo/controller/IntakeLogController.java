package com.yakmogo.yakmogo.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.yakmogo.yakmogo.service.IntakeLogService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/intakes")
@RequiredArgsConstructor
public class IntakeLogController {
	private final IntakeLogService intakeLogService;

	//복용 완료 api
	@PostMapping("/{logId}/complete")
	public ResponseEntity<String> completeIntake(@PathVariable Long logId) {
		boolean result = intakeLogService.markAsTaken(logId);

		if (result) {
			return ResponseEntity.ok("약 복용 처리 완료");
		} else {
			return ResponseEntity.badRequest().body("이미 처리됐거나 잘못된 요청");
		}
	}

}
