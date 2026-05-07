package com.dvcs.notification.controller;

import com.dvcs.auth.domain.User;
import com.dvcs.notification.domain.Notification;
import com.dvcs.notification.repository.NotificationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the notification API.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/notifications} — returns paginated unread notifications for the
 *       authenticated user</li>
 *   <li>{@code PATCH /api/notifications/{id}/read} — marks a notification as read</li>
 * </ul>
 *
 * <p>Requirement 14.3 and 14.4: Notification list and mark-as-read.
 */
@Tag(name = "Notifications", description = "User notification management")
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * Returns a paginated list of unread notifications for the authenticated user.
     *
     * @param pageable pagination parameters (page, size, sort)
     * @return 200 with a page of unread notifications
     */
    @Operation(summary = "List paginated unread notifications for the authenticated user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Unread notifications returned"),
        @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    @GetMapping
    public ResponseEntity<Page<Notification>> listUnreadNotifications(Pageable pageable) {
        Long userId = getAuthenticatedUserId();
        Page<Notification> notifications =
                notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId, pageable);
        return ResponseEntity.ok(notifications);
    }

    /**
     * Marks a notification as read.
     *
     * <p>Returns 403 if the notification does not belong to the authenticated user.
     * Returns 404 if the notification does not exist.
     *
     * @param id the notification ID
     * @return 200 on success, 403 if not the owner, 404 if not found
     */
    @Operation(summary = "Mark a notification as read")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Notification marked as read"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Notification does not belong to the authenticated user"),
        @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    @PatchMapping("/{id}/read")
    public ResponseEntity<Notification> markAsRead(@PathVariable Long id) {
        Long userId = getAuthenticatedUserId();

        Notification notification = notificationRepository.findById(id)
                .orElse(null);

        if (notification == null) {
            return ResponseEntity.notFound().build();
        }

        if (!notification.getUserId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }

        notification.setRead(true);
        notificationRepository.save(notification);
        return ResponseEntity.ok(notification);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts the authenticated user's ID from the {@link SecurityContextHolder}.
     *
     * @return the authenticated user's ID
     * @throws IllegalStateException if no authenticated user is present
     */
    private Long getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user in security context");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof User user) {
            return user.getId();
        }
        throw new IllegalStateException("Unexpected principal type: " + principal.getClass());
    }
}
