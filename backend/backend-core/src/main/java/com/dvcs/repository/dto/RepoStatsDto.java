package com.dvcs.repository.dto;

/**
 * Data transfer object representing repository statistics.
 */
public record RepoStatsDto(
        long totalObjectSizeBytes,
        long commitCount,
        long contributorCount
) {}
