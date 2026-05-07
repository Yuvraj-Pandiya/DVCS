package com.dvcs.issue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * DTO returned from the issue service representing a single issue.
 */
@Schema(description = "Issue data returned by the issue API")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueResponse {

    @Schema(description = "Unique identifier of the issue", example = "23")
    private Long id;

    @Schema(description = "Sequential issue number within the repository", example = "7")
    private Integer number;

    @Schema(description = "Issue title", example = "Login button unresponsive on mobile Safari")
    private String title;

    @Schema(description = "Issue description body (may contain Markdown)",
            example = "## Steps to reproduce\n1. Open the app on iOS Safari\n2. Tap the login button")
    private String body;

    @Schema(description = "ID of the user who created the issue", example = "5")
    private Long authorId;

    @Schema(description = "Current status of the issue", example = "open",
            allowableValues = {"open", "closed"})
    private String status;

    @Schema(description = "Timestamp when the issue was created", example = "2026-03-20T14:45:00Z")
    private OffsetDateTime createdAt;
}
