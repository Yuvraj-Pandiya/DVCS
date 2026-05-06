package com.dvcs.git.event;

import java.util.List;

/**
 * Event record published when a push is successfully accepted by
 * {@code ReceivePackServiceImpl}.
 *
 * <p>This record is published via Spring's {@link org.springframework.context.ApplicationEventPublisher}
 * so that downstream modules (webhook delivery, pipeline engine) can react to push
 * events asynchronously without tight coupling to the transport layer.
 *
 * <p>Requirement 6: HTTP Smart Git Transport — push event publishing.
 * Requirement 12: Webhook Management and Delivery — push event trigger.
 * Requirement 13: CI/CD Pipeline Simulation — push event trigger.
 *
 * @param repoId      the internal repository ID
 * @param userId      the ID of the user who performed the push
 * @param branchName  the short branch name (e.g. {@code "main"})
 * @param newHeadSha  the new head commit SHA after the push
 * @param commitShas  ordered list of new commit SHAs introduced by the push
 */
public record PushEvent(Long repoId, Long userId, String branchName,
                        String newHeadSha, List<String> commitShas) {}
