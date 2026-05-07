package com.dvcs.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

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
@Schema(description = "Response returned exactly once when a personal access token is created; contains the raw token value which is never shown again")
public record PersonalTokenResponse(
        @Schema(description = "Unique identifier of the personal access token", example = "42")
        Long id,

        @Schema(description = "Human-readable name for the token", example = "CI/CD deploy token")
        String name,

        @Schema(description = "List of permission scopes granted to this token", example = "[\"repo:read\", \"repo:write\"]")
        List<String> scopes,

        @Schema(description = "Expiry timestamp of the token; null means the token never expires", example = "2027-01-01T00:00:00Z")
        Instant expiresAt,

        @Schema(description = "Timestamp when the token was created", example = "2026-01-15T10:30:00Z")
        OffsetDateTime createdAt,

        @Schema(description = "The raw token value — returned exactly once at creation time. Store it securely; it cannot be retrieved again.",
                example = "dvcs_pat_a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6")
        /** The raw token value. Returned exactly once at creation time. Never log or store this. */
        String rawToken
) {}
