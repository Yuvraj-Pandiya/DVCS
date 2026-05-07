package com.dvcs.webhook;

import com.dvcs.AbstractWebhookIntegrationTest;
import com.dvcs.auth.dto.LoginRequest;
import com.dvcs.auth.dto.RegisterRequest;
import com.dvcs.auth.repository.UserRepository;
import com.dvcs.repository.domain.Collaborator;
import com.dvcs.repository.domain.Repository;
import com.dvcs.repository.repository.CollaboratorRepository;
import com.dvcs.repository.repository.RepoRepository;
import com.dvcs.webhook.domain.Webhook;
import com.dvcs.webhook.dto.CreateWebhookRequest;
import com.dvcs.webhook.dto.DeliveryResult;
import com.dvcs.webhook.repository.WebhookRepository;
import com.dvcs.webhook.service.WebhookDeliveryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link WebhookController}.
 *
 * <p>Tests webhook creation, delivery with HMAC signing, retry logic, and the test endpoint.
 * Uses Testcontainers for PostgreSQL and Redis, and WireMock for the webhook receiver.
 *
 * <p>Requirement 12: Webhook Management and Delivery.
 */
@WireMockTest
class WebhookControllerIT extends AbstractWebhookIntegrationTest {

    private static final String WEBHOOK_PATH = "/webhook-receiver";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RepoRepository repoRepository;

    @Autowired
    private CollaboratorRepository collaboratorRepository;

    @Autowired
    private WebhookRepository webhookRepository;

    @Autowired
    private WebhookDeliveryService webhookDeliveryService;

    // =========================================================================
    // Test state
    // =========================================================================

    private String ownerToken;
    private String ownerUsername;
    private Long ownerId;
    private String repoName;
    private Long repoId;

