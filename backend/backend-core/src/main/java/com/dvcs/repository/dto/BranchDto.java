package com.dvcs.repository.dto;

import java.time.OffsetDateTime;

/**
 * Data transfer object representing a branch.
 */
public record BranchDto(
        Long id,
        Long repoId,
        String name,
        String headSha,
        boolean isProtected,
        OffsetDateTime createdAt
) {}
