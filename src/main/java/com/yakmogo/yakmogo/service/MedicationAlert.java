package com.yakmogo.yakmogo.service;

public record MedicationAlert(
	String key,
	AlertLevel level,
	int hoursOverdue
) {
	public enum AlertLevel {
		ON_TIME,
		LEVEL_1,
		LEVEL_2,
		LEVEL_3
	}
}
