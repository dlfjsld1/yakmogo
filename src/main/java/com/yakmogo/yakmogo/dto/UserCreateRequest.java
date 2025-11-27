package com.yakmogo.yakmogo.dto;

public record UserCreateRequest(
	String name,
	// 보호자 텔레그램 ID (콤마로 구분, 예: "1234,5678")
	String guardianChatIds
) {}