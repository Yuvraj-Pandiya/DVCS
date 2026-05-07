package com.dvcs.pipeline;

import com.dvcs.AbstractPipelineIntegrationTest;
import com.dvcs.auth.dto.LoginRequest;
import com.dvcs.auth.dto.RegisterRequest;
import com.dvcs.auth.repository.UserRepository;
import com.dvcs.git.event.PushEvent;
import com.dvcs.pipeline.repository.PipelineRunRepository;
import com.dvcs.repository.domain.Collaborator;
import com.dvcs.repository.domain.Repository;
import com.dvcs.repository.repository.CollaboratorRepository;
import com.dvcs.repository.repository.RepoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.TimeUnit;

/**
 * Integration tests for {@link com.dvcs.pipeline.controller.PipelineController}.
 *
 * <p>Tests:
 * <ol>
 *   <li>Trigger a push event, poll the pipeline list until the run reaches SUCCESS or FAILURE
 *       (max 30 seconds), then verify the stages JSON contains build and test entries with timing.</li>
 *   <li>Verify the detail endpoint returns the full run including parsed stages.</li>
 *   <li>Verify the list endpoint returns paginated results ordered newest-first.</li>
 * </ol>
 *
 * <p>Requirement 13: CI/CD Pipeline Simulation.
 */
class PipelineControllerIT extends AbstractPipelineIntegrationTest {

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
    private PipelineRunRepository pipelineRunRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

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
        ownerUsername = "pipelineowner_" + ts;
        repoName = "pipeline-test-repo-" + ts;

