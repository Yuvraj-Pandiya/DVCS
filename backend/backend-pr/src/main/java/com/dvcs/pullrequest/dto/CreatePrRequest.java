package com.dvcs.pullrequest.dto;

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
public record CreatePrRequest(
        @NotBlank(message = "Title must not be blank")
        @Size(max = 512, message = "Title must not exceed 512 characters")
        String title,

        String body,

        @NotBlank(message = "Head branch must not be blank")
        String headBranch,

        @NotBlank(message = "Base branch must not be blank")
        String baseBranch
) {}
