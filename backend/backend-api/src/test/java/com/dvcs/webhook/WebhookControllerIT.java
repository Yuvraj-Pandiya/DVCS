package com.dvcs.webhook;

import com.dvcs.AbstractIntegrationTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code /api/repos/{owner}/{repo}/webhooks} endpoints.
 *
 * <p>Covers: create webhook (201), POST /{id}/test delivers ping (200).
 *
 * <p>Uses WireMock to simulate the webhook target URL.
 */
@DisplayName("WebhookController Integration Tests")
class WebhookControllerIT extends AbstractIntegrationTest {

    private WireMockServer wireMockServer;

    private String ownerUsername;
    private String ownerToken;
    private String repoName;

    @BeforeEach
    void setUpRepoAndWireMock() throws Exception {
        // Start WireMock on a random port
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        com.github.tomakehurst.wiremock.client.WireMock.configureFor("localhost", wireMockServer.port());

        // Stub the webhook endpoint to return 200
        stubFor(post(urlEqualTo("/webhook"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("OK")));

        ownerUsername = uniqueUsername("webhookowner");
        ownerToken = registerAndLogin(ownerUsername, "WebhookPass123!");
        repoName = "webhook-test-repo";

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/repos")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", repoName,
                                "isPrivate", false
                        ))))
                .andExpect(status().isCreated());
    }

    @AfterEach
    void tearDownWireMock() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    // -------------------------------------------------------------------------
    // POST /api/repos/{owner}/{repo}/webhooks
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST webhooks returns 201 with created webhook")
    void createWebhook_validRequest_returns201() throws Exception {
        String webhookUrl = "http://localhost:" + wireMockServer.port() + "/webhook";

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/repos/{owner}/{repo}/webhooks", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "url", webhookUrl,
                                "secret", "my-webhook-secret",
                                "events", List.of("push", "pull_request")
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value(webhookUrl))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.id").isNumber());
    }

    // -------------------------------------------------------------------------
    // POST /api/repos/{owner}/{repo}/webhooks/{id}/test
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST webhooks/{id}/test delivers ping and returns 200")
    void testWebhook_validWebhook_returns200WithDeliveryResult() throws Exception {
        String webhookUrl = "http://localhost:" + wireMockServer.port() + "/webhook";

        // Create webhook
        MvcResult createResult = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/api/repos/{owner}/{repo}/webhooks", ownerUsername, repoName)
                                .header("Authorization", "Bearer " + ownerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "url", webhookUrl,
                                        "secret", "test-secret",
                                        "events", List.of("push")
                                ))))
                .andExpect(status().isCreated())
                .andReturn();

        long webhookId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asLong();

        // Test webhook (ping delivery)
        MvcResult testResult = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                                "/api/repos/{owner}/{repo}/webhooks/{id}/test",
                                ownerUsername, repoName, webhookId)
                                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();

        // Verify the delivery result contains status info
        String responseBody = testResult.getResponse().getContentAsString();
        assertThat(responseBody).isNotBlank();

        // Verify WireMock received the ping request
        verify(postRequestedFor(urlEqualTo("/webhook")));
    }

    // -------------------------------------------------------------------------
    // GET /api/repos/{owner}/{repo}/webhooks
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET webhooks returns 200 with list of webhooks")
    void listWebhooks_returns200() throws Exception {
        String webhookUrl = "http://localhost:" + wireMockServer.port() + "/webhook";

        // Create a webhook first
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/repos/{owner}/{repo}/webhooks", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "url", webhookUrl,
                                "secret", "list-secret",
                                "events", List.of("push")
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/repos/{owner}/{repo}/webhooks", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].url").value(webhookUrl));
    }
}
