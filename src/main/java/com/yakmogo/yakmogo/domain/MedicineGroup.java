package com.yakmogo.yakmogo.domain;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.persistence.*;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MedicineGroup {

	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private User user;

	//약 이름
	private String name;

	@Enumerated(EnumType.STRING)
	private ScheduleType scheduleType;

	// WEEKLY의 경우 예시: "MONDAY", "WEDNESDAY" (월요일 수요일)
	// INTERVAL의 경우 예시: "3" (3일마다)
	private String scheduleValue;

	// 격일 계산 기준점
	private LocalDate startDate;

	//복용 시간 예시: 08:00
	private LocalTime intakeTime;

	//복용 여부
	private boolean isActive;

	@Builder
	public MedicineGroup(
		User user,
		String name,
		ScheduleType scheduleType,
		String scheduleValue,
		LocalDate startDate,
		LocalTime intakeTime)
	{
		this.user = user;
		this.name = name;
		this.scheduleType = scheduleType;
		this.scheduleValue = scheduleValue;
		this.startDate = startDate;
		this.intakeTime = intakeTime;
		this.isActive = true;
	}
}
