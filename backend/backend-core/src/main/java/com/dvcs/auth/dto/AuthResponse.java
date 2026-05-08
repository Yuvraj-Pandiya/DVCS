package com.dvcs.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response body returned after a successful login or token refresh.
 * Contains a short-lived JWT access token and a long-lived refresh token.
 */
@Schema(description = "Authentication response containing JWT tokens returned after a successful login or token refresh")
public record AuthResponse(
        @Schema(description = "Short-lived JWT access token (15-minute expiry) to be sent as Bearer token in subsequent requests",
                example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwidXNlcm5hbWUiOiJhbGljZSJ9.abc123")
        String accessToken,

        @Schema(description = "Long-lived refresh token (30-day expiry) used to obtain a new access token; also set as an HttpOnly cookie",
                example = "dGhpcyBpcyBhIHJlZnJlc2ggdG9rZW4gZXhhbXBsZQ==")
        String refreshToken,

        @Schema(description = "User information")
        UserResponse user
) {}
