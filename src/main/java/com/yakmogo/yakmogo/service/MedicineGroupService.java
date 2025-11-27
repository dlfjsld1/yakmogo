package com.yakmogo.yakmogo.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yakmogo.yakmogo.domain.IntakeLog;
import com.yakmogo.yakmogo.domain.IntakeStatus;
import com.yakmogo.yakmogo.domain.MedicineGroup;
import com.yakmogo.yakmogo.domain.ScheduleType;
import com.yakmogo.yakmogo.domain.User;
import com.yakmogo.yakmogo.dto.MedicineRequest;
import com.yakmogo.yakmogo.repository.IntakeLogRepository;
import com.yakmogo.yakmogo.repository.MedicineGroupRepository;
import com.yakmogo.yakmogo.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class MedicineGroupService {
	private final MedicineGroupRepository medicineGroupRepository;
	private final UserRepository userRepository;
	private final IntakeLogRepository intakeLogRepository;

	//약 등록
	public Long register(Long userId, MedicineRequest request) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new IllegalArgumentException("없는 유저입니다."));

		// 같은 유저 같은 약 중복 체크
		if (medicineGroupRepository.existsByUserAndName(user, request.name())) {
			throw new IllegalArgumentException(request.name() + "는 이미 등록한 약입니다");
		}

		//약 그룹 저장
		MedicineGroup group = MedicineGroup.builder()
			.user(user)
			.name(request.name())
			.scheduleType(request.scheduleType())
			.scheduleValue(request.scheduleValue())
			.startDate(request.startDate())
			.intakeTime(request.intakeTime())
			.build();

		medicineGroupRepository.save(group);

		//오늘 바로 먹어야 하는 약이면 즉시 생성
		LocalDate today = LocalDate.now();
		if (shouldEatToday(group, today)) {
			IntakeLog log = IntakeLog.builder()
				.user(user)
				.medicineGroup(group)
				.intakeDate(today)
				.intakeTime(group.getIntakeTime())
				.status(IntakeStatus.PENDING)
				.build();

			intakeLogRepository.save(log);
			System.out.println("오늘 복용 기록 생성 완료");
		}

		return group.getId();
	}

	// 날짜 계산 로직
	private boolean shouldEatToday(MedicineGroup group, LocalDate today) {
		if (group.getStartDate().isAfter(today)) return false; // 시작일이 미래면 패스

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