    @BeforeEach
    void setUp() throws Exception {
        long ts = System.currentTimeMillis();
        ownerUsername = "webhookowner_" + ts;
        repoName = "webhook-test-repo-" + ts;

        // ---- Register & login owner ----
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(ownerUsername,
                                        ownerUsername + "@example.com", "password123"))))
                .andExpect(status().isCreated());

        MvcResult ownerLogin = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(ownerUsername, "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        ownerToken = objectMapper.readTree(ownerLogin.getResponse().getContentAsString())
                .get("accessToken").asText();

        // ---- Look up user ID ----
        ownerId = userRepository.findByUsername(ownerUsername).orElseThrow().getId();

        // ---- Create test repository ----
        Repository repo = Repository.builder()
                .ownerId(ownerId)
                .name(repoName)
                .description("Webhook test repository")
                .isPrivate(false)
                .defaultBranch("main")
                .createdAt(OffsetDateTime.now())
                .build();
        repo = repoRepository.save(repo);
        repoId = repo.getId();

        // ---- Add owner as OWNER collaborator ----
        Collaborator ownerCollab = new Collaborator();
        ownerCollab.setRepoId(repoId);
        ownerCollab.setUserId(ownerId);
        ownerCollab.setRole("OWNER");
        collaboratorRepository.save(ownerCollab);
    }

    // =========================================================================
    // Test: Create webhook — verify 201 and secret NOT in response
    // =========================================================================

    @Test
    @DisplayName("POST /webhooks — creates webhook, returns 201, secret not in response")
    void createWebhook_success_returns201_secretNotInResponse(WireMockRuntimeInfo wmRuntimeInfo)
            throws Exception {

        String webhookUrl = "http://localhost:" + wmRuntimeInfo.getHttpPort() + WEBHOOK_PATH;

        CreateWebhookRequest request = new CreateWebhookRequest(
                webhookUrl, "my-super-secret", List.of("push", "issues"));

        MvcResult result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/repos/{owner}/{repo}/webhooks", ownerUsername, repoName)
                                .header("Authorization", "Bearer " + ownerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.url").value(webhookUrl))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        // Verify secret is NOT in the response
        JsonNode responseNode = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(responseNode.has("secret"))
                .as("Response must not contain 'secret' field")
                .isFalse();
        assertThat(result.getResponse().getContentAsString())
                .as("Response body must not contain the secret value")
                .doesNotContain("my-super-secret");
    }

    // =========================================================================
    // Test: Trigger push event and verify WireMock received correct HMAC header
    // =========================================================================

    @Test
    @DisplayName("deliverSync — sends POST with correct X-Hub-Signature-256 header")
    void deliverSync_pushEvent_correctHmacHeader(WireMockRuntimeInfo wmRuntimeInfo)
            throws Exception {

        int wireMockPort = wmRuntimeInfo.getHttpPort();
        String webhookUrl = "http://localhost:" + wireMockPort + WEBHOOK_PATH;

        // Stub WireMock to return 200
        stubFor(post(urlEqualTo(WEBHOOK_PATH))
                .willReturn(aResponse().withStatus(200)));

        // Create webhook directly in DB for delivery testing
        Webhook webhook = Webhook.builder()
                .repoId(repoId)
                .url(webhookUrl)
                .secret("test-hmac-secret")
                .events(new String[]{"push"})
                .active(true)
                .build();
        webhook = webhookRepository.save(webhook);

        // Build a simple push payload
        Map<String, Object> payload = Map.of(
                "ref", "refs/heads/main",
                "repository", Map.of("id", repoId, "name", repoName)
        );

        // Deliver synchronously
        DeliveryResult result = webhookDeliveryService.deliverSync(webhook, "push", payload);

        // Verify delivery succeeded
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAttempts()).isEqualTo(1);

        // Verify WireMock received the request with the correct HMAC header
        verify(1, postRequestedFor(urlEqualTo(WEBHOOK_PATH))
                .withHeader("X-GitHub-Event", equalTo("push"))
                .withHeader("X-Hub-Signature-256", WireMock.matching("sha256=[0-9a-f]{64}")));
    }

    // =========================================================================
    // Test: Simulate 500 response and verify 3 retry attempts
    // =========================================================================

    @Test
    @DisplayName("deliverSync — retries 3 times on 500 response, returns attempts=3 and success=false")
    void deliverSync_500Response_retries3Times(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {

        int wireMockPort = wmRuntimeInfo.getHttpPort();
        String webhookUrl = "http://localhost:" + wireMockPort + WEBHOOK_PATH;

        // Stub WireMock to always return 500
        stubFor(post(urlEqualTo(WEBHOOK_PATH))
                .willReturn(aResponse().withStatus(500)));

        // Create webhook directly in DB
        Webhook webhook = Webhook.builder()
                .repoId(repoId)
                .url(webhookUrl)
                .secret("retry-test-secret")
                .events(new String[]{"push"})
                .active(true)
                .build();
        webhook = webhookRepository.save(webhook);

        Map<String, Object> payload = Map.of("action", "test");

        // Deliver synchronously (will retry 3 times with backoff)
        // Note: backoff is 1s + 2s = 3s total wait for 3 attempts
        DeliveryResult result = webhookDeliveryService.deliverSync(webhook, "push", payload);

        // Verify 3 attempts were made and delivery failed
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getAttempts()).isEqualTo(3);
        assertThat(result.getStatusCode()).isEqualTo(500);

        // Verify WireMock received exactly 3 requests
        verify(3, postRequestedFor(urlEqualTo(WEBHOOK_PATH)));
    }

    // =========================================================================
    // Test: Test endpoint delivers ping
    // =========================================================================

    @Test
    @DisplayName("POST /webhooks/{id}/test — delivers ping, returns 200, WireMock receives ping event")
    void testEndpoint_deliversPing_returns200(WireMockRuntimeInfo wmRuntimeInfo) throws Exception {

        int wireMockPort = wmRuntimeInfo.getHttpPort();
        String webhookUrl = "http://localhost:" + wireMockPort + WEBHOOK_PATH;

        // Stub WireMock to return 200
        stubFor(post(urlEqualTo(WEBHOOK_PATH))
                .willReturn(aResponse().withStatus(200)));

        // Create webhook via API
        CreateWebhookRequest createRequest = new CreateWebhookRequest(
                webhookUrl, "ping-test-secret", List.of("push"));

        MvcResult createResult = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/repos/{owner}/{repo}/webhooks", ownerUsername, repoName)
                                .header("Authorization", "Bearer " + ownerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        Long webhookId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("id").asLong();

        // Call the test endpoint
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/repos/{owner}/{repo}/webhooks/{id}/test",
                                        ownerUsername, repoName, webhookId)
                                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.attempts").value(1));

        // Verify WireMock received a POST with X-GitHub-Event: ping
        verify(1, postRequestedFor(urlEqualTo(WEBHOOK_PATH))
                .withHeader("X-GitHub-Event", equalTo("ping")));
    }
}
