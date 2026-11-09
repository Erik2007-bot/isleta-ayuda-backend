package com.isletaayuda.backend.auth.dto;

import java.time.Instant;

public record LoginResponse(String token, String tokenType, long expiresIn, Instant expiresAt) {
}
