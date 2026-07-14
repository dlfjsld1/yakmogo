package com.yakmogo.yakmogo.service;

public record TelegramDeliveryResult(boolean delivered, String failureReason) {
	public static TelegramDeliveryResult success() {
		return new TelegramDeliveryResult(true, null);
	}

	public static TelegramDeliveryResult failure(String reason) {
		return new TelegramDeliveryResult(false, reason);
	}
}
