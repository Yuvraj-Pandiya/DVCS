package com.dvcs.auth.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Response DTO returned exactly once when a personal access token is created.
 *
 * <p>Contains the raw token value ({@code rawToken}) which is returned to the caller
 * <em>exactly once</em> and never stored or exposed again. All other fields are safe
 * to return in subsequent list/info responses.
 *
 * <p>The {@code tokenHash} is intentionally excluded — it must never be exposed via any API.
 */
public record PersonalTokenResponse(
        Long id,
        String name,
        List<String> scopes,
        Instant expiresAt,
        OffsetDateTime createdAt,
        /** The raw token value. Returned exactly once at creation time. Never log or store this. */
        String rawToken
) {}
