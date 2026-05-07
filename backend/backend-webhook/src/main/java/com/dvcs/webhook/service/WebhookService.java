package com.dvcs.webhook.service;

import com.dvcs.common.exception.AccessDeniedException;
import com.dvcs.common.exception.EntityNotFoundException;
import com.dvcs.repository.repository.CollaboratorRepository;
import com.dvcs.webhook.domain.Webhook;
import com.dvcs.webhook.dto.CreateWebhookRequest;
import com.dvcs.webhook.dto.UpdateWebhookRequest;
import com.dvcs.webhook.dto.WebhookResponse;
import com.dvcs.webhook.repository.WebhookRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing webhook lifecycle operations.
 *
 * <p>All mutating operations require the caller to have OWNER role on the repository.
 */
@Service
@Transactional
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private static final List<String> OWNER_ROLES = List.of("OWNER");

    private final WebhookRepository webhookRepository;
    private final CollaboratorRepository collaboratorRepository;

    public WebhookService(WebhookRepository webhookRepository,
                          CollaboratorRepository collaboratorRepository) {
        this.webhookRepository = webhookRepository;
        this.collaboratorRepository = collaboratorRepository;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Creates a new webhook for the given repository.
     *
     * <p>Only users with OWNER role may create webhooks.
     *
     * @param repoId the repository ID
     * @param userId the ID of the requesting user
     * @param req    the creation request
     * @return the saved webhook as a {@link WebhookResponse}
     * @throws AccessDeniedException if the user is not an OWNER of the repository
     */
    public WebhookResponse createWebhook(Long repoId, Long userId, CreateWebhookRequest req) {
        checkOwnerAccess(repoId, userId);

        Webhook webhook = Webhook.builder()
                .repoId(repoId)
                .url(req.getUrl())
                .secret(req.getSecret())
                .events(req.getEvents().toArray(new String[0]))
                .active(true)
                .build();

        Webhook saved = webhookRepository.save(webhook);
        log.debug("Created webhook id={} for repo {}", saved.getId(), repoId);
        return toResponse(saved);
    }

    /**
     * Lists all webhooks for the given repository.
     *
     * <p>Only users with OWNER role may list webhooks.
     *
     * @param repoId the repository ID
     * @param userId the ID of the requesting user
     * @return list of webhooks as {@link WebhookResponse}
     * @throws AccessDeniedException if the user is not an OWNER of the repository
     */
    @Transactional(readOnly = true)
    public List<WebhookResponse> listWebhooks(Long repoId, Long userId) {
        checkOwnerAccess(repoId, userId);

        return webhookRepository.findByRepoId(repoId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Updates an existing webhook.
     *
     * <p>Only non-null fields in the request are applied.
     * Only users with OWNER role on the webhook's repository may update it.
     *
     * @param webhookId the webhook ID
     * @param userId    the ID of the requesting user
     * @param req       the update request
     * @return the updated webhook as a {@link WebhookResponse}
     * @throws EntityNotFoundException if the webhook does not exist
     * @throws AccessDeniedException   if the user is not an OWNER of the repository
     */
    public WebhookResponse updateWebhook(Long webhookId, Long userId, UpdateWebhookRequest req) {
        Webhook webhook = loadWebhookById(webhookId);
        checkOwnerAccess(webhook.getRepoId(), userId);

        if (req.getUrl() != null) {
            webhook.setUrl(req.getUrl());
        }
        if (req.getEvents() != null) {
            webhook.setEvents(req.getEvents().toArray(new String[0]));
        }
        if (req.getActive() != null) {
            webhook.setActive(req.getActive());
        }

        Webhook saved = webhookRepository.save(webhook);
        log.debug("Updated webhook id={} by user {}", webhookId, userId);
        return toResponse(saved);
    }

    /**
     * Deletes a webhook.
     *
     * <p>Only users with OWNER role on the webhook's repository may delete it.
     *
     * @param webhookId the webhook ID
     * @param userId    the ID of the requesting user
     * @throws EntityNotFoundException if the webhook does not exist
     * @throws AccessDeniedException   if the user is not an OWNER of the repository
     */
    public void deleteWebhook(Long webhookId, Long userId) {
        Webhook webhook = loadWebhookById(webhookId);
        checkOwnerAccess(webhook.getRepoId(), userId);

        webhookRepository.delete(webhook);
        log.debug("Deleted webhook id={} by user {}", webhookId, userId);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Loads a webhook by its primary key, throwing {@link EntityNotFoundException} if absent.
     */
    private Webhook loadWebhookById(Long webhookId) {
        return webhookRepository.findById(webhookId)
                .orElseThrow(() -> new EntityNotFoundException("Webhook not found: " + webhookId));
    }

    /**
     * Checks that the given user has OWNER role on the repository.
     *
     * @throws AccessDeniedException if the user does not have OWNER role
     */
    private void checkOwnerAccess(Long repoId, Long userId) {
        boolean isOwner = collaboratorRepository
                .existsByRepoIdAndUserIdAndRoleIn(repoId, userId, OWNER_ROLES);
        if (!isOwner) {
            throw new AccessDeniedException(
                    "User " + userId + " does not have OWNER role on repository " + repoId);
        }
    }

    /**
     * Maps a {@link Webhook} entity to a {@link WebhookResponse} DTO.
     *
     * <p>The secret is intentionally excluded from the response.
     */
    private WebhookResponse toResponse(Webhook webhook) {
        return WebhookResponse.builder()
                .id(webhook.getId())
                .repoId(webhook.getRepoId())
                .url(webhook.getUrl())
                .events(webhook.getEvents() != null
                        ? Arrays.asList(webhook.getEvents())
                        : List.of())
                .active(webhook.isActive())
                .createdAt(webhook.getCreatedAt())
                .build();
    }
}
