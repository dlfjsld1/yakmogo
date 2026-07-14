package com.yakmogo.yakmogo.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
	// 유저 이름
	@NotBlank(message = "복용자 이름은 필수입니다.")
	@Size(max = 255, message = "복용자 이름은 255자 이하여야 합니다.")
	String name,
	// 알림 수신자 이름과 텔레그램 ID
	List<@Valid GuardianDto> guardians
) {
	public record GuardianDto(
		@NotBlank(message = "알림 수신자 이름은 필수입니다.")
		@Size(max = 255, message = "알림 수신자 이름은 255자 이하여야 합니다.")
		String name,
		@NotBlank(message = "Telegram Chat ID는 필수입니다.")
		@Size(max = 255, message = "Telegram Chat ID는 255자 이하여야 합니다.")
		String chatId
	) {}
}
