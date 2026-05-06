package com.dvcs.auth.dto;

/**
 * Response body returned after a successful login or token refresh.
 * Contains a short-lived JWT access token and a long-lived refresh token.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken
) {}
