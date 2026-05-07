package com.dvcs.repository.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * Data transfer object representing repository metadata.
 */
@Schema(description = "Repository metadata returned by the repository API")
public record RepoDto(
        @Schema(description = "Unique identifier of the repository", example = "1")
        Long id,

        @Schema(description = "Username of the repository owner", example = "alice")
        String ownerUsername,

        @Schema(description = "Repository name", example = "my-awesome-project")
        String name,

        @Schema(description = "Human-readable description of the repository",
                example = "A collection of useful utilities for distributed systems")
        String description,

        @Schema(description = "Whether the repository is private", example = "false")
        boolean isPrivate,

        @Schema(description = "Name of the default branch", example = "main")
        String defaultBranch,

        @Schema(description = "ID of the repository this was forked from; null if not a fork", example = "null")
        Long forkOf,

        @Schema(description = "Timestamp when the repository was created", example = "2026-01-15T10:30:00Z")
        OffsetDateTime createdAt
) {}
