package com.yakmogo.yakmogo.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yakmogo.yakmogo.domain.IntakeLog;
import com.yakmogo.yakmogo.domain.MedicineGroup;
import com.yakmogo.yakmogo.domain.ScheduleType;
import com.yakmogo.yakmogo.domain.IntakeStatus;
import com.yakmogo.yakmogo.repository.IntakeLogRepository;
import com.yakmogo.yakmogo.repository.MedicineGroupRepository;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SchedulerService {

	private final MedicineGroupRepository medicineGroupRepository;
	private final IntakeLogRepository intakeLogRepository;

	private final TelegramService telegramService;

	// 1. 새벽 2시 13분에 오늘 먹을 약 생성 (기존 로직 유지)
	@Scheduled(cron = "0 13 2 * * *")
	public void generateDailyLogs() {
		LocalDate today = LocalDate.now();
		List<MedicineGroup> groups = medicineGroupRepository.findAllByIsActiveTrue();

		for (MedicineGroup group : groups) {
			if (isIntakeDay(group, today)) {
				// 중복 생성 방지
				if (!intakeLogRepository.existsByMedicineGroupAndIntakeDate(group, today)) {
					IntakeLog log = IntakeLog.builder()
						.user(group.getUser())
						.medicineGroup(group)
						.intakeDate(today)
						.intakeTime(group.getIntakeTime())
						.status(IntakeStatus.PENDING)
						.build();
					intakeLogRepository.save(log);
					System.out.println("[생성 완료] " + group.getUser().getName() + "님의 " + group.getName());
				}
			}
		}
	}

	@Scheduled(cron = "0 * * * * *")
	@Transactional // DB 상태 변경(notifiedCount 증가)을 위해 필수!
	public void checkMissedDose() {
		LocalTime now = LocalTime.now().withSecond(0).withNano(0);
		LocalDate today = LocalDate.now();

		List<IntakeLog> pendingLogs = intakeLogRepository.findPendingLogs(today, now);

		for (IntakeLog logInfo : pendingLogs) {
			long minutesOverdue = ChronoUnit.MINUTES.between(logInfo.getIntakeTime(), now);
			int currentCount = logInfo.getNotifiedCount();

			// 1. 첫 알람 - 아직 한 번도 알람을 안 보냈는데 시간이 이미 지났다면 즉시 발송
			if (currentCount == 0 && minutesOverdue >= 0) {
				sendNormalAlert(logInfo);
				logInfo.incrementNotifiedCount(); // 0 -> 1
			}
			// 2. 이미 최소 1회 알람이 나갔고, 특정 지연 시점에 도달했을 때만 발송
			else if (currentCount > 0) {
				if (minutesOverdue == 30) {
					sendNaggingAlertLevel1(logInfo);
					logInfo.incrementNotifiedCount();
				} else if (minutesOverdue == 60) {
					sendNaggingAlertLevel2(logInfo);
					logInfo.incrementNotifiedCount();
				} else if (minutesOverdue > 60 && minutesOverdue <= 360 && minutesOverdue % 60 == 0) {
					sendNaggingAlertLevel3(logInfo, (int)(minutesOverdue / 60));
					logInfo.incrementNotifiedCount();
				}
			}
		}
	}

	// 날짜 계산 로직
	private boolean isIntakeDay(MedicineGroup group, LocalDate today) {
		if (group.getScheduleType() == ScheduleType.DAILY) return true;

		if (group.getScheduleType() == ScheduleType.WEEKLY) {
			return group.getScheduleValue().contains(today.getDayOfWeek().name());
		}

		if (group.getScheduleType() == ScheduleType.INTERVAL) {
			long diff = ChronoUnit.DAYS.between(group.getStartDate(), today);
			int interval = Integer.parseInt(group.getScheduleValue());
			return diff >= 0 && diff % interval == 0;
		}

		return false;
	}

	// ---알림 발송 메서드들---

	private void sendNormalAlert(IntakeLog logInfo) {
		String photoUrl = "https://images.unsplash.com/photo-1518155317743-a8ff43ea6a5f?q=80&w=600&auto=format&fit=crop";

		logInfo.getUser().getGuardians().forEach(guardian -> {
			String caption = String.format(
				"🦉 [약모고 정시 알림]\n\n지금은 %s님이 '%s'을(를) 드실 시간입니다! 💊\n복용 후 아래 약 복용 완료 버튼을 눌러주세요.",
				logInfo.getUser().getName(), logInfo.getMedicineGroup().getName()
			);
			telegramService.sendPhotoWithButton(guardian.getChatId(), photoUrl, caption, logInfo.getId());
		});
	}

	private void sendNaggingAlertLevel1(IntakeLog logInfo) {
		String photoUrl = "https://images.unsplash.com/photo-1518155317743-a8ff43ea6a5f?q=80&w=600&auto=format&fit=crop";

		logInfo.getUser().getGuardians().forEach(guardian -> {
			String caption = String.format(
				"🦉 [경고: 30분 경과]\n\n%s님... '%s' 아직 안 드셨습니까?\n제가 지켜보고 있습니다... 빨리 드시고 아래 버튼 눌러주세요.",
				logInfo.getUser().getName(), logInfo.getMedicineGroup().getName()
			);
			telegramService.sendPhotoWithButton(guardian.getChatId(), photoUrl, caption, logInfo.getId());
		});
	}

	private void sendNaggingAlertLevel2(IntakeLog logInfo) {
		String photoUrl = "https://images.unsplash.com/photo-1550159930-40066082a4fc?q=80&w=600&auto=format&fit=crop";

		logInfo.getUser().getGuardians().forEach(guardian -> {
			String caption = String.format(
				"🔥 [긴급: 1시간 경과!!!]\n\n아니 %s님!!! '%s' 왜 아직도 안 드시는 겁니까!!!\n당장 입에 털어 넣으세요!!!\n아래 버튼 눌렀!! 💊🔥🔥",
				logInfo.getUser().getName(), logInfo.getMedicineGroup().getName()
			);
			telegramService.sendPhotoWithButton(guardian.getChatId(), photoUrl, caption, logInfo.getId());
		});
	}

	private void sendNaggingAlertLevel3(IntakeLog logInfo, int hoursOverdue) {
		String photoUrl = "https://images.unsplash.com/photo-1550159930-40066082a4fc?q=80&w=600&auto=format&fit=crop";

		logInfo.getUser().getGuardians().forEach(guardian -> {
			String caption = String.format(
				"🔥🔥 [최후통첩: %d시간 경과]\n\n%s님!!! '%s' 안 드신 지 %d시간이나 지났습니다!!!\n제발 저를 그만 시험하시고 당장 💊아래 버튼을 눌러주세요!!!",
				hoursOverdue, logInfo.getUser().getName(), logInfo.getMedicineGroup().getName(), hoursOverdue
			);
			telegramService.sendPhotoWithButton(guardian.getChatId(), photoUrl, caption, logInfo.getId());
		});
	}

	@Scheduled(cron = "59 59 23 * * *")
	@Transactional
	public void cleanupPendingLogs() {
		int updatedCount = intakeLogRepository.updateMissedStatus(LocalDate.now());
		log.info("[자정 정리 완료] 미복용 처리된 로그 개수: {}", updatedCount);
	}
}