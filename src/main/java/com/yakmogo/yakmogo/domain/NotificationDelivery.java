package com.yakmogo.yakmogo.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
	name = "notification_delivery",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_notification_delivery_target",
		columnNames = {"intake_log_id", "guardian_id", "alert_key"}
	)
)
public class NotificationDelivery {
	private static final int MAX_ATTEMPTS = 3;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "intake_log_id", nullable = false)
	private IntakeLog intakeLog;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "guardian_id", nullable = false)
	private Guardian guardian;

	@Column(name = "alert_key", nullable = false, length = 64)
	private String alertKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private NotificationDeliveryStatus status;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "next_attempt_at")
	private LocalDateTime nextAttemptAt;

	@Column(name = "last_attempt_at")
	private LocalDateTime lastAttemptAt;

	@Column(name = "sent_at")
	private LocalDateTime sentAt;

	@Column(name = "last_error", length = 500)
	private String lastError;

	public static NotificationDelivery pending(IntakeLog intakeLog, Guardian guardian, String alertKey) {
		NotificationDelivery delivery = new NotificationDelivery();
		delivery.intakeLog = intakeLog;
		delivery.guardian = guardian;
		delivery.alertKey = alertKey;
		delivery.status = NotificationDeliveryStatus.PENDING;
		return delivery;
	}

	public boolean canAttempt(LocalDateTime now) {
		if (status == NotificationDeliveryStatus.SENT || status == NotificationDeliveryStatus.EXHAUSTED) {
			return false;
		}
		return nextAttemptAt == null || !nextAttemptAt.isAfter(now);
	}

	public void recordSuccess(LocalDateTime attemptedAt) {
		attemptCount++;
		lastAttemptAt = attemptedAt;
		sentAt = attemptedAt;
		nextAttemptAt = null;
		lastError = null;
		status = NotificationDeliveryStatus.SENT;
	}

	public void recordFailure(LocalDateTime attemptedAt, String failureReason) {
		attemptCount++;
		lastAttemptAt = attemptedAt;
		lastError = truncate(failureReason);
		if (attemptCount >= MAX_ATTEMPTS) {
			status = NotificationDeliveryStatus.EXHAUSTED;
			nextAttemptAt = null;
			return;
		}
		status = NotificationDeliveryStatus.RETRY_WAIT;
		nextAttemptAt = attemptedAt.plusMinutes(attemptCount == 1 ? 1 : 5);
	}

	private String truncate(String value) {
		if (value == null || value.isBlank()) {
			return "Telegram delivery failed";
		}
		return value.length() <= 500 ? value : value.substring(0, 500);
	}
}
