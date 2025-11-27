package com.yakmogo.yakmogo.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yakmogo.yakmogo.domain.IntakeLog;
import com.yakmogo.yakmogo.domain.IntakeStatus;
import com.yakmogo.yakmogo.repository.IntakeLogRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class IntakeLogService {
	private final IntakeLogRepository intakeLogRepository;

	// 약 복용 처리
	public void markAsTaken(Long logId) {
		// 기록 검색
		IntakeLog log = intakeLogRepository.findById(logId)
			.orElseThrow(() -> new IllegalArgumentException("해당 기록이 없습니다. ID=" + logId));

		// 중복 체크
		if (log.getStatus() == IntakeStatus.TAKEN) {
			System.out.println("이미 복용 완료된 약입니다.");
			throw new IllegalArgumentException("이미 처리됐거나 잘못된 요청입니다.");
		}

		// TAKEN으로 상태 변경
		log.markAsTaken();

		System.out.println("복용 처리 완료: " + log.getMedicineGroup().getName());
	}

}
