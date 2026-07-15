package com.yakmogo.yakmogo.controller;

import java.util.Map;

public record ApiError(
	String code,
	String error,
	Map<String, String> fieldErrors
) {
	public static ApiError of(String code, String error) {
		return new ApiError(code, error, Map.of());
	}
}
