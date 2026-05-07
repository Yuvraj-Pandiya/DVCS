package com.dvcs.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * DTO representing a user search result.
 */
@Schema(description = "A user account matching a search query")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSearchResult {

    @Schema(description = "Unique identifier of the user", example = "5")
    private Long id;

    @Schema(description = "Username of the user", example = "alice")
    private String username;

    @Schema(description = "Email address of the user", example = "alice@example.com")
    private String email;

    @Schema(description = "URL of the user's avatar image", example = "https://avatars.example.com/alice.png")
    private String avatarUrl;

    @Schema(description = "User's biography or profile description", example = "Open source enthusiast and distributed systems engineer")
    private String bio;

    @Schema(description = "Timestamp when the user account was created", example = "2026-01-15T10:30:00Z")
    private OffsetDateTime createdAt;
}
