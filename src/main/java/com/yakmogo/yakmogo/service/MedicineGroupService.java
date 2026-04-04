package com.yakmogo.yakmogo.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
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
		if (medicineGroupRepository.existsByUserIdAndNameAndIsActiveTrue(userId, request.name())) {
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
			return group.getScheduleValue().toUpperCase().contains(today.getDayOfWeek().name());
		}

		if (group.getScheduleType() == ScheduleType.INTERVAL) {
			long diff = ChronoUnit.DAYS.between(group.getStartDate(), today);
			int interval = Integer.parseInt(group.getScheduleValue());
			return diff >= 0 && diff % interval == 0;
		}
		return false;
	}

	// 약 삭제
	public void delete(Long groupId) {
		MedicineGroup group = medicineGroupRepository.findById(groupId)
			.orElseThrow(() -> new IllegalArgumentException("해당 약이 없습니다."));

		if (!group.isActive()) {
			throw new IllegalArgumentException("이미 복용 중단된 약입니다.");
		}

		// Soft Delete
		group.deactivate();

		intakeLogRepository.cancelPendingLogsByGroupId(groupId, IntakeStatus.CANCELLED);

		System.out.println(group.getName() + "을(를) 복용 중단 처리했습니다.");
	}

	// 약 정보 수정
	public void update(Long groupId, MedicineRequest request) {
		MedicineGroup group = medicineGroupRepository.findById(groupId)
			.orElseThrow(() -> new IllegalArgumentException("해당 약이 없습니다."));

		// 정보 업데이트
		group.updateInfo(
			request.name(),
			request.scheduleType(),
			request.scheduleValue(),
			request.startDate(),
			request.intakeTime()
		);

		System.out.println(group.getName() + "의 복용 정보 수정 완료");
	}

	// 특정 유저의 약 목록 가져오기
	public List<MedicineGroup> getMedicines(Long userId) {
		return medicineGroupRepository.findAllByUserIdAndIsActiveTrue(userId);
	}

	@Scheduled(cron = "1 0 3 * * *") // 매일 00:03:01에 실행
	public void generateDailyIntakeLogs() {
		LocalDate today = LocalDate.now();

		// 1. 현재 복용 중인 모든 약(isActive=true) 조회
		List<MedicineGroup> allActiveGroups = medicineGroupRepository.findAllByIsActiveTrue();

		int createdCount = 0;
		for (MedicineGroup group : allActiveGroups) {
			// 2. 오늘이 이 약을 복용하는 날인지 확인
			if (shouldEatToday(group, today)) {

				// 3. 중복 방지: 이미 오늘치 데이터가 있으면 패스
				boolean exists = intakeLogRepository.existsByMedicineGroupIdAndIntakeDate(group.getId(), today);

				if (!exists) {
					IntakeLog log = IntakeLog.builder()
						.user(group.getUser())
						.medicineGroup(group)
						.intakeDate(today)
						.intakeTime(group.getIntakeTime())
						.status(IntakeStatus.PENDING)
						.build();

					intakeLogRepository.save(log);
					createdCount++;
				}
			}
		}
		System.out.println("[새벽 배치 완료] " + today + " 복용 로그 " + createdCount + "건 생성 완료! 💊");
	}
}
