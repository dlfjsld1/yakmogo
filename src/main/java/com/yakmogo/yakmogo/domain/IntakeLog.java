package com.yakmogo.yakmogo.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import jakarta.persistence.Entity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IntakeLog {

	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private User user;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "medicine_group_id")
	private MedicineGroup medicineGroup;

	// 복용날짜
	private LocalDate intakeDate;

	// 예정시간
	private LocalTime intakeTime;

	@Enumerated(EnumType.STRING)
	private IntakeStatus status;

	// 실제 먹은 시간
	private LocalDateTime actualTakenTime;

	// 약 먹을 때 상태 변경
	public void markAsTaken() {
		this.status = IntakeStatus.TAKEN;
		this.actualTakenTime = LocalDateTime.now();
	}

	@Builder
	public IntakeLog(
		User user,
		MedicineGroup medicineGroup,
		LocalDate intakeDate,
		LocalTime intakeTime,
		IntakeStatus status
	) {
		this.user = user;
		this.medicineGroup = medicineGroup;
		this.intakeDate = intakeDate;
		this.intakeTime = intakeTime;
		this.status = status;
	}
}
