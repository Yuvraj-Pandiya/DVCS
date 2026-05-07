package com.dvcs.repository.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * Data transfer object representing a branch.
 */
@Schema(description = "Branch metadata returned by the branch API")
public record BranchDto(
        @Schema(description = "Unique identifier of the branch", example = "7")
        Long id,

        @Schema(description = "ID of the repository this branch belongs to", example = "1")
        Long repoId,

        @Schema(description = "Branch name", example = "feature/add-oauth")
        String name,

        @Schema(description = "SHA of the commit at the tip of this branch",
                example = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2")
        String headSha,

        @Schema(description = "Whether this branch is protected from force-pushes and deletion", example = "false")
        boolean isProtected,

        @Schema(description = "Timestamp when the branch was created", example = "2026-01-15T10:30:00Z")
        OffsetDateTime createdAt,

        @Schema(description = "Timestamp of the most recent commit on this branch", example = "2026-03-20T14:45:00Z")
        OffsetDateTime lastCommitDate
) {}
