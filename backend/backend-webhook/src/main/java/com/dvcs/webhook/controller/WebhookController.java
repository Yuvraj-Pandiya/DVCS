package com.dvcs.webhook.controller;

import com.dvcs.auth.domain.User;
import com.dvcs.auth.repository.UserRepository;
import com.dvcs.common.exception.AccessDeniedException;
import com.dvcs.common.exception.EntityNotFoundException;
import com.dvcs.common.security.RepoAccessGuard;
import com.dvcs.repository.domain.Repository;
import com.dvcs.repository.repository.CollaboratorRepository;
import com.dvcs.repository.repository.RepoRepository;
import com.dvcs.webhook.domain.Webhook;
import com.dvcs.webhook.dto.CreateWebhookRequest;
import com.dvcs.webhook.dto.DeliveryResult;
import com.dvcs.webhook.dto.UpdateWebhookRequest;
import com.dvcs.webhook.dto.WebhookResponse;
import com.dvcs.webhook.repository.WebhookRepository;
import com.dvcs.webhook.service.WebhookDeliveryService;
import com.dvcs.webhook.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller for webhook management operations.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/repos/{owner}/{repo}/webhooks — create webhook</li>
 *   <li>GET /api/repos/{owner}/{repo}/webhooks — list webhooks</li>
 *   <li>PATCH /api/repos/{owner}/{repo}/webhooks/{id} — update webhook</li>
 *   <li>DELETE /api/repos/{owner}/{repo}/webhooks/{id} — delete webhook</li>
 *   <li>POST /api/repos/{owner}/{repo}/webhooks/{id}/test — test webhook (ping)</li>
 * </ul>
 *
 * <p>Requirement 12: Webhook Management and Delivery.
 */
@Tag(name = "Webhooks", description = "Webhook management and delivery operations")
@RestController
@RequestMapping("/api/repos/{owner}/{repo}/webhooks")
public class WebhookController {

    private static final List<String> OWNER_ROLES = List.of("OWNER");

    private final WebhookService webhookService;
    private final WebhookDeliveryService webhookDeliveryService;
    private final WebhookRepository webhookRepository;
    private final RepoRepository repoRepository;
    private final UserRepository userRepository;
    private final CollaboratorRepository collaboratorRepository;

    public WebhookController(WebhookService webhookService,
                              WebhookDeliveryService webhookDeliveryService,
                              WebhookRepository webhookRepository,
                              RepoRepository repoRepository,
                              UserRepository userRepository,
                              CollaboratorRepository collaboratorRepository) {
        this.webhookService = webhookService;
        this.webhookDeliveryService = webhookDeliveryService;
        this.webhookRepository = webhookRepository;
        this.repoRepository = repoRepository;
        this.userRepository = userRepository;
        this.collaboratorRepository = collaboratorRepository;
    }

    // =========================================================================
    // POST /api/repos/{owner}/{repo}/webhooks
    // =========================================================================

