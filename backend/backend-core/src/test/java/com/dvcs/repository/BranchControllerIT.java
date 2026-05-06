package com.dvcs.repository;

import com.dvcs.AbstractIntegrationTest;
import com.dvcs.auth.dto.LoginRequest;
import com.dvcs.auth.dto.RegisterRequest;
import com.dvcs.repository.dto.CreateBranchRequest;
import com.dvcs.repository.dto.CreateRepoRequest;
import com.dvcs.repository.dto.ToggleProtectionRequest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link com.dvcs.repository.controller.BranchController}.
 *
 * <p>Tests: list branches, create branch, protect branch, reject delete of protected branch.
 */
class BranchControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String accessToken;
    private String username;
    private String repoName;

    private static final String DUMMY_SHA =
            "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";

    @BeforeEach
    void setUp() throws Exception {
        username = "branchuser_" + System.currentTimeMillis();
        repoName = "branch-test-repo";
        String email = username + "@example.com";

        // Register user
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(username, email, "password123"))))
                .andExpect(status().isCreated());

        // Login
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(username, "password123"))))
                .andExpect(status().isOk())
                .andReturn();

        accessToken = objectMapper.readTree(
                loginResult.getResponse().getContentAsString()).get("accessToken").asText();

        // Create a repository
        mockMvc.perform(post("/api/repos")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateRepoRequest(repoName, "Branch test repo", false, "main"))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("GET /branches — lists branches including default branch")
    void listBranches_returnsDefaultBranch() throws Exception {
        mockMvc.perform(get("/api/repos/{owner}/{repo}/branches", username, repoName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("main")));
    }

    @Test
    @DisplayName("POST /branches — creates a new branch")
    void createBranch_success_returns201() throws Exception {
        CreateBranchRequest request = new CreateBranchRequest("feature/new-feature", DUMMY_SHA);

        mockMvc.perform(post("/api/repos/{owner}/{repo}/branches", username, repoName)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("feature/new-feature")))
                .andExpect(jsonPath("$.headSha", is(DUMMY_SHA)))
                .andExpect(jsonPath("$.protected", is(false)));
    }

    @Test
    @DisplayName("POST /branches — duplicate branch name returns 409")
    void createBranch_duplicate_returns409() throws Exception {
        CreateBranchRequest request = new CreateBranchRequest("duplicate-branch", DUMMY_SHA);

        // Create first time
        mockMvc.perform(post("/api/repos/{owner}/{repo}/branches", username, repoName)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Create second time — should conflict
        mockMvc.perform(post("/api/repos/{owner}/{repo}/branches", username, repoName)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PATCH /branches/{name}/protect — protects a branch")
    void protectBranch_success_returns200() throws Exception {
        // Create a branch first
        CreateBranchRequest createRequest = new CreateBranchRequest("to-protect", DUMMY_SHA);
        mockMvc.perform(post("/api/repos/{owner}/{repo}/branches", username, repoName)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated());

        // Protect it
        ToggleProtectionRequest protectRequest = new ToggleProtectionRequest(true);
        mockMvc.perform(patch("/api/repos/{owner}/{repo}/branches/{name}/protect",
                        username, repoName, "to-protect")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(protectRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.protected", is(true)));
    }

    @Test
    @DisplayName("DELETE /branches/{name} — rejects deletion of protected branch with 403")
    void deleteBranch_protected_returns403() throws Exception {
        // Create a branch
        CreateBranchRequest createRequest = new CreateBranchRequest("protected-branch", DUMMY_SHA);
        mockMvc.perform(post("/api/repos/{owner}/{repo}/branches", username, repoName)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated());

        // Protect it
        ToggleProtectionRequest protectRequest = new ToggleProtectionRequest(true);
        mockMvc.perform(patch("/api/repos/{owner}/{repo}/branches/{name}/protect",
                        username, repoName, "protected-branch")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(protectRequest)))
                .andExpect(status().isOk());

        // Try to delete — should be rejected
        mockMvc.perform(delete("/api/repos/{owner}/{repo}/branches/{name}",
                        username, repoName, "protected-branch")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /branches/{name} — deletes an unprotected branch")
    void deleteBranch_unprotected_returns204() throws Exception {
        // Create a branch
        CreateBranchRequest createRequest = new CreateBranchRequest("deletable-branch", DUMMY_SHA);
        mockMvc.perform(post("/api/repos/{owner}/{repo}/branches", username, repoName)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated());

        // Delete it
        mockMvc.perform(delete("/api/repos/{owner}/{repo}/branches/{name}",
                        username, repoName, "deletable-branch")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        // Verify it's gone from the list
        mockMvc.perform(get("/api/repos/{owner}/{repo}/branches", username, repoName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'deletable-branch')]", hasSize(0)));
    }
}
