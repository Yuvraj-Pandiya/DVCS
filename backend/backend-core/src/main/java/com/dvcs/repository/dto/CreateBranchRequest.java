package com.dvcs.repository.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a new branch.
 */
public record CreateBranchRequest(
        @NotBlank(message = "Branch name is required")
        @Size(min = 1, max = 255, message = "Branch name must be between 1 and 255 characters")
        String name,

        @NotBlank(message = "Source SHA is required")
        String sourceSha
) {}
