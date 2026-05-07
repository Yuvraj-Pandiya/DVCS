package com.dvcs.pullrequest.dto;

import com.dvcs.diff.model.DiffHunk;
import com.dvcs.pullrequest.domain.PrComment;
import com.dvcs.pullrequest.domain.PrReview;
import com.dvcs.pullrequest.domain.PullRequest;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Response DTO for pull request detail, combining the PR metadata with its
 * diff, reviews, and comments.
 *
 * @param pr       the pull request entity
 * @param diff     the list of diff hunks between head and base branches
 * @param reviews  the list of reviews submitted on this PR
 * @param comments the list of comments on this PR, ordered by creation time
 */
@Schema(description = "Full pull request detail including metadata, diff hunks, reviews, and comments")
public record PrDetailResponse(
        @Schema(description = "The pull request entity with all metadata")
        PullRequest pr,

        @Schema(description = "List of diff hunks showing the changes between head and base branches")
        List<DiffHunk> diff,

        @Schema(description = "List of reviews submitted on this pull request, ordered by submission time")
        List<PrReview> reviews,

        @Schema(description = "List of comments on this pull request, ordered by creation time")
        List<PrComment> comments
) {}
