package com.dvcs.repository.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a new branch.
 */
@Schema(description = "Request body for creating a new branch in a repository")
public record CreateBranchRequest(
        @Schema(description = "Name for the new branch", example = "feature/add-oauth")
        @NotBlank(message = "Branch name is required")
        @Size(min = 1, max = 255, message = "Branch name must be between 1 and 255 characters")
        String name,

        @Schema(description = "The commit SHA from which the new branch will be created",
                example = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2")
        @NotBlank(message = "Source SHA is required")
        String sourceSha
) {}
