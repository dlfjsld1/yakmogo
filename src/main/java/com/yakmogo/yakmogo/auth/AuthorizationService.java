package com.yakmogo.yakmogo.auth;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthorizationService {
	private final AuthenticationContext authenticationContext;

	public void requireAdmin() {
		if (!authenticationContext.getRequiredPrincipal().admin()) {
			throw new ForbiddenException("관리자 권한이 필요합니다.");
		}
	}

	public void requireUserAccess(Long userId) {
		AuthenticatedPrincipal principal = authenticationContext.getRequiredPrincipal();
		if (!principal.admin() && !principal.allowedUserIds().contains(userId)) {
			throw new ForbiddenException("해당 사용자에 대한 권한이 없습니다.");
		}
	}
}
