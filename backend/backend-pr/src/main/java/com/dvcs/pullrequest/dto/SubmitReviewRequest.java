package com.dvcs.pullrequest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for submitting a pull request review.
 *
 * @param verdict the review verdict; must be APPROVE, CHANGES_REQUESTED, or COMMENT
 * @param body    the optional review body text
 */
public record SubmitReviewRequest(
        @NotBlank(message = "Verdict must not be blank")
        @Pattern(regexp = "APPROVE|CHANGES_REQUESTED|COMMENT",
                 message = "Verdict must be one of: APPROVE, CHANGES_REQUESTED, COMMENT")
        String verdict,

        String body
) {}
