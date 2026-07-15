package com.yakmogo.yakmogo.dto;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.Locale;

import com.yakmogo.yakmogo.domain.ScheduleType;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MedicineScheduleValidator implements ConstraintValidator<ValidMedicineSchedule, MedicineRequest> {
	@Override
	public boolean isValid(MedicineRequest request, ConstraintValidatorContext context) {
		if (request == null || request.scheduleType() == null) {
			return true;
		}

		boolean valid = switch (request.scheduleType()) {
			case DAILY -> request.scheduleValue() == null || request.scheduleValue().isBlank();
			case WEEKLY -> isValidWeekly(request.scheduleValue());
			case INTERVAL -> isValidInterval(request.scheduleValue());
		};

		if (!valid) {
			context.disableDefaultConstraintViolation();
			context.buildConstraintViolationWithTemplate(messageFor(request.scheduleType()))
				.addPropertyNode("scheduleValue")
				.addConstraintViolation();
		}
		return valid;
	}

	private boolean isValidWeekly(String value) {
		if (value == null || value.isBlank()) {
			return false;
		}
		return Arrays.stream(value.split(",", -1))
			.map(String::trim)
			.allMatch(this::isDayOfWeek);
	}

	private boolean isDayOfWeek(String value) {
		if (value.isBlank()) {
			return false;
		}
		try {
			DayOfWeek.valueOf(value.toUpperCase(Locale.ROOT));
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	private boolean isValidInterval(String value) {
		try {
			return value != null && Integer.parseInt(value) > 0;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private String messageFor(ScheduleType scheduleType) {
		return switch (scheduleType) {
			case DAILY -> "매일 복용은 일정 값을 비워야 합니다.";
			case WEEKLY -> "요일을 쉼표로 구분해 하나 이상 입력해야 합니다.";
			case INTERVAL -> "복용 간격은 1 이상의 정수여야 합니다.";
		};
	}
}
