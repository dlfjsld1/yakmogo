package com.yakmogo.yakmogo.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.yakmogo.yakmogo.domain.MedicineGroup;
import com.yakmogo.yakmogo.domain.ScheduleType;

@Component
public class MedicineSchedulePolicy {
	public boolean shouldTakeOn(MedicineGroup group, LocalDate date) {
		if (group.getStartDate().isAfter(date)) {
			return false;
		}
		return switch (group.getScheduleType()) {
			case DAILY -> true;
			case WEEKLY -> containsDay(group.getScheduleValue(), date.getDayOfWeek());
			case INTERVAL -> isIntervalDay(group.getStartDate(), date, group.getScheduleValue());
		};
	}

	private boolean containsDay(String value, DayOfWeek dayOfWeek) {
		if (value == null) {
			return false;
		}
		return Arrays.stream(value.split(","))
			.map(String::trim)
			.map(day -> day.toUpperCase(Locale.ROOT))
			.anyMatch(dayOfWeek.name()::equals);
	}

	private boolean isIntervalDay(LocalDate startDate, LocalDate date, String value) {
		int interval;
		try {
			interval = Integer.parseInt(value);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("복용 간격은 1 이상의 정수여야 합니다.");
		}
		if (interval < 1) {
			throw new IllegalArgumentException("복용 간격은 1 이상의 정수여야 합니다.");
		}
		long difference = ChronoUnit.DAYS.between(startDate, date);
		return difference % interval == 0;
	}
}
