package com.yakmogo.yakmogo.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class AuthTokenServiceTest {
	private static final String SECRET = "test-auth-secret-with-at-least-32-bytes";
	private static final Instant NOW = Instant.parse("2026-07-14T00:00:00Z");

	@Test
	void accessTokenContainsOnlyIssuedUserScope() {
		AuthTokenService service = serviceAt(NOW, 3600);

		IssuedToken issued = service.issueAccessToken(Set.of(2L, 1L));
		AuthenticatedPrincipal principal = service.verifyAccessToken(issued.token());

		assertEquals(PrincipalType.TELEGRAM, principal.principalType());
		assertEquals(Set.of(1L, 2L), principal.allowedUserIds());
		assertFalse(principal.admin());
	}

	@Test
	void rejectsTamperedToken() {
		AuthTokenService service = serviceAt(NOW, 3600);
		String token = service.issueAccessToken(Set.of(1L)).token();
		char replacement = token.endsWith("A") ? 'B' : 'A';
		String tampered = token.substring(0, token.length() - 1) + replacement;

		assertThrows(UnauthorizedException.class, () -> service.verifyAccessToken(tampered));
	}

	@Test
	void rejectsExpiredToken() {
		String token = serviceAt(NOW, 10).issueAccessToken(Set.of(1L)).token();
		AuthTokenService verifier = serviceAt(NOW.plusSeconds(11), 10);

		assertThrows(UnauthorizedException.class, () -> verifier.verifyAccessToken(token));
	}

	@Test
	void keepsLoginProofSeparateFromAccessToken() {
		AuthTokenService service = serviceAt(NOW, 3600);
		String proof = service.issueLoginProof("telegram-chat");

		assertEquals("telegram-chat", service.verifyLoginProof(proof));
		assertThrows(UnauthorizedException.class, () -> service.verifyAccessToken(proof));
		assertTrue(service.issueAccessToken(Set.of(1L)).expiresAt().isAfter(NOW));
	}

	private AuthTokenService serviceAt(Instant instant, long accessTtlSeconds) {
		return new AuthTokenService(
			new ObjectMapper().findAndRegisterModules(),
			SECRET,
			accessTtlSeconds,
			600,
			Clock.fixed(instant, ZoneOffset.UTC)
		);
	}
}
