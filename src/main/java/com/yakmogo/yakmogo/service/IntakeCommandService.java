package com.yakmogo.yakmogo.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yakmogo.yakmogo.auth.AuthorizationService;
import com.yakmogo.yakmogo.auth.ForbiddenException;
import com.yakmogo.yakmogo.domain.IntakeLog;
import com.yakmogo.yakmogo.domain.IntakeStatus;
import com.yakmogo.yakmogo.repository.GuardianRepository;
import com.yakmogo.yakmogo.repository.IntakeLogRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class IntakeCommandService {
	private final IntakeLogRepository intakeLogRepository;
	private final GuardianRepository guardianRepository;
	private final AuthorizationService authorizationService;
	private final Clock applicationClock;

	public IntakeCompletion complete(Long logId) {
		IntakeLog log = findLog(logId);
		authorizationService.requireUserAccess(log.getUser().getId());
		return completePending(log);
	}

	public IntakeCompletion completeFromTelegram(Long logId, String chatId) {
		IntakeLog log = findLog(logId);
		if (!guardianRepository.existsByUserIdAndChatId(log.getUser().getId(), chatId)) {
			throw new ForbiddenException("해당 복용 기록을 처리할 권한이 없습니다.");
		}
		return completePending(log);
	}

	private IntakeLog findLog(Long logId) {
		return intakeLogRepository.findByIdWithUserAndGroup(logId)
			.orElseThrow(() -> new ResourceNotFoundException("해당 복용 기록이 없습니다. ID=" + logId));
	}

	private IntakeCompletion completePending(IntakeLog log) {
		if (log.getStatus() != IntakeStatus.PENDING) {
			throw new InvalidIntakeTransitionException(
				"대기 중인 복용 기록만 완료할 수 있습니다. 현재 상태=" + log.getStatus()
			);
		}
		LocalDateTime completedAt = LocalDateTime.now(applicationClock);
		log.markAsTaken(completedAt);
		return new IntakeCompletion(
			log.getId(),
			log.getUser().getName(),
			log.getMedicineGroup().getName(),
			completedAt
		);
	}
}
