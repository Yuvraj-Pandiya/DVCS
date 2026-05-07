package com.dvcs.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Read-only DTO representing metadata about a personal access token.
 *
 * <p>Used for list operations. Does not contain the raw token value or the token hash —
 * neither is ever exposed after the initial creation response.
 */
@Schema(description = "Metadata about a personal access token (does not include the raw token value)")
public record PersonalTokenInfo(
        @Schema(description = "Unique identifier of the personal access token", example = "42")
        Long id,

        @Schema(description = "Human-readable name for the token", example = "CI/CD deploy token")
        String name,

        @Schema(description = "List of permission scopes granted to this token", example = "[\"repo:read\", \"repo:write\"]")
        List<String> scopes,

        @Schema(description = "Expiry timestamp of the token; null means the token never expires", example = "2027-01-01T00:00:00Z")
        Instant expiresAt,

        @Schema(description = "Timestamp when the token was created", example = "2026-01-15T10:30:00Z")
        OffsetDateTime createdAt
) {}
