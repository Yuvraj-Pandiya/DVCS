package com.dvcs.notification;

import com.dvcs.AbstractIntegrationTest;
import com.dvcs.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code /api/notifications} endpoints.
 *
 * <p>Covers: create notification via service, GET /api/notifications (200),
 * PATCH /{id}/read (200).
 */
@DisplayName("NotificationController Integration Tests")
class NotificationControllerIT extends AbstractIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private com.dvcs.auth.repository.UserRepository userRepository;

    private String username;
    private String token;
    private Long userId;

    @BeforeEach
    void setUpUser() throws Exception {
        username = uniqueUsername("notifuser");
        token = registerAndLogin(username, "NotifPass123!");
        userId = userRepository.findByUsername(username)
                .map(com.dvcs.auth.domain.User::getId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + username));
    }

    // -------------------------------------------------------------------------
    // GET /api/notifications
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/notifications returns 200 with unread notifications")
    void listNotifications_withUnreadNotifications_returns200() throws Exception {
        // Create notifications via service
        notificationService.createNotification(userId, "pull_request", 1L, "review_approve");
        notificationService.createNotification(userId, "issue", 2L, "issue_comment");

        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].read").value(false));
    }

    @Test
    @DisplayName("GET /api/notifications returns 200 with empty list when no notifications")
    void listNotifications_noNotifications_returnsEmptyPage() throws Exception {
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    @DisplayName("GET /api/notifications returns 401 for unauthenticated request")
    void listNotifications_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // PATCH /api/notifications/{id}/read
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("PATCH /api/notifications/{id}/read returns 200 and marks notification as read")
    void markAsRead_ownNotification_returns200() throws Exception {
        // Create a notification
        var notification = notificationService.createNotification(
                userId, "pull_request", 10L, "review_approve");

        // Mark as read
        mockMvc.perform(patch("/api/notifications/{id}/read", notification.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true))
                .andExpect(jsonPath("$.id").value(notification.getId()));
    }

    @Test
    @DisplayName("PATCH /api/notifications/{id}/read returns 403 for another user's notification")
    void markAsRead_otherUsersNotification_returns403() throws Exception {
        // Create another user
        String otherUsername = uniqueUsername("othernotif");
        registerAndLogin(otherUsername, "OtherNotifPass123!");
        Long otherUserId = userRepository.findByUsername(otherUsername)
                .map(com.dvcs.auth.domain.User::getId)
                .orElseThrow();

        // Create notification for the other user
        var notification = notificationService.createNotification(
                otherUserId, "issue", 20L, "issue_comment");

        // Try to mark it as read with our token — should fail with 403
        mockMvc.perform(patch("/api/notifications/{id}/read", notification.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /api/notifications/{id}/read returns 404 for non-existent notification")
    void markAsRead_nonExistent_returns404() throws Exception {
        mockMvc.perform(patch("/api/notifications/{id}/read", 999999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // Read notifications are excluded from GET /api/notifications
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Read notifications are not returned by GET /api/notifications")
    void listNotifications_excludesReadNotifications() throws Exception {
        // Create two notifications
        var n1 = notificationService.createNotification(userId, "pull_request", 30L, "review_approve");
        notificationService.createNotification(userId, "issue", 31L, "issue_comment");

        // Mark first as read
        mockMvc.perform(patch("/api/notifications/{id}/read", n1.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Only unread notification should be returned
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].subjectId").value(31));
    }
}
