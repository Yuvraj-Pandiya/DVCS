package com.dvcs.repository.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * Data transfer object representing commit metadata.
 */
@Schema(description = "Commit metadata returned by the commit API")
public record CommitMetaDto(
        @Schema(description = "Unique identifier of the commit record", example = "101")
        Long id,

        @Schema(description = "SHA-256 hash of the commit object (64 hex characters)",
                example = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2")
        String sha,

        @Schema(description = "ID of the user who authored the commit", example = "5")
        Long authorId,

        @Schema(description = "Username of the commit author", example = "alice")
        String authorUsername,

        @Schema(description = "Commit message", example = "feat: add OAuth2 login support")
        String message,

        @Schema(description = "Timestamp when the commit was authored", example = "2026-03-20T14:45:00Z")
        OffsetDateTime authoredAt,

        @Schema(description = "Timestamp when the commit was committed (may differ from authoredAt for rebased commits)",
                example = "2026-03-20T14:45:00Z")
        OffsetDateTime committedAt
) {}
