package com.dvcs.notification;

import com.dvcs.AbstractNotificationIntegrationTest;
import com.dvcs.auth.dto.LoginRequest;
import com.dvcs.auth.dto.RegisterRequest;
import com.dvcs.notification.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link com.dvcs.notification.controller.NotificationController}.
 *
 * <p>Tests the notification lifecycle: create via service, list unread, mark as read.
 * Uses Testcontainers for PostgreSQL and Redis.
 *
 * <p>Requirement 14: Real-Time Notifications.
 */
class NotificationControllerIT extends AbstractNotificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationService notificationService;

    private String accessToken;
    private Long userId;
    private String username;

    @BeforeEach
    void setUp() throws Exception {
        // Register and login a unique user for each test
        username = "notifuser_" + System.currentTimeMillis();
        String email = username + "@example.com";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(username, email, "password123"))))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(username, "password123"))))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        accessToken = objectMapper.readTree(responseBody).get("accessToken").asText();

        // Extract userId from the JWT claims via the list endpoint (or parse the token)
        // We'll get it by calling the list endpoint and checking the response
        // Actually, we need the userId to call NotificationService directly.
        // Extract it from the JWT token's sub claim.
        userId = extractUserIdFromToken(accessToken);
    }

    // =========================================================================
    // Test: Create notification via service, verify it appears in GET /api/notifications
    // =========================================================================

    @Test
    @DisplayName("GET /api/notifications — returns unread notifications created via service")
    void listNotifications_afterCreate_returnsNotification() throws Exception {
        // Create a notification via the service
        notificationService.createNotification(userId, "pull_request", 42L, "review_approve");

        // Call GET /api/notifications
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].userId", is(userId.intValue())))
                .andExpect(jsonPath("$.content[0].subjectType", is("pull_request")))
                .andExpect(jsonPath("$.content[0].subjectId", is(42)))
                .andExpect(jsonPath("$.content[0].reason", is("review_approve")))
                .andExpect(jsonPath("$.content[0].read", is(false)))
                .andExpect(jsonPath("$.content[0].id", notNullValue()));
    }

    // =========================================================================
    // Test: Mark notification as read, verify it no longer appears in unread list
    // =========================================================================

    @Test
    @DisplayName("PATCH /api/notifications/{id}/read — marks notification as read, removes from unread list")
    void markAsRead_removesFromUnreadList() throws Exception {
        // Create a notification via the service
        var notification = notificationService.createNotification(
                userId, "issue", 10L, "issue_comment");
        Long notificationId = notification.getId();

        // Verify it appears in the unread list
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));

        // Mark it as read
        mockMvc.perform(patch("/api/notifications/{id}/read", notificationId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(notificationId.intValue())))
                .andExpect(jsonPath("$.read", is(true)));

        // Verify it no longer appears in the unread list
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    // =========================================================================
    // Test: Mark notification belonging to another user returns 403
    // =========================================================================

    @Test
    @DisplayName("PATCH /api/notifications/{id}/read — returns 403 when notification belongs to another user")
    void markAsRead_otherUsersNotification_returns403() throws Exception {
        // Register a second user
        String otherUsername = "othernotif_" + System.currentTimeMillis();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(otherUsername,
                                        otherUsername + "@example.com", "password123"))))
                .andExpect(status().isCreated());

        MvcResult otherLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(otherUsername, "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        String otherToken = objectMapper.readTree(
                otherLogin.getResponse().getContentAsString()).get("accessToken").asText();
        Long otherUserId = extractUserIdFromToken(otherToken);

        // Create a notification for the OTHER user
        var notification = notificationService.createNotification(
                otherUserId, "pull_request", 99L, "merged");
        Long notificationId = notification.getId();

        // Try to mark it as read as the FIRST user — should get 403
        mockMvc.perform(patch("/api/notifications/{id}/read", notificationId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Test: Unauthenticated request returns 401
    // =========================================================================

    @Test
    @DisplayName("GET /api/notifications — unauthenticated request returns 401")
    void listNotifications_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Extracts the user ID from a JWT access token by parsing the base64-encoded payload.
     *
     * @param token the JWT access token
     * @return the user ID from the {@code sub} claim
     */
    private Long extractUserIdFromToken(String token) throws Exception {
        // JWT format: header.payload.signature
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid JWT format");
        }
        // Decode the payload (base64url encoded)
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
        return objectMapper.readTree(payload).get("sub").asLong();
    }
}