    /**
     * Creates a new webhook for the repository.
     *
     * @param owner          the repository owner's username
     * @param repo           the repository name
     * @param request        the webhook creation request
     * @param authentication the current authentication
     * @return HTTP 201 with the created webhook (secret excluded)
     */
    @Operation(summary = "Create a new webhook for the repository")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Webhook created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request payload"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Owner access required"),
        @ApiResponse(responseCode = "404", description = "Repository not found")
    })
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<WebhookResponse> createWebhook(
            @PathVariable String owner,
            @PathVariable String repo,
            @Valid @RequestBody CreateWebhookRequest request,
            Authentication authentication) {

        Long repoId = resolveRepoId(owner, repo);
        User user = extractUser(authentication);
        WebhookResponse response = webhookService.createWebhook(repoId, user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // =========================================================================
    // GET /api/repos/{owner}/{repo}/webhooks
    // =========================================================================

    /**
     * Lists all webhooks for the repository.
     *
     * @param owner          the repository owner's username
     * @param repo           the repository name
     * @param authentication the current authentication
     * @return HTTP 200 with the list of webhooks (secrets excluded)
     */
    @Operation(summary = "List all webhooks for the repository")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Webhook list returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Owner access required"),
        @ApiResponse(responseCode = "404", description = "Repository not found")
    })
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<WebhookResponse>> listWebhooks(
            @PathVariable String owner,
            @PathVariable String repo,
            Authentication authentication) {

        Long repoId = resolveRepoId(owner, repo);
        User user = extractUser(authentication);
        List<WebhookResponse> list = webhookService.listWebhooks(repoId, user.getId());
        return ResponseEntity.ok(list);
    }

    // =========================================================================
    // PATCH /api/repos/{owner}/{repo}/webhooks/{id}
    // =========================================================================

    /**
     * Updates an existing webhook.
     *
     * @param owner          the repository owner's username
     * @param repo           the repository name
     * @param id             the webhook ID
     * @param request        the update request
     * @param authentication the current authentication
     * @return HTTP 200 with the updated webhook (secret excluded)
     */
    @Operation(summary = "Update an existing webhook")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Webhook updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request payload"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Owner access required"),
        @ApiResponse(responseCode = "404", description = "Repository or webhook not found")
    })
    @PatchMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<WebhookResponse> updateWebhook(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable Long id,
            @RequestBody UpdateWebhookRequest request,
            Authentication authentication) {

        User user = extractUser(authentication);
        WebhookResponse response = webhookService.updateWebhook(id, user.getId(), request);
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // DELETE /api/repos/{owner}/{repo}/webhooks/{id}
    // =========================================================================

    /**
     * Deletes a webhook.
     *
     * @param owner          the repository owner's username
     * @param repo           the repository name
     * @param id             the webhook ID
     * @param authentication the current authentication
     * @return HTTP 204 No Content
     */
    @Operation(summary = "Delete a webhook")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Webhook deleted successfully"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Owner access required"),
        @ApiResponse(responseCode = "404", description = "Repository or webhook not found")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteWebhook(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable Long id,
            Authentication authentication) {

        User user = extractUser(authentication);
        webhookService.deleteWebhook(id, user.getId());
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // POST /api/repos/{owner}/{repo}/webhooks/{id}/test
    // =========================================================================

    /**
     * Sends a synthetic ping payload to the webhook URL and returns the delivery result.
     *
     * @param owner          the repository owner's username
     * @param repo           the repository name
     * @param id             the webhook ID
     * @param authentication the current authentication
     * @return HTTP 200 with the delivery result
     */
    @Operation(summary = "Send a synthetic ping to the webhook URL and return the delivery result")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ping delivered, result returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Owner access required"),
        @ApiResponse(responseCode = "404", description = "Repository or webhook not found")
    })
    @PostMapping("/{id}/test")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DeliveryResult> testWebhook(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable Long id,
            Authentication authentication) {

        User user = extractUser(authentication);

        Webhook webhook = webhookRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Webhook not found: " + id));

        // Verify the caller has OWNER role on the webhook's repository
        boolean isOwner = collaboratorRepository
                .existsByRepoIdAndUserIdAndRoleIn(webhook.getRepoId(), user.getId(), OWNER_ROLES);
        if (!isOwner) {
            throw new AccessDeniedException(
                    "User " + user.getId() + " does not have OWNER role on repository "
                            + webhook.getRepoId());
        }

        // Build synthetic ping payload
        Map<String, Object> pingPayload = Map.of(
                "zen", "Keep it logically awesome.",
                "hook_id", id,
                "hook", Map.of(
                        "type", "Repository",
                        "id", id,
                        "active", webhook.isActive()
                )
        );

        DeliveryResult result = webhookDeliveryService.deliverSync(webhook, "ping", pingPayload);
        return ResponseEntity.ok(result);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Resolves the repository ID from owner username and repository name.
     *
     * @param owner    the owner's username
     * @param repoName the repository name
     * @return the repository ID
     * @throws EntityNotFoundException if the owner or repository does not exist
     */
    private Long resolveRepoId(String owner, String repoName) {
        User ownerUser = userRepository.findByUsername(owner)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Repository '" + owner + "/" + repoName + "' not found."));

        Repository repository = repoRepository.findByOwnerIdAndName(ownerUser.getId(), repoName)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Repository '" + owner + "/" + repoName + "' not found."));

        return repository.getId();
    }

    /**
     * Extracts the authenticated user from the security context.
     *
     * @param authentication the current authentication
     * @return the authenticated user
     * @throws EntityNotFoundException if the user cannot be extracted
     */
    private User extractUser(Authentication authentication) {
        User user = RepoAccessGuard.extractUser(authentication);
        if (user == null) {
            throw new EntityNotFoundException("Authentication required.");
        }
        return user;
    }
}
