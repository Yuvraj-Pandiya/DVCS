package com.dvcs.pullrequest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for opening a new pull request.
 *
 * @param title      the PR title; must not be blank
 * @param body       the PR description body (optional)
 * @param headBranch the source branch containing the changes; must not be blank
 * @param baseBranch the target branch to merge into; must not be blank
 */
@Schema(description = "Request body for opening a new pull request")
public record CreatePrRequest(
        @Schema(description = "Title of the pull request", example = "feat: add OAuth2 login support")
        @NotBlank(message = "Title must not be blank")
        @Size(max = 512, message = "Title must not exceed 512 characters")
        String title,

        @Schema(description = "Optional description body for the pull request (supports Markdown)",
                example = "This PR adds OAuth2 login via GitHub and Google.\n\n## Changes\n- Added OAuth2 config\n- Added callback endpoints")
        String body,

        @Schema(description = "Source branch containing the changes to be merged", example = "feature/add-oauth")
        @NotBlank(message = "Head branch must not be blank")
        String headBranch,

        @Schema(description = "Target branch to merge the changes into", example = "main")
        @NotBlank(message = "Base branch must not be blank")
        String baseBranch
) {}
