package com.dvcs.repository.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a new repository.
 */
public record CreateRepoRequest(
        @NotBlank(message = "Repository name is required")
        @Size(min = 1, max = 128, message = "Repository name must be between 1 and 128 characters")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$",
                message = "Repository name may only contain letters, digits, dots, hyphens, and underscores")
        String name,

        String description,

        boolean isPrivate,

        String defaultBranch
) {
    public String defaultBranch() {
        return defaultBranch != null ? defaultBranch : "main";
    }
}
