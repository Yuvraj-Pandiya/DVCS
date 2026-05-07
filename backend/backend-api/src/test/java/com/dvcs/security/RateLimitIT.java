package com.dvcs.security;

import com.dvcs.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the rate-limiting filter (Req 17).
 *
 * <p>Verifies that:
 * <ol>
 *   <li>The 11th request to {@code /api/auth/login} within 1 minute from the same IP
 *       returns HTTP 429 with a {@code Retry-After} header.</li>
 *   <li>The 61st unauthenticated GET request within 1 minute from the same IP
 *       returns HTTP 429.</li>
 * </ol>
 *
 * <p>Extends {@link AbstractIntegrationTest} to reuse the shared Testcontainers setup
 * (postgres:16-alpine + redis:7-alpine) and Redis flush before each test.
 */
@DisplayName("RateLimit Integration Tests")
class RateLimitIT extends AbstractIntegrationTest {

    // -------------------------------------------------------------------------
    // Test: auth endpoint rate limit (10 req/min per IP)
    // -------------------------------------------------------------------------

    /**
     * Sends 11 POST requests to {@code /api/auth/login} and verifies that the
     * 11th returns HTTP 429 with a {@code Retry-After} header.
     *
     * <p>The auth endpoint limit is 10 req/min per IP (Req 17).
     */
    @Test
    @DisplayName("11th login request from same IP returns 429 with Retry-After header")
    void authEndpoint_exceedsLimit_returns429() throws Exception {
        String loginBody = """
                {"username":"nonexistent","password":"wrong"}
                """;

        // Send 10 requests — all should be processed (may return 401 for bad credentials)
        for (int i = 1; i <= 10; i++) {
            final int requestNum = i;
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody))
                    .andExpect(result -> assertThat(result.getResponse().getStatus())
                            .as("Request %d should not be rate-limited", requestNum)
                            .isNotEqualTo(429));
        }

        // 11th request should be rate-limited
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isTooManyRequests())
                .andReturn();

        String retryAfter = result.getResponse().getHeader("Retry-After");
        assertThat(retryAfter)
                .as("Retry-After header must be present on 429 response")
                .isNotNull()
                .isNotBlank();

        long retryAfterSeconds = Long.parseLong(retryAfter);
        assertThat(retryAfterSeconds)
                .as("Retry-After must be a positive number of seconds")
                .isPositive();
    }

    // -------------------------------------------------------------------------
    // Test: unauthenticated read rate limit (60 req/min per IP)
    // -------------------------------------------------------------------------

    /**
     * Sends 61 unauthenticated GET requests to a public endpoint and verifies
     * that the 61st returns HTTP 429.
     *
     * <p>The unauthenticated read limit is 60 req/min per IP (Req 17).
     */
    @Test
    @DisplayName("61st unauthenticated request from same IP returns 429")
    void unauthenticatedReads_exceedsLimit_returns429() throws Exception {
        // Send 60 requests — all should pass through (may return 404 for unknown repos)
        for (int i = 1; i <= 60; i++) {
            final int requestNum = i;
            mockMvc.perform(get("/api/repos/nonexistent/repo"))
                    .andExpect(result -> assertThat(result.getResponse().getStatus())
                            .as("Request %d should not be rate-limited", requestNum)
                            .isNotEqualTo(429));
        }

        // 61st request should be rate-limited
        MvcResult result = mockMvc.perform(get("/api/repos/nonexistent/repo"))
                .andExpect(status().isTooManyRequests())
                .andReturn();

        String retryAfter = result.getResponse().getHeader("Retry-After");
        assertThat(retryAfter)
                .as("Retry-After header must be present on 429 response")
                .isNotNull()
                .isNotBlank();
    }
}
