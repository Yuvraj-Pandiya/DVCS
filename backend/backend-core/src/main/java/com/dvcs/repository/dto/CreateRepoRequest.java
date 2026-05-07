package com.dvcs.repository.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a new repository.
 */
@Schema(description = "Request body for creating a new Git repository")
public record CreateRepoRequest(
        @Schema(description = "Repository name; may only contain letters, digits, dots, hyphens, and underscores",
                example = "my-awesome-project")
        @NotBlank(message = "Repository name is required")
        @Size(min = 1, max = 128, message = "Repository name must be between 1 and 128 characters")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$",
                message = "Repository name may only contain letters, digits, dots, hyphens, and underscores")
        String name,

        @Schema(description = "Optional human-readable description of the repository",
                example = "A collection of useful utilities for distributed systems")
        String description,

        @Schema(description = "Whether the repository should be private (true) or publicly visible (false)",
                example = "false")
        boolean isPrivate,

        @Schema(description = "Name of the default branch; defaults to 'main' if not specified",
                example = "main")
        String defaultBranch
) {
    public String defaultBranch() {
        return defaultBranch != null ? defaultBranch : "main";
    }
}
