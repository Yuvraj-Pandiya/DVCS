package com.dvcs.issue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for updating an existing issue.
 * Only non-null fields are applied.
 */
@Schema(description = "Request body for updating an existing issue; only non-null fields are applied")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateIssueRequest {

    @Schema(description = "New title for the issue; leave null to keep the existing title",
            example = "Login button unresponsive on mobile Safari (iOS 17)")
    @Size(max = 512)
    private String title;

    @Schema(description = "New description body for the issue; leave null to keep the existing body",
            example = "## Steps to reproduce\n1. Open the app on iOS 17 Safari\n2. Tap the login button\n3. Nothing happens")
    private String body;
}
