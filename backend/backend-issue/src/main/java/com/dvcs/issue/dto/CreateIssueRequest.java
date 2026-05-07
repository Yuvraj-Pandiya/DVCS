package com.dvcs.issue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for creating a new issue.
 */
@Schema(description = "Request body for creating a new issue in a repository")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateIssueRequest {

    @Schema(description = "Title of the issue", example = "Login button unresponsive on mobile Safari")
    @NotBlank
    @Size(max = 512)
    private String title;

    @Schema(description = "Optional detailed description of the issue (supports Markdown)",
            example = "## Steps to reproduce\n1. Open the app on iOS Safari\n2. Tap the login button\n3. Nothing happens")
    private String body;
}
