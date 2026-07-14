package com.yakmogo.yakmogo.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.yakmogo.yakmogo.domain.IntakeLog;
import com.yakmogo.yakmogo.service.MedicationAlert.AlertLevel;

@Component
public class MedicationAlertPolicy {
	private static final Pattern HOURLY_KEY = Pattern.compile("OVERDUE_([2-6])_HOURS");

	public Optional<MedicationAlert> currentAlert(IntakeLog log, LocalDateTime now) {
		if (!log.getIntakeDate().equals(now.toLocalDate())) {
			return Optional.empty();
		}

		long minutesOverdue = ChronoUnit.MINUTES.between(log.getIntakeTime(), now.toLocalTime());
		if (minutesOverdue < 0 || minutesOverdue > 360) {
			return Optional.empty();
		}
		if (minutesOverdue < 30) {
			return Optional.of(new MedicationAlert("ON_TIME", AlertLevel.ON_TIME, 0));
		}
		if (minutesOverdue < 60) {
			return Optional.of(new MedicationAlert("OVERDUE_30_MINUTES", AlertLevel.LEVEL_1, 0));
		}
		if (minutesOverdue < 120) {
			return Optional.of(new MedicationAlert("OVERDUE_1_HOUR", AlertLevel.LEVEL_2, 1));
		}

		int hoursOverdue = (int)(minutesOverdue / 60);
		return Optional.of(new MedicationAlert(
			"OVERDUE_" + hoursOverdue + "_HOURS",
			AlertLevel.LEVEL_3,
			hoursOverdue
		));
	}

	public Optional<MedicationAlert> alertForKey(String key) {
		if ("ON_TIME".equals(key)) {
			return Optional.of(new MedicationAlert(key, AlertLevel.ON_TIME, 0));
		}
		if ("OVERDUE_30_MINUTES".equals(key)) {
			return Optional.of(new MedicationAlert(key, AlertLevel.LEVEL_1, 0));
		}
		if ("OVERDUE_1_HOUR".equals(key)) {
			return Optional.of(new MedicationAlert(key, AlertLevel.LEVEL_2, 1));
		}
		Matcher matcher = HOURLY_KEY.matcher(key);
		if (matcher.matches()) {
			int hours = Integer.parseInt(matcher.group(1));
			return Optional.of(new MedicationAlert(key, AlertLevel.LEVEL_3, hours));
		}
		return Optional.empty();
	}
}