        // ---- Register & login owner ----
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(ownerUsername,
                                        ownerUsername + "@example.com", "password123"))))
                .andExpect(status().isCreated());

        MvcResult ownerLogin = mockMvc.perform(post("/api/auth/login")
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
                .description("Pipeline test repository")
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
    // Test 1: Push event triggers pipeline; poll until terminal status
    // =========================================================================

    @Test
    @DisplayName("Push event triggers pipeline run; run reaches SUCCESS or FAILURE within 30s")
    void pushEvent_triggersPipeline_reachesTerminalStatus() throws Exception {
        String commitSha = "aabbccdd" + "0".repeat(56);

        // Publish a push event directly (simulates what ReceivePackServiceImpl does)
        eventPublisher.publishEvent(
                new PushEvent(repoId, ownerId, "main", commitSha, List.of(commitSha)));

        // Poll the pipeline list until the run is no longer PENDING or RUNNING (max 30s)
        await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    MvcResult result = mockMvc.perform(
                                    get("/api/repos/{owner}/{repo}/pipelines", ownerUsername, repoName)
                                            .header("Authorization", "Bearer " + ownerToken))
                            .andExpect(status().isOk())
                            .andReturn();

                    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
                    JsonNode content = body.get("content");
                    assertThat(content).isNotNull();
                    assertThat(content.size()).isGreaterThan(0);

                    String runStatus = content.get(0).get("status").asText();
                    assertThat(runStatus)
                            .as("Pipeline run must reach a terminal status")
                            .isIn("SUCCESS", "FAILURE");
                });
    }

    // =========================================================================
    // Test 2: Detail endpoint returns parsed stages with timing
    // =========================================================================

    @Test
    @DisplayName("GET /api/pipelines/{id} returns full run detail with build and test stages")
    void getPipelineDetail_returnsParsedStagesWithTiming() throws Exception {
        String commitSha = "11223344" + "0".repeat(56);

        // Publish push event
        eventPublisher.publishEvent(
                new PushEvent(repoId, ownerId, "main", commitSha, List.of(commitSha)));

        // Wait for the run to complete
        await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> pipelineRunRepository
                        .findByRepoIdAndCommitSha(repoId, commitSha)
                        .stream()
                        .anyMatch(r -> "SUCCESS".equals(r.getStatus()) || "FAILURE".equals(r.getStatus())));

        // Get the run ID from the list endpoint
        MvcResult listResult = mockMvc.perform(
                        get("/api/repos/{owner}/{repo}/pipelines", ownerUsername, repoName)
                                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode listBody = objectMapper.readTree(listResult.getResponse().getContentAsString());
        // Find the run matching our commitSha
        Long runId = null;
        for (JsonNode run : listBody.get("content")) {
            if (commitSha.equals(run.get("commitSha").asText())) {
                runId = run.get("id").asLong();
                break;
            }
        }
        assertThat(runId).as("Pipeline run for commitSha must appear in list").isNotNull();

        // Fetch the detail
        MvcResult detailResult = mockMvc.perform(
                        get("/api/pipelines/{id}", runId)
                                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(runId))
                .andExpect(jsonPath("$.commitSha").value(commitSha))
                .andReturn();

        JsonNode detail = objectMapper.readTree(detailResult.getResponse().getContentAsString());
        JsonNode stages = detail.get("stages");

        // Verify stages list is present and non-empty
        assertThat(stages).isNotNull();
        assertThat(stages.isArray()).isTrue();
        assertThat(stages.size()).isGreaterThanOrEqualTo(1);

        // Verify build stage is always present
        JsonNode buildStage = findStageByName(stages, "build");
        assertThat(buildStage).as("Build stage must be present").isNotNull();
        assertThat(buildStage.get("name").asText()).isEqualTo("build");
        assertThat(buildStage.get("status").asText()).isIn("SUCCESS", "FAILURE");
        assertThat(buildStage.get("startedAt").asText()).isNotBlank();
        assertThat(buildStage.get("finishedAt").asText()).isNotBlank();

        // If build succeeded, test stage must also be present
        String buildStatus = buildStage.get("status").asText();
        if ("SUCCESS".equals(buildStatus)) {
            JsonNode testStage = findStageByName(stages, "test");
            assertThat(testStage).as("Test stage must be present when build succeeds").isNotNull();
            assertThat(testStage.get("name").asText()).isEqualTo("test");
            assertThat(testStage.get("status").asText()).isIn("SUCCESS", "FAILURE");
            assertThat(testStage.get("startedAt").asText()).isNotBlank();
            assertThat(testStage.get("finishedAt").asText()).isNotBlank();
        }
    }

    // =========================================================================
    // Test 3: List endpoint returns paginated results ordered newest-first
    // =========================================================================

    @Test
    @DisplayName("GET /api/repos/{owner}/{repo}/pipelines returns paginated runs ordered newest-first")
    void listPipelines_returnsPaginatedResultsNewestFirst() throws Exception {
        String sha1 = "aaaaaaaa" + "0".repeat(56);
        String sha2 = "bbbbbbbb" + "0".repeat(56);

        // Publish two push events sequentially
        eventPublisher.publishEvent(
                new PushEvent(repoId, ownerId, "main", sha1, List.of(sha1)));
        // Small delay to ensure different createdAt timestamps
        Thread.sleep(50);
        eventPublisher.publishEvent(
                new PushEvent(repoId, ownerId, "main", sha2, List.of(sha2)));

        // Wait for both runs to be created (they may still be running)
        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .until(() -> pipelineRunRepository
                        .findByRepoIdAndCommitSha(repoId, sha1).size() >= 1
                        && pipelineRunRepository
                        .findByRepoIdAndCommitSha(repoId, sha2).size() >= 1);

        // Verify list returns at least 2 runs
        MvcResult result = mockMvc.perform(
                        get("/api/repos/{owner}/{repo}/pipelines", ownerUsername, repoName)
                                .header("Authorization", "Bearer " + ownerToken)
                                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode content = body.get("content");
        assertThat(content.size()).isGreaterThanOrEqualTo(2);

        // Verify newest-first ordering: sha2 was pushed after sha1, so it should appear first
        // (or at least both should be present)
        boolean sha2Found = false;
        boolean sha1Found = false;
        for (JsonNode run : content) {
            String sha = run.get("commitSha").asText();
            if (sha2.equals(sha)) sha2Found = true;
            if (sha1.equals(sha)) sha1Found = true;
        }
        assertThat(sha1Found).as("sha1 run must appear in list").isTrue();
        assertThat(sha2Found).as("sha2 run must appear in list").isTrue();

        // Verify sha2 appears before sha1 (newest-first)
        int sha2Index = -1, sha1Index = -1;
        for (int i = 0; i < content.size(); i++) {
            String sha = content.get(i).get("commitSha").asText();
            if (sha2.equals(sha)) sha2Index = i;
            if (sha1.equals(sha)) sha1Index = i;
        }
        assertThat(sha2Index).as("sha2 (newer) must appear before sha1 (older)").isLessThan(sha1Index);
    }

    // =========================================================================
    // Test 4: Detail endpoint returns 404 for unknown ID
    // =========================================================================

    @Test
    @DisplayName("GET /api/pipelines/{id} returns 404 for unknown pipeline run ID")
    void getPipelineDetail_unknownId_returns404() throws Exception {
        mockMvc.perform(
                        get("/api/pipelines/{id}", Long.MAX_VALUE)
                                .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Finds a stage node by name within a JSON array of stage objects.
     *
     * @param stages the JSON array of stage objects
     * @param name   the stage name to find
     * @return the matching stage node, or {@code null} if not found
     */
    private JsonNode findStageByName(JsonNode stages, String name) {
        for (JsonNode stage : stages) {
            if (name.equals(stage.get("name").asText())) {
                return stage;
            }
        }
        return null;
    }
}
