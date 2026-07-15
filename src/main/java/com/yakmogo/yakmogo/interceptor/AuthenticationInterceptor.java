package com.yakmogo.yakmogo.interceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.yakmogo.yakmogo.auth.AuthTokenService;
import com.yakmogo.yakmogo.auth.AuthenticatedPrincipal;
import com.yakmogo.yakmogo.auth.AuthenticationContext;
import com.yakmogo.yakmogo.auth.UnauthorizedException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AuthenticationInterceptor implements HandlerInterceptor {
	private final AuthTokenService authTokenService;
	private final AuthenticationContext authenticationContext;

	@Value("${admin.password}")
	private String adminPassword;

	@Override
	public boolean preHandle(
		HttpServletRequest request,
		HttpServletResponse response,
		Object handler
	) throws Exception {
		if (request.getMethod().equals("OPTIONS")) {
			return true;
		}

		String requestPassword = request.getHeader("x-admin-password");
		String magicToken = request.getHeader("x-magic-token");

		if (matchesAdminPassword(requestPassword)) {
			authenticationContext.setPrincipal(AuthenticatedPrincipal.forAdmin());
			return true;
		}
		if (magicToken != null && !magicToken.isBlank()) {
			authenticationContext.setPrincipal(authTokenService.verifyAccessToken(magicToken));
			return true;
		}

		throw new UnauthorizedException("인증이 필요합니다.");
	}

	private boolean matchesAdminPassword(String requestPassword) {
		if (requestPassword == null) {
			return false;
		}
		return MessageDigest.isEqual(
			adminPassword.getBytes(StandardCharsets.UTF_8),
			requestPassword.getBytes(StandardCharsets.UTF_8)
		);
	}
}
