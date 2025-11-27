package com.yakmogo.yakmogo.dto;

import java.util.List;

public record UserCreateRequest(
	// 유저 이름
	String name,
	// 알림 수신자 이름과 텔레그램 ID
	List<GuardianDto> guardians
) {
	public record GuardianDto(String name, String chatId) {}
}