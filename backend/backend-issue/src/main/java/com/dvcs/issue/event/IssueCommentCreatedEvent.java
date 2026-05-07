package com.dvcs.issue.event;

/**
 * Application event published when a comment is added to an issue.
 *
 * <p>Consumed by the notification module (when implemented) to send
 * in-app notifications to the issue author and previous commenters.
 * Using {@link org.springframework.context.ApplicationEventPublisher} decouples
 * the issue module from the notification module, which is not yet a dependency.
 *
 * @param issueId         the ID of the issue that received a comment
 * @param commentId       the ID of the new comment
 * @param commenterUserId the user who posted the comment
 * @param targetUserIds   the user IDs that should be notified (author + prior commenters)
 */
public record IssueCommentCreatedEvent(
        Long issueId,
        Long commentId,
        Long commenterUserId,
        java.util.List<Long> targetUserIds) {
}
