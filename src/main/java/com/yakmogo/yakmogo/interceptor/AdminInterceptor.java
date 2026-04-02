package com.yakmogo.yakmogo.interceptor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AdminInterceptor implements HandlerInterceptor {

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

		if (adminPassword.equals(requestPassword)) return true;
		if (magicToken != null && !magicToken.isEmpty()) return true;

		//틀리면 401에러
		response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "관리자 암호가 필요합니다.");
		return false;
	}
}
