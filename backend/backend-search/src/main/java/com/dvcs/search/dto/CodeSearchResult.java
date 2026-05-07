package com.dvcs.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO representing a code search result.
 */
@Schema(description = "A code file matching a search query, with a content snippet showing the match")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeSearchResult {

    @Schema(description = "Username of the repository owner", example = "alice")
    private String repoOwner;

    @Schema(description = "Repository name containing the matching file", example = "my-awesome-project")
    private String repoName;

    @Schema(description = "Path to the matching file within the repository", example = "src/main/java/com/dvcs/auth/service/AuthService.java")
    private String filePath;

    @Schema(description = "A short excerpt from the file showing the matching content",
            example = "...public AuthResponse login(LoginRequest request) {\n    // validate credentials...")
    private String snippet;
}
