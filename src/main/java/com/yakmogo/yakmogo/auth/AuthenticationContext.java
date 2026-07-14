package com.yakmogo.yakmogo.auth;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class AuthenticationContext {
	private AuthenticatedPrincipal principal;

	public void setPrincipal(AuthenticatedPrincipal principal) {
		this.principal = principal;
	}

	public AuthenticatedPrincipal getRequiredPrincipal() {
		if (principal == null) {
			throw new UnauthorizedException("인증 정보가 없습니다.");
		}
		return principal;
	}
}
