package com.dvcs.repository;

import com.dvcs.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code /api/repos/{owner}/{repo}/branches} endpoints.
 *
 * <p>Covers: create branch (201), protect branch (200), attempt delete protected branch (403).
 */
@DisplayName("BranchController Integration Tests")
class BranchControllerIT extends AbstractIntegrationTest {

    private String ownerUsername;
    private String ownerToken;
    private String repoName;

    /** The SHA of the initial empty branch — used as sourceSha for new branches. */
    private static final String EMPTY_SHA =
            "0000000000000000000000000000000000000000000000000000000000000000";

    @BeforeEach
    void setUpRepoAndUser() throws Exception {
        ownerUsername = uniqueUsername("branchowner");
        ownerToken = registerAndLogin(ownerUsername, "BranchPass123!");
        repoName = "branch-test-repo";

        mockMvc.perform(post("/api/repos")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", repoName,
                                "isPrivate", false
                        ))))
                .andExpect(status().isCreated());
    }

    // -------------------------------------------------------------------------
    // POST /api/repos/{owner}/{repo}/branches
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST branches returns 201 with created branch")
    void createBranch_validRequest_returns201() throws Exception {
        mockMvc.perform(post("/api/repos/{owner}/{repo}/branches", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "feature/new-feature",
                                "sourceSha", EMPTY_SHA
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("feature/new-feature"))
                .andExpect(jsonPath("$.repoId").isNumber());
    }

    @Test
    @DisplayName("POST branches returns 409 for duplicate branch name")
    void createBranch_duplicateName_returns409() throws Exception {
        String branchName = "duplicate-branch";
        String body = objectMapper.writeValueAsString(Map.of(
                "name", branchName,
                "sourceSha", EMPTY_SHA
        ));

        mockMvc.perform(post("/api/repos/{owner}/{repo}/branches", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/repos/{owner}/{repo}/branches", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    // -------------------------------------------------------------------------
    // PATCH /api/repos/{owner}/{repo}/branches/{name}/protect
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("PATCH branches/{name}/protect returns 200 with protected=true")
    void protectBranch_owner_returns200() throws Exception {
        String branchName = "protect-me";

        // Create branch
        mockMvc.perform(post("/api/repos/{owner}/{repo}/branches", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", branchName,
                                "sourceSha", EMPTY_SHA
                        ))))
                .andExpect(status().isCreated());

        // Protect branch
        mockMvc.perform(patch("/api/repos/{owner}/{repo}/branches/{name}/protect",
                        ownerUsername, repoName, branchName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("protect", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isProtected").value(true));
    }

    // -------------------------------------------------------------------------
    // DELETE /api/repos/{owner}/{repo}/branches/{name}
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DELETE branches/{name} returns 204 for unprotected branch")
    void deleteBranch_unprotected_returns204() throws Exception {
        String branchName = "deletable-branch";

        mockMvc.perform(post("/api/repos/{owner}/{repo}/branches", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", branchName,
                                "sourceSha", EMPTY_SHA
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/repos/{owner}/{repo}/branches/{name}",
                        ownerUsername, repoName, branchName)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE branches/{name} returns 403 for protected branch")
    void deleteBranch_protected_returns403() throws Exception {
        String branchName = "protected-branch";

        // Create branch
        mockMvc.perform(post("/api/repos/{owner}/{repo}/branches", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", branchName,
                                "sourceSha", EMPTY_SHA
                        ))))
                .andExpect(status().isCreated());

        // Protect it
        mockMvc.perform(patch("/api/repos/{owner}/{repo}/branches/{name}/protect",
                        ownerUsername, repoName, branchName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("protect", true))))
                .andExpect(status().isOk());

        // Attempt to delete — should fail with 403
        mockMvc.perform(delete("/api/repos/{owner}/{repo}/branches/{name}",
                        ownerUsername, repoName, branchName)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // GET /api/repos/{owner}/{repo}/branches
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET branches returns 200 with list of branches")
    void listBranches_returns200() throws Exception {
        mockMvc.perform(get("/api/repos/{owner}/{repo}/branches", ownerUsername, repoName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
