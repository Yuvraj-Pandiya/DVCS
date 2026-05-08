package com.dvcs.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Public user information returned in authentication responses.
 */
@Schema(description = "Public user information")
public record UserResponse(
        @Schema(description = "Unique user ID", example = "1")
        Long id,

        @Schema(description = "Unique username", example = "alice")
        String username,

        @Schema(description = "User email address", example = "alice@example.com")
        String email,

        @Schema(description = "URL to the user's avatar image", example = "https://example.com/avatar.png")
        String avatarUrl
) {}
