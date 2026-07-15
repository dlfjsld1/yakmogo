package com.yakmogo.yakmogo.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.yakmogo.yakmogo.domain.ScheduleType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@ValidMedicineSchedule
public record MedicineRequest(
	//약이름
	@NotBlank(message = "약 이름은 필수입니다.")
	@Size(max = 255, message = "약 이름은 255자 이하여야 합니다.")
	String name,
	//DAILY, WEEKLY, INTERVAL
	@NotNull(message = "복용 일정 유형은 필수입니다.")
	ScheduleType scheduleType,
	//"Monday" or "3" or null
	String scheduleValue,
	//시작일
	@NotNull(message = "복용 시작일은 필수입니다.")
	LocalDate startDate,
	//복용시간
	@NotNull(message = "복용 시간은 필수입니다.")
	LocalTime intakeTime
) {}
