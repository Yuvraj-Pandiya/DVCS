package com.dvcs.pullrequest.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for adding a comment to a pull request.
 *
 * @param body       the comment body text; must not be blank
 * @param filePath   optional file path for inline comments
 * @param lineNumber optional line number for inline comments
 */
public record AddCommentRequest(
        @NotBlank(message = "Comment body must not be blank")
        String body,

        String filePath,

        Integer lineNumber
) {}
