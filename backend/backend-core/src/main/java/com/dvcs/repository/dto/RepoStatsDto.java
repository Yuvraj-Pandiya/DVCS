package com.dvcs.repository.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Data transfer object representing repository statistics.
 */
@Schema(description = "Aggregated statistics for a repository")
public record RepoStatsDto(
        @Schema(description = "Total size of all stored Git objects in bytes", example = "1048576")
        long totalObjectSizeBytes,

        @Schema(description = "Total number of commits in the repository", example = "342")
        long commitCount,

        @Schema(description = "Number of distinct contributors who have committed to the repository", example = "12")
        long contributorCount
) {}
