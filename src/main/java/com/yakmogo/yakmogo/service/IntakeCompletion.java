package com.yakmogo.yakmogo.service;

import java.time.LocalDateTime;

public record IntakeCompletion(
	Long logId,
	String userName,
	String medicineName,
	LocalDateTime actualTakenTime
) {
}
