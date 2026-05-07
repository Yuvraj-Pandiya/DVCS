package com.dvcs.auth;

import com.dvcs.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code /api/auth} endpoints.
 *
 * <p>Covers: register (201), login (200 with token), refresh (200 with new token).
 */
@DisplayName("AuthController Integration Tests")
class AuthControllerIT extends AbstractIntegrationTest {

    // -------------------------------------------------------------------------
    // POST /api/auth/register
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/auth/register returns 201 for valid payload")
    void register_validPayload_returns201() throws Exception {
        String username = uniqueUsername("reguser");
        String body = objectMapper.writeValueAsString(Map.of(
                "username", username,
                "email", username + "@example.com",
                "password", "SecurePass123!"
        ));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/auth/register returns 409 for duplicate username")
    void register_duplicateUsername_returns409() throws Exception {
        String username = uniqueUsername("dupuser");
        String body = objectMapper.writeValueAsString(Map.of(
                "username", username,
                "email", username + "@example.com",
                "password", "SecurePass123!"
        ));

        // First registration succeeds
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // Second registration with same username fails
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    // -------------------------------------------------------------------------
    // POST /api/auth/login
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/auth/login returns 200 with accessToken for valid credentials")
    void login_validCredentials_returns200WithToken() throws Exception {
        String username = uniqueUsername("loginuser");
        String password = "TestPass456!";

        // Register first
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "email", username + "@example.com",
                                "password", password
                        ))))
                .andExpect(status().isCreated());

        // Login
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        String accessToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
        assertThat(accessToken).isNotBlank();

        // Verify refresh token cookie is set
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains("refreshToken");
        assertThat(setCookie).contains("HttpOnly");
    }

    @Test
    @DisplayName("POST /api/auth/login returns 401 for wrong password")
    void login_wrongPassword_returns401() throws Exception {
        String username = uniqueUsername("wrongpw");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "email", username + "@example.com",
                                "password", "CorrectPass!"
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "WrongPass!"
                        ))))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // POST /api/auth/refresh
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/auth/refresh returns 200 with new accessToken when cookie is present")
    void refresh_validCookie_returns200WithNewToken() throws Exception {
        String username = uniqueUsername("refreshuser");
        String password = "RefreshPass789!";

        // Register
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "email", username + "@example.com",
                                "password", password
                        ))))
                .andExpect(status().isCreated());

        // Login to get refresh token cookie
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        // Extract refresh token from Set-Cookie header
        String setCookie = loginResult.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).isNotNull();
        String refreshToken = extractRefreshTokenFromCookie(setCookie);
        assertThat(refreshToken).isNotBlank();

        // Use refresh token to get new access token
        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("refreshToken", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        String newAccessToken = objectMapper.readTree(refreshResult.getResponse().getContentAsString())
                .get("accessToken").asText();
        assertThat(newAccessToken).isNotBlank();
    }

    @Test
    @DisplayName("POST /api/auth/refresh returns 401 when no cookie is present")
    void refresh_noCookie_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String extractRefreshTokenFromCookie(String setCookieHeader) {
        // Format: "refreshToken=<value>; HttpOnly; ..."
        for (String part : setCookieHeader.split(";")) {
            part = part.trim();
            if (part.startsWith("refreshToken=")) {
                return part.substring("refreshToken=".length());
            }
        }
        return "";
    }
}
