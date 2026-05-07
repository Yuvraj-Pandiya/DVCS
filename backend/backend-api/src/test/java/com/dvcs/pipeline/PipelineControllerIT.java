package com.dvcs.pipeline;

import com.dvcs.AbstractIntegrationTest;
import com.dvcs.git.event.PushEvent;
import com.dvcs.pipeline.repository.PipelineRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.TimeUnit;

/**
 * Integration tests for pipeline endpoints.
 *
 * <p>Covers:
 * <ul>
 *   <li>Trigger push event → pipeline run created</li>
 *   <li>GET /api/repos/{owner}/{repo}/pipelines returns runs</li>
 *   <li>Poll until pipeline reaches SUCCESS or FAILURE</li>
 * </ul>
 */
@DisplayName("PipelineController Integration Tests")
class PipelineControllerIT extends AbstractIntegrationTest {

    private static final String EMPTY_SHA =
            "0000000000000000000000000000000000000000000000000000000000000000";

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PipelineRunRepository pipelineRunRepository;

    private String ownerUsername;
    private String ownerToken;
    private String repoName;
    private Long repoId;
    private Long ownerId;

    @Autowired
    private com.dvcs.auth.repository.UserRepository userRepository;

    @BeforeEach
    void setUpRepoAndUser() throws Exception {
        ownerUsername = uniqueUsername("pipelineowner");
        ownerToken = registerAndLogin(ownerUsername, "PipelinePass123!");

        ownerId = userRepository.findByUsername(ownerUsername)
                .map(com.dvcs.auth.domain.User::getId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + ownerUsername));

        repoName = "pipeline-test-repo";

        MvcResult repoResult = mockMvc.perform(post("/api/repos")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", repoName,
                                "isPrivate", false
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        repoId = objectMapper.readTree(repoResult.getResponse().getContentAsString())
                .get("id").asLong();
    }

    // -------------------------------------------------------------------------
    // Trigger push event → pipeline run created
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Publishing PushEvent creates a pipeline run for the repo")
    void pushEvent_createsPipelineRun() {
        String commitSha = "abc123def456abc123def456abc123def456abc123def456abc123def456abc1";

        // Publish a push event
        eventPublisher.publishEvent(new PushEvent(repoId, ownerId, "main", commitSha, java.util.List.of(commitSha)));

        // Wait for the pipeline run to be created (async)
        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> pipelineRunRepository.findByRepoIdOrderByCreatedAtDesc(
                        repoId, org.springframework.data.domain.PageRequest.of(0, 1))
                        .hasContent());
    }

    // -------------------------------------------------------------------------
    // GET /api/repos/{owner}/{repo}/pipelines
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/repos/{owner}/{repo}/pipelines returns 200 with pipeline runs")
    void listPipelineRuns_returns200() throws Exception {
        // Trigger a pipeline run
        String commitSha = "def456abc123def456abc123def456abc123def456abc123def456abc123def4";
        eventPublisher.publishEvent(new PushEvent(repoId, ownerId, "main", commitSha, java.util.List.of(commitSha)));

        // Wait for the run to be persisted
        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> pipelineRunRepository.findByRepoIdOrderByCreatedAtDesc(
                        repoId, org.springframework.data.domain.PageRequest.of(0, 1))
                        .hasContent());

        // List pipeline runs
        mockMvc.perform(get("/api/repos/{owner}/{repo}/pipelines", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].repoId").value(repoId))
                .andExpect(jsonPath("$.content[0].commitSha").value(commitSha));
    }

    // -------------------------------------------------------------------------
    // Poll until pipeline reaches terminal state
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Pipeline run eventually reaches SUCCESS or FAILURE status")
    void pipelineRun_eventuallyReachesTerminalState() {
        String commitSha = "111222333444555666777888999aaabbbccc111222333444555666777888999a";

        // Trigger pipeline
        eventPublisher.publishEvent(new PushEvent(repoId, ownerId, "main", commitSha, java.util.List.of(commitSha)));

        // Poll until the run reaches a terminal state (SUCCESS or FAILURE)
        // Pipeline takes 3-8 seconds to complete (build: 1-3s, test: 2-5s)
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> {
                    var page = pipelineRunRepository.findByRepoIdOrderByCreatedAtDesc(
                            repoId, org.springframework.data.domain.PageRequest.of(0, 1));
                    if (!page.hasContent()) return false;
                    String status = page.getContent().get(0).getStatus();
                    return "SUCCESS".equals(status) || "FAILURE".equals(status);
                });

        // Verify the final status
        var runs = pipelineRunRepository.findByRepoIdOrderByCreatedAtDesc(
                repoId, org.springframework.data.domain.PageRequest.of(0, 1));
        assertThat(runs.hasContent()).isTrue();
        String finalStatus = runs.getContent().get(0).getStatus();
        assertThat(finalStatus).isIn("SUCCESS", "FAILURE");
    }

    // -------------------------------------------------------------------------
    // GET /api/pipelines/{id}
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/pipelines/{id} returns 200 with pipeline run detail")
    void getPipelineRunDetail_returns200() throws Exception {
        String commitSha = "aaa111bbb222ccc333ddd444eee555fff666aaa111bbb222ccc333ddd444eee5";

        // Trigger pipeline
        eventPublisher.publishEvent(new PushEvent(repoId, ownerId, "main", commitSha, java.util.List.of(commitSha)));

        // Wait for run to be created
        await().atMost(2, TimeUnit.SECONDS)
                .until(() -> pipelineRunRepository.findByRepoIdOrderByCreatedAtDesc(
                        repoId, org.springframework.data.domain.PageRequest.of(0, 1))
                        .hasContent());

        Long runId = pipelineRunRepository.findByRepoIdOrderByCreatedAtDesc(
                repoId, org.springframework.data.domain.PageRequest.of(0, 1))
                .getContent().get(0).getId();

        mockMvc.perform(get("/api/pipelines/{id}", runId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(runId))
                .andExpect(jsonPath("$.repoId").value(repoId));
    }
}
