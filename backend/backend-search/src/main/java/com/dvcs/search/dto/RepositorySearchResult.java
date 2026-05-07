package com.dvcs.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * DTO representing a repository search result.
 */
@Schema(description = "A repository matching a search query")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepositorySearchResult {

    @Schema(description = "Unique identifier of the repository", example = "1")
    private Long id;

    @Schema(description = "Username of the repository owner", example = "alice")
    private String ownerUsername;

    @Schema(description = "Repository name", example = "my-awesome-project")
    private String name;

    @Schema(description = "Repository description", example = "A collection of useful utilities for distributed systems")
    private String description;

    @Schema(description = "Whether the repository is private", example = "false")
    private boolean isPrivate;

    @Schema(description = "Timestamp when the repository was created", example = "2026-01-15T10:30:00Z")
    private OffsetDateTime createdAt;
}
