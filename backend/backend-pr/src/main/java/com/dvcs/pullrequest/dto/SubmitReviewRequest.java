package com.dvcs.pullrequest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for submitting a pull request review.
 *
 * @param verdict the review verdict; must be APPROVE, CHANGES_REQUESTED, or COMMENT
 * @param body    the optional review body text
 */
@Schema(description = "Request body for submitting a review on a pull request")
public record SubmitReviewRequest(
        @Schema(description = "Review verdict; must be one of: APPROVE, CHANGES_REQUESTED, COMMENT",
                example = "APPROVE",
                allowableValues = {"APPROVE", "CHANGES_REQUESTED", "COMMENT"})
        @NotBlank(message = "Verdict must not be blank")
        @Pattern(regexp = "APPROVE|CHANGES_REQUESTED|COMMENT",
                 message = "Verdict must be one of: APPROVE, CHANGES_REQUESTED, COMMENT")
        String verdict,

        @Schema(description = "Optional review comment body (supports Markdown)",
                example = "LGTM! The implementation looks clean and well-tested.")
        String body
) {}
