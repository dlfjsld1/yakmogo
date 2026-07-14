package com.yakmogo.yakmogo.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Positive;

import com.yakmogo.yakmogo.service.IntakeCommandService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/intakes")
@RequiredArgsConstructor
@Validated
public class IntakeLogController {
	private final IntakeCommandService intakeCommandService;

	//복용 완료 api
	@PostMapping("/{logId}/complete")
	public ResponseEntity<String> completeIntake(@PathVariable @Positive Long logId) {
		intakeCommandService.complete(logId);

		return ResponseEntity.ok("약 복용 처리 완료");
	}

}
