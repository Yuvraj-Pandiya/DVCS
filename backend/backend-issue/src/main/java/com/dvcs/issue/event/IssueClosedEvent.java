package com.dvcs.issue.event;

/**
 * Application event published when an issue is closed.
 *
 * <p>Consumed by the webhook module (when implemented) to deliver
 * an {@code issues} webhook event to registered subscribers.
 * Using {@link org.springframework.context.ApplicationEventPublisher} decouples
 * the issue module from the webhook module, which is not yet a dependency.
 *
 * @param repoId  the repository the issue belongs to
 * @param issueId the ID of the closed issue
 * @param payload a simple description of the event for webhook delivery
 */
public record IssueClosedEvent(Long repoId, Long issueId, String payload) {
}
