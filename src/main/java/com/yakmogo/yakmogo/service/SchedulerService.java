package com.yakmogo.yakmogo.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yakmogo.yakmogo.domain.Guardian;
import com.yakmogo.yakmogo.domain.IntakeLog;
import com.yakmogo.yakmogo.domain.NotificationDelivery;
import com.yakmogo.yakmogo.repository.IntakeLogRepository;
import com.yakmogo.yakmogo.repository.NotificationDeliveryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulerService {
	private final DailyIntakeLogService dailyIntakeLogService;
	private final IntakeLogRepository intakeLogRepository;
	private final NotificationDeliveryRepository notificationDeliveryRepository;
	private final MedicationAlertPolicy medicationAlertPolicy;
	private final TelegramService telegramService;
	private final Clock applicationClock;

	@Scheduled(cron = "0 13 2 * * *")
	public void generateDailyLogs() {
		LocalDate today = LocalDate.now(applicationClock);
		int createdCount = dailyIntakeLogService.generateFor(today);
		log.info("[일일 복용 기록 생성] date={}, created={}", today, createdCount);
	}

	@Scheduled(cron = "0 * * * * *")
	@Transactional
	public void checkMissedDose() {
		LocalDateTime now = LocalDateTime.now(applicationClock).withSecond(0).withNano(0);
		List<IntakeLog> pendingLogs = intakeLogRepository.findPendingLogs(now.toLocalDate(), now.toLocalTime());
		Set<String> retriedTargets = retryDueDeliveries(now);

		for (IntakeLog intakeLog : pendingLogs) {
			medicationAlertPolicy.currentAlert(intakeLog, now).ifPresent(alert ->
				intakeLog.getUser().getGuardians().stream()
					.filter(guardian -> !retriedTargets.contains(targetKey(intakeLog, guardian)))
					.forEach(guardian -> deliver(intakeLog, guardian, alert, now))
			);
		}
	}

	private Set<String> retryDueDeliveries(LocalDateTime now) {
		Set<String> retriedTargets = new HashSet<>();
		for (NotificationDelivery delivery : notificationDeliveryRepository.findDueRetries(now)) {
			medicationAlertPolicy.alertForKey(delivery.getAlertKey()).ifPresent(alert -> {
				retriedTargets.add(targetKey(delivery.getIntakeLog(), delivery.getGuardian()));
				deliverExisting(delivery, alert, now);
			});
		}
		return retriedTargets;
	}

	private void deliver(IntakeLog intakeLog, Guardian guardian, MedicationAlert alert, LocalDateTime now) {
		NotificationDelivery delivery = notificationDeliveryRepository
			.findByIntakeLogIdAndGuardianIdAndAlertKey(intakeLog.getId(), guardian.getId(), alert.key())
			.orElseGet(() -> notificationDeliveryRepository.save(
				NotificationDelivery.pending(intakeLog, guardian, alert.key())
			));

		if (!delivery.canAttempt(now)) {
			return;
		}

		deliverExisting(delivery, alert, now);
	}

	private void deliverExisting(NotificationDelivery delivery, MedicationAlert alert, LocalDateTime now) {
		if (!delivery.canAttempt(now)) {
			return;
		}
		IntakeLog intakeLog = delivery.getIntakeLog();
		Guardian guardian = delivery.getGuardian();
		TelegramDeliveryResult result = telegramService.sendMedicationAlert(intakeLog, guardian, alert);
		if (result.delivered()) {
			delivery.recordSuccess(now);
			log.info("[Telegram 알림 성공] logId={}, guardianId={}, alertKey={}",
				intakeLog.getId(), guardian.getId(), alert.key());
		} else {
			delivery.recordFailure(now, result.failureReason());
			log.warn("[Telegram 알림 실패] logId={}, guardianId={}, alertKey={}, attempts={}, status={}",
				intakeLog.getId(), guardian.getId(), alert.key(), delivery.getAttemptCount(), delivery.getStatus());
		}
	}

	private String targetKey(IntakeLog intakeLog, Guardian guardian) {
		return intakeLog.getId() + ":" + guardian.getId();
	}

	@Scheduled(cron = "59 59 23 * * *")
	@Transactional
	public void cleanupPendingLogs() {
		int updatedCount = intakeLogRepository.updateMissedStatus(LocalDate.now(applicationClock));
		log.info("[자정 정리 완료] 미복용 처리된 로그 개수: {}", updatedCount);
	}
}
