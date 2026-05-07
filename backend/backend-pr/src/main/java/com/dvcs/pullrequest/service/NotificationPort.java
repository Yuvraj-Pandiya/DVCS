package com.dvcs.pullrequest.service;

/**
 * Port interface for sending notifications from the PR module.
 *
 * <p>This interface decouples the PR module from the notification module.
 * The notification module provides an implementation of this interface,
 * which is injected via Spring's dependency injection. If no implementation
 * is available, notification calls are silently skipped.
 *
 * <p>Implementations should be annotated with {@code @Component} or
 * {@code @Service} in the notification module.
 */
public interface NotificationPort {

    /**
     * Creates a notification for a user about a subject.
     *
     * @param userId      the ID of the user to notify
     * @param subjectType the type of subject (e.g., "pull_request", "pr_review")
     * @param subjectId   the ID of the subject entity
     * @param reason      the reason for the notification (e.g., "review_approve")
     */
    void createNotification(Long userId, String subjectType, Long subjectId, String reason);
}
