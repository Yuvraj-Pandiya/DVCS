package com.dvcs.repository.dto;

import java.time.OffsetDateTime;

/**
 * Data transfer object representing repository metadata.
 */
public record RepoDto(
        Long id,
        String ownerUsername,
        String name,
        String description,
        boolean isPrivate,
        String defaultBranch,
        Long forkOf,
        OffsetDateTime createdAt
) {}
