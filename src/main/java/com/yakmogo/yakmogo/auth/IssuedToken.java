package com.yakmogo.yakmogo.auth;

import java.time.Instant;

public record IssuedToken(String token, Instant expiresAt) {
}
