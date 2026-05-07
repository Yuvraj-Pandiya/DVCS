package com.dvcs.repository.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request body for toggling branch protection.
 */
@Schema(description = "Request body for enabling or disabling branch protection")
public record ToggleProtectionRequest(
        @Schema(description = "Set to true to protect the branch (prevents force-pushes and deletion), false to unprotect",
                example = "true")
        boolean protect
) {}
