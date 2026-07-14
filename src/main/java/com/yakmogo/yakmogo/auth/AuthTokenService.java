package com.yakmogo.yakmogo.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AuthTokenService {
	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final String ACCESS_TYPE = "ACCESS";
	private static final String LOGIN_PROOF_TYPE = "LOGIN_PROOF";

	private final ObjectMapper objectMapper;
	private final byte[] secret;
	private final long accessTtlSeconds;
	private final long loginProofTtlSeconds;
	private final Clock clock;

	@Autowired
	public AuthTokenService(
		ObjectMapper objectMapper,
		@Value("${auth.token.secret}") String secret,
		@Value("${auth.token.ttl-seconds:3600}") long accessTtlSeconds,
		@Value("${auth.token.login-proof-ttl-seconds:600}") long loginProofTtlSeconds
	) {
		this(objectMapper, secret, accessTtlSeconds, loginProofTtlSeconds, Clock.systemUTC());
	}

	AuthTokenService(
		ObjectMapper objectMapper,
		String secret,
		long accessTtlSeconds,
		long loginProofTtlSeconds,
		Clock clock
	) {
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalArgumentException("auth.token.secret은 32바이트 이상이어야 합니다.");
		}
		this.objectMapper = objectMapper;
		this.secret = secret.getBytes(StandardCharsets.UTF_8);
		this.accessTtlSeconds = accessTtlSeconds;
		this.loginProofTtlSeconds = loginProofTtlSeconds;
		this.clock = clock;
	}

	public IssuedToken issueAccessToken(Set<Long> allowedUserIds) {
		Instant expiresAt = clock.instant().plusSeconds(accessTtlSeconds);
		TokenPayload payload = new TokenPayload(
			ACCESS_TYPE,
			null,
			allowedUserIds.stream().sorted().toList(),
			expiresAt.getEpochSecond()
		);
		return new IssuedToken(encode(payload), expiresAt);
	}

	public String issueLoginProof(String chatId) {
		Instant expiresAt = clock.instant().plusSeconds(loginProofTtlSeconds);
		return encode(new TokenPayload(LOGIN_PROOF_TYPE, chatId, List.of(), expiresAt.getEpochSecond()));
	}

	public String verifyLoginProof(String token) {
		TokenPayload payload = decodeAndVerify(token, LOGIN_PROOF_TYPE);
		if (payload.subject() == null || payload.subject().isBlank()) {
			throw new UnauthorizedException("유효하지 않은 로그인 proof입니다.");
		}
		return payload.subject();
	}

	public AuthenticatedPrincipal verifyAccessToken(String token) {
		TokenPayload payload = decodeAndVerify(token, ACCESS_TYPE);
		return AuthenticatedPrincipal.telegram(Set.copyOf(payload.allowedUserIds()));
	}

	private String encode(TokenPayload payload) {
		try {
			byte[] payloadBytes = objectMapper.writeValueAsBytes(payload);
			String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadBytes);
			String signature = Base64.getUrlEncoder().withoutPadding()
				.encodeToString(sign(encodedPayload.getBytes(StandardCharsets.UTF_8)));
			return encodedPayload + "." + signature;
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("인증 토큰을 생성할 수 없습니다.", e);
		}
	}

	private TokenPayload decodeAndVerify(String token, String expectedType) {
		try {
			String[] parts = token == null ? new String[0] : token.split("\\.");
			if (parts.length != 2) {
				throw new UnauthorizedException("유효하지 않은 인증 토큰입니다.");
			}

			byte[] expectedSignature = sign(parts[0].getBytes(StandardCharsets.UTF_8));
			byte[] actualSignature = Base64.getUrlDecoder().decode(parts[1]);
			if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
				throw new UnauthorizedException("유효하지 않은 인증 토큰입니다.");
			}

			TokenPayload payload = objectMapper.readValue(
				Base64.getUrlDecoder().decode(parts[0]),
				TokenPayload.class
			);
			if (!expectedType.equals(payload.type()) || payload.expiresAt() <= clock.instant().getEpochSecond()) {
				throw new UnauthorizedException("인증 토큰이 만료됐거나 유효하지 않습니다.");
			}
			return payload;
		} catch (UnauthorizedException e) {
			throw e;
		} catch (Exception e) {
			throw new UnauthorizedException("유효하지 않은 인증 토큰입니다.");
		}
	}

	private byte[] sign(byte[] value) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
			return mac.doFinal(value);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("인증 토큰에 서명할 수 없습니다.", e);
		}
	}

	private record TokenPayload(
		String type,
		String subject,
		List<Long> allowedUserIds,
		long expiresAt
	) {
		private TokenPayload {
			allowedUserIds = allowedUserIds == null ? List.of() : List.copyOf(allowedUserIds);
		}
	}
}
