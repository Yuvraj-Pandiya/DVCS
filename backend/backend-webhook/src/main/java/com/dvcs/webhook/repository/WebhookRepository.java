package com.dvcs.webhook.repository;

import com.dvcs.webhook.domain.Webhook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Webhook} entities.
 */
@Repository
public interface WebhookRepository extends JpaRepository<Webhook, Long> {

    /**
     * Finds all active webhooks for a given repository.
     *
     * @param repoId the repository ID
     * @return list of active webhooks
     */
    List<Webhook> findByRepoIdAndActiveTrue(Long repoId);

    /**
     * Finds all webhooks for a given repository.
     *
     * @param repoId the repository ID
     * @return list of all webhooks for the repository
     */
    List<Webhook> findByRepoId(Long repoId);

    /**
     * Finds webhooks for a repository that are subscribed to a specific event.
     *
     * <p>Uses a native PostgreSQL array contains query.
     *
     * @param repoId the repository ID
     * @param event  the event type to check for (e.g., "push", "issues")
     * @return list of webhooks subscribed to the given event
     */
    @Query(value = "SELECT * FROM webhooks WHERE repo_id = :repoId AND :event = ANY(events)",
            nativeQuery = true)
    List<Webhook> findByRepoIdAndEventsContaining(@Param("repoId") Long repoId,
                                                   @Param("event") String event);
}
