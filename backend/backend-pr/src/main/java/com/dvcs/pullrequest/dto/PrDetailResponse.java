package com.dvcs.pullrequest.dto;

import com.dvcs.diff.model.DiffHunk;
import com.dvcs.pullrequest.domain.PrComment;
import com.dvcs.pullrequest.domain.PrReview;
import com.dvcs.pullrequest.domain.PullRequest;

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
public record PrDetailResponse(
        PullRequest pr,
        List<DiffHunk> diff,
        List<PrReview> reviews,
        List<PrComment> comments
) {}
