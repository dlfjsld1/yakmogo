package com.yakmogo.yakmogo.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.yakmogo.yakmogo.domain.IntakeLog;
import com.yakmogo.yakmogo.domain.IntakeStatus;
import com.yakmogo.yakmogo.domain.MedicineGroup;
import com.yakmogo.yakmogo.domain.ScheduleType;
import com.yakmogo.yakmogo.repository.IntakeLogRepository;
import com.yakmogo.yakmogo.repository.MedicineGroupRepository;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class SchedulerService {
	private final MedicineGroupRepository medicineGroupRepository;
	private final IntakeLogRepository intakeLogRepository;
	private final TelegramSender telegramSender;

	//새벽 2시 13분(Thundering Herd 회피)에 오늘 먹을 약 생성
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

	// 테스트용 감시자: 10초마다 확인
	// @Scheduled(cron = "0/10 * * * * *")
	// 감시자: 매시 7분, 37분마다 미복용 체크
	@Scheduled(cron = "0 7/37 * * * *")
	public void checkMissedDose() {
		LocalDateTime now = LocalDateTime.now();
		List<IntakeLog> pendingLogs = intakeLogRepository.findPendingLogs(now.toLocalDate(), now.toLocalTime());

		for (IntakeLog log : pendingLogs) {
			long minutesOverdue = ChronoUnit.MINUTES.between(log.getIntakeTime(), now.toLocalTime());
			String userName = log.getUser().getName();
			String medicineName = log.getMedicineGroup().getName();

			if (minutesOverdue >= 60) {
				// 1시간 지났을 때 보호자에게 텔레그램 발송
				String msg = String.format("[알림] %s님이 '%s' 복용 시간을 1시간 넘겼습니다! 확인이 필요합니다.", userName, medicineName);
				telegramSender.sendToGuardians(log.getUser().getGuardianChatIds(), msg);
			} else {
				// 1시간 미만일 때 로그만 찍음(추후 앱 푸시로 변경)
				System.out.println("[알림] " + userName + "님, " + medicineName + " 드실 시간입니다.");
			}
		}
	}

	// 날짜 계산
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
}
