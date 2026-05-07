package com.dvcs.pullrequest;

import com.dvcs.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code /api/repos/{owner}/{repo}/pulls} endpoints.
 *
 * <p>Covers: open PR (201), submit APPROVE review (201), merge PR (200),
 * attempt merge with CHANGES_REQUESTED (422).
 */
@DisplayName("PullRequestController Integration Tests")
class PullRequestControllerIT extends AbstractIntegrationTest {

    private static final String EMPTY_SHA =
            "0000000000000000000000000000000000000000000000000000000000000000";

    private String ownerUsername;
    private String ownerToken;
    private String repoName;

    @BeforeEach
    void setUpRepoAndBranches() throws Exception {
        ownerUsername = uniqueUsername("prowner");
        ownerToken = registerAndLogin(ownerUsername, "PrPass123!");
        repoName = "pr-test-repo";

        // Create repo
        mockMvc.perform(post("/api/repos")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", repoName,
                                "isPrivate", false
                        ))))
                .andExpect(status().isCreated());

        // Create feature branch (head)
        mockMvc.perform(post("/api/repos/{owner}/{repo}/branches", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "feature/pr-test",
                                "sourceSha", EMPTY_SHA
                        ))))
                .andExpect(status().isCreated());
    }

    // -------------------------------------------------------------------------
    // POST /api/repos/{owner}/{repo}/pulls
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST pulls returns 201 with created PR")
    void openPr_validRequest_returns201() throws Exception {
        mockMvc.perform(post("/api/repos/{owner}/{repo}/pulls", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Add new feature",
                                "body", "This PR adds a new feature",
                                "headBranch", "feature/pr-test",
                                "baseBranch", "main"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Add new feature"))
                .andExpect(jsonPath("$.status").value("open"))
                .andExpect(jsonPath("$.number").isNumber());
    }

    @Test
    @DisplayName("POST pulls returns 422 when head and base branch are the same")
    void openPr_sameBranch_returns422() throws Exception {
        mockMvc.perform(post("/api/repos/{owner}/{repo}/pulls", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Invalid PR",
                                "headBranch", "main",
                                "baseBranch", "main"
                        ))))
                .andExpect(status().isUnprocessableEntity());
    }

    // -------------------------------------------------------------------------
    // POST /api/repos/{owner}/{repo}/pulls/{number}/review
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST pulls/{number}/review with APPROVE returns 201")
    void submitReview_approve_returns201() throws Exception {
        // Open a PR
        MvcResult prResult = mockMvc.perform(post("/api/repos/{owner}/{repo}/pulls", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Review test PR",
                                "headBranch", "feature/pr-test",
                                "baseBranch", "main"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        int prNumber = objectMapper.readTree(prResult.getResponse().getContentAsString())
                .get("number").asInt();

        // Create a reviewer (different user)
        String reviewerUsername = uniqueUsername("reviewer");
        String reviewerToken = registerAndLogin(reviewerUsername, "ReviewPass123!");

        // Submit APPROVE review
        mockMvc.perform(post("/api/repos/{owner}/{repo}/pulls/{number}/review",
                        ownerUsername, repoName, prNumber)
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "verdict", "APPROVE",
                                "body", "Looks good to me!"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verdict").value("APPROVE"));
    }

    // -------------------------------------------------------------------------
    // POST /api/repos/{owner}/{repo}/pulls/{number}/merge
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST pulls/{number}/merge returns 200 after APPROVE review")
    void mergePr_withApproval_returns200() throws Exception {
        // Open a PR
        MvcResult prResult = mockMvc.perform(post("/api/repos/{owner}/{repo}/pulls", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Merge test PR",
                                "headBranch", "feature/pr-test",
                                "baseBranch", "main"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        int prNumber = objectMapper.readTree(prResult.getResponse().getContentAsString())
                .get("number").asInt();

        // Create a reviewer and approve
        String reviewerUsername = uniqueUsername("mergereviewer");
        String reviewerToken = registerAndLogin(reviewerUsername, "MergeReviewPass123!");

        mockMvc.perform(post("/api/repos/{owner}/{repo}/pulls/{number}/review",
                        ownerUsername, repoName, prNumber)
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "verdict", "APPROVE",
                                "body", "LGTM"
                        ))))
                .andExpect(status().isCreated());

        // Merge the PR
        mockMvc.perform(post("/api/repos/{owner}/{repo}/pulls/{number}/merge",
                        ownerUsername, repoName, prNumber)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST pulls/{number}/merge returns 422 when CHANGES_REQUESTED review exists")
    void mergePr_withChangesRequested_returns422() throws Exception {
        // Open a PR
        MvcResult prResult = mockMvc.perform(post("/api/repos/{owner}/{repo}/pulls", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Blocked merge PR",
                                "headBranch", "feature/pr-test",
                                "baseBranch", "main"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        int prNumber = objectMapper.readTree(prResult.getResponse().getContentAsString())
                .get("number").asInt();

        // Create a reviewer and request changes
        String reviewerUsername = uniqueUsername("changesreviewer");
        String reviewerToken = registerAndLogin(reviewerUsername, "ChangesPass123!");

        mockMvc.perform(post("/api/repos/{owner}/{repo}/pulls/{number}/review",
                        ownerUsername, repoName, prNumber)
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "verdict", "CHANGES_REQUESTED",
                                "body", "Please fix the tests"
                        ))))
                .andExpect(status().isCreated());

        // Attempt to merge — should fail with 422
        mockMvc.perform(post("/api/repos/{owner}/{repo}/pulls/{number}/merge",
                        ownerUsername, repoName, prNumber)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("POST pulls/{number}/merge returns 422 when no reviews exist")
    void mergePr_noReviews_returns422() throws Exception {
        // Open a PR
        MvcResult prResult = mockMvc.perform(post("/api/repos/{owner}/{repo}/pulls", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "No review PR",
                                "headBranch", "feature/pr-test",
                                "baseBranch", "main"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        int prNumber = objectMapper.readTree(prResult.getResponse().getContentAsString())
                .get("number").asInt();

        // Attempt to merge without any reviews
        mockMvc.perform(post("/api/repos/{owner}/{repo}/pulls/{number}/merge",
                        ownerUsername, repoName, prNumber)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isUnprocessableEntity());
    }
}
