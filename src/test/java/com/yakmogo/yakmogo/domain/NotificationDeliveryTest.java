package com.yakmogo.yakmogo.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class NotificationDeliveryTest {
	private final LocalDateTime firstAttempt = LocalDateTime.of(2026, 7, 14, 9, 0);

	@Test
	void retriesWithBoundedBackoffAndStopsAfterThirdFailure() {
		NotificationDelivery delivery = NotificationDelivery.pending(null, null, "ON_TIME");

		delivery.recordFailure(firstAttempt, "temporary failure");
		assertEquals(NotificationDeliveryStatus.RETRY_WAIT, delivery.getStatus());
		assertEquals(firstAttempt.plusMinutes(1), delivery.getNextAttemptAt());
		assertFalse(delivery.canAttempt(firstAttempt));
		assertTrue(delivery.canAttempt(firstAttempt.plusMinutes(1)));

		delivery.recordFailure(firstAttempt.plusMinutes(1), "temporary failure");
		assertEquals(firstAttempt.plusMinutes(6), delivery.getNextAttemptAt());

		delivery.recordFailure(firstAttempt.plusMinutes(6), "permanent failure");
		assertEquals(NotificationDeliveryStatus.EXHAUSTED, delivery.getStatus());
		assertEquals(3, delivery.getAttemptCount());
		assertNull(delivery.getNextAttemptAt());
		assertFalse(delivery.canAttempt(firstAttempt.plusDays(1)));
	}

	@Test
	void successfulDeliveryCannotBeSentAgain() {
		NotificationDelivery delivery = NotificationDelivery.pending(null, null, "OVERDUE_30_MINUTES");

		delivery.recordSuccess(firstAttempt);

		assertEquals(NotificationDeliveryStatus.SENT, delivery.getStatus());
		assertEquals(1, delivery.getAttemptCount());
		assertFalse(delivery.canAttempt(firstAttempt.plusMinutes(1)));
	}
}
