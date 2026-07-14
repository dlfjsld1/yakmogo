package com.yakmogo.yakmogo.auth;

import java.util.Set;

public record AuthenticatedPrincipal(
	PrincipalType principalType,
	Set<Long> allowedUserIds,
	boolean admin
) {
	public AuthenticatedPrincipal {
		allowedUserIds = Set.copyOf(allowedUserIds);
	}

	public static AuthenticatedPrincipal forAdmin() {
		return new AuthenticatedPrincipal(PrincipalType.ADMIN, Set.of(), true);
	}

	public static AuthenticatedPrincipal telegram(Set<Long> allowedUserIds) {
		return new AuthenticatedPrincipal(PrincipalType.TELEGRAM, allowedUserIds, false);
	}
}
