package com.dvcs.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * Detailed user profile information.
 */
@Schema(description = "Detailed user profile information")
public record UserProfileDto(
        @Schema(description = "Unique user ID", example = "1")
        Long id,

        @Schema(description = "Unique username", example = "alice")
        String username,

        @Schema(description = "User email address", example = "alice@example.com")
        String email,

        @Schema(description = "URL to the user's avatar image", example = "https://example.com/avatar.png")
        String avatarUrl,

        @Schema(description = "User bio", example = "Software engineer and open source enthusiast.")
        String bio,

        @Schema(description = "Account creation timestamp")
        OffsetDateTime createdAt
) {}
