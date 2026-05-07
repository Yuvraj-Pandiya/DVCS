package com.dvcs.pullrequest.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for adding a comment to a pull request.
 *
 * @param body       the comment body text; must not be blank
 * @param filePath   optional file path for inline comments
 * @param lineNumber optional line number for inline comments
 */
@Schema(description = "Request body for adding a comment to a pull request")
public record AddCommentRequest(
        @Schema(description = "Comment body text (supports Markdown)",
                example = "Could you add a unit test for the edge case where the token is expired?")
        @NotBlank(message = "Comment body must not be blank")
        String body,

        @Schema(description = "File path for an inline comment; null for a general PR comment",
                example = "src/main/java/com/dvcs/auth/service/AuthService.java")
        String filePath,

        @Schema(description = "Line number in the file for an inline comment; null for a general PR comment",
                example = "42")
        Integer lineNumber
) {}
