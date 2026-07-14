package com.yakmogo.yakmogo.service;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yakmogo.yakmogo.domain.IntakeLog;
import com.yakmogo.yakmogo.domain.IntakeStatus;
import com.yakmogo.yakmogo.domain.MedicineGroup;
import com.yakmogo.yakmogo.repository.IntakeLogRepository;
import com.yakmogo.yakmogo.repository.MedicineGroupRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DailyIntakeLogService {
	private final MedicineGroupRepository medicineGroupRepository;
	private final IntakeLogRepository intakeLogRepository;
	private final MedicineSchedulePolicy medicineSchedulePolicy;

	@Transactional
	public int generateFor(LocalDate date) {
		int createdCount = 0;
		for (MedicineGroup group : medicineGroupRepository.findAllByIsActiveTrue()) {
			if (!medicineSchedulePolicy.shouldTakeOn(group, date)
				|| intakeLogRepository.existsByMedicineGroupIdAndIntakeDate(group.getId(), date)) {
				continue;
			}
			intakeLogRepository.save(IntakeLog.builder()
				.user(group.getUser())
				.medicineGroup(group)
				.intakeDate(date)
				.intakeTime(group.getIntakeTime())
				.status(IntakeStatus.PENDING)
				.build());
			createdCount++;
		}
		return createdCount;
	}
}
