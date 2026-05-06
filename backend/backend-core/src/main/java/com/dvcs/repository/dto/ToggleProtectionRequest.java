package com.dvcs.repository.dto;

/**
 * Request body for toggling branch protection.
 */
public record ToggleProtectionRequest(
        boolean protect
) {}
