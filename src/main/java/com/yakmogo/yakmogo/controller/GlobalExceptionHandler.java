package com.yakmogo.yakmogo.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import com.yakmogo.yakmogo.auth.ForbiddenException;
import com.yakmogo.yakmogo.auth.UnauthorizedException;
import com.yakmogo.yakmogo.service.ResourceNotFoundException;

import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException e) {
		return ResponseEntity.badRequest().body(ApiError.of("BAD_REQUEST", e.getMessage()));
	}

	@ExceptionHandler(UnauthorizedException.class)
	public ResponseEntity<ApiError> handleUnauthorized(UnauthorizedException e) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
			.body(ApiError.of("UNAUTHORIZED", e.getMessage()));
	}

	@ExceptionHandler(ForbiddenException.class)
	public ResponseEntity<ApiError> handleForbidden(ForbiddenException e) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
			.body(ApiError.of("FORBIDDEN", e.getMessage()));
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(ApiError.of("NOT_FOUND", e.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		e.getBindingResult().getFieldErrors().forEach(error ->
			fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
		e.getBindingResult().getGlobalErrors().forEach(error ->
			fieldErrors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
		return ResponseEntity.badRequest().body(new ApiError(
			"VALIDATION_FAILED",
			"입력값이 올바르지 않습니다.",
			fieldErrors
		));
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException e) {
		return ResponseEntity.badRequest().body(ApiError.of(
			"VALIDATION_FAILED",
			"경로 또는 요청 값이 올바르지 않습니다."
		));
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException e) {
		return ResponseEntity.badRequest().body(ApiError.of(
			"VALIDATION_FAILED",
			"경로 또는 요청 값이 올바르지 않습니다."
		));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiError> handleUnreadableMessage(HttpMessageNotReadableException e) {
		return ResponseEntity.badRequest().body(ApiError.of(
			"INVALID_REQUEST_BODY",
			"요청 본문 형식이 올바르지 않습니다."
		));
	}
}
