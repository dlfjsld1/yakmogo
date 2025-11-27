package com.yakmogo.yakmogo.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import com.yakmogo.yakmogo.domain.ScheduleType;

public record MedicineRequest(
	//약이름
	String name,
	//DAILY, WEEKLY, INTERVAL
	ScheduleType scheduleType,
	//"Monday" or "3" or null
	String scheduleValue,
	//시작일
	LocalDate startDate,
	//복용시간
	LocalTime intakeTime
) {}
