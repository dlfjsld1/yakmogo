package com.yakmogo.yakmogo.service;

import org.springframework.stereotype.Service;

import com.yakmogo.yakmogo.domain.IntakeLog;
import com.yakmogo.yakmogo.domain.IntakeStatus;
import com.yakmogo.yakmogo.repository.IntakeLogRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class IntakeLogService {
	private final IntakeLogRepository intakeLogRepository;

	// 약 복용 처리
	public boolean markAsTaken(Long logId) {
		// 기록 검색
		IntakeLog log = intakeLogRepository.findById(logId)
			.orElseThrow(() -> new IllegalArgumentException("해당 기록이 없습니다. ID=" + logId));

		// 중복 체크
		if (log.getStatus() == IntakeStatus.TAKEN) {
			System.out.println("이미 복용 완료된 약입니다.");
			return false;
		}

		// TAKEN으로 상태 변경
		log.markAsTaken();

		System.out.println("복용 처리 완료: " + log.getMedicineGroup().getName());
		return true;
	}

}
