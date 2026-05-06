package com.dvcs.repository;

import com.dvcs.AbstractIntegrationTest;
import com.dvcs.auth.dto.LoginRequest;
import com.dvcs.auth.dto.RegisterRequest;
import com.dvcs.repository.dto.CreateRepoRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link com.dvcs.repository.controller.RepoController}.
 *
 * <p>Tests repository lifecycle: create, get, fork, delete, stats.
 * Uses Testcontainers for PostgreSQL and Redis.
 */
class RepoControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String accessToken;
    private String username;

    @BeforeEach
    void setUp() throws Exception {
        // Register and login a unique user for each test
        username = "repouser_" + System.currentTimeMillis();
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
    }

    @Test
    @DisplayName("POST /api/repos — creates a repository and returns 201")
    void createRepo_success_returns201() throws Exception {
        CreateRepoRequest request = new CreateRepoRequest(
                "my-repo", "A test repository", false, "main");

        mockMvc.perform(post("/api/repos")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("my-repo")))
                .andExpect(jsonPath("$.ownerUsername", is(username)))
                .andExpect(jsonPath("$.id", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/repos — duplicate name returns 409")
    void createRepo_duplicateName_returns409() throws Exception {
        CreateRepoRequest request = new CreateRepoRequest(
                "duplicate-repo", "First", false, "main");

        // Create first time
        mockMvc.perform(post("/api/repos")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Create second time — should conflict
        mockMvc.perform(post("/api/repos")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /api/repos/{owner}/{repo} — returns repository metadata")
    void getRepo_publicRepo_returns200() throws Exception {
        // Create a public repo
        CreateRepoRequest request = new CreateRepoRequest(
                "public-repo", "Public repository", false, "main");

        mockMvc.perform(post("/api/repos")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Get it without auth (public)
        mockMvc.perform(get("/api/repos/{owner}/{repo}", username, "public-repo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("public-repo")))
                .andExpect(jsonPath("$.ownerUsername", is(username)));
    }

    @Test
    @DisplayName("GET /api/repos/{owner}/{repo} — private repo returns 404 to anonymous")
    void getRepo_privateRepo_anonymousGets404() throws Exception {
        // Create a private repo
        CreateRepoRequest request = new CreateRepoRequest(
                "private-repo", "Private repository", true, "main");

        mockMvc.perform(post("/api/repos")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Anonymous access should return 404 (not 403, per Req 3.4)
        mockMvc.perform(get("/api/repos/{owner}/{repo}", username, "private-repo"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/repos/{owner}/{repo} — owner can delete, returns 204")
    void deleteRepo_owner_returns204() throws Exception {
        // Create a repo
        CreateRepoRequest request = new CreateRepoRequest(
                "delete-me", "To be deleted", false, "main");

        mockMvc.perform(post("/api/repos")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Delete it
        mockMvc.perform(delete("/api/repos/{owner}/{repo}", username, "delete-me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        // Verify it's gone
        mockMvc.perform(get("/api/repos/{owner}/{repo}", username, "delete-me"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/repos/{owner}/{repo} — non-owner gets 403")
    void deleteRepo_nonOwner_returns403() throws Exception {
        // Create a repo
        CreateRepoRequest request = new CreateRepoRequest(
                "protected-repo", "Protected", false, "main");

        mockMvc.perform(post("/api/repos")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Register another user
        String otherUsername = "other_" + System.currentTimeMillis();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(otherUsername, otherUsername + "@example.com", "password123"))))
                .andExpect(status().isCreated());

        MvcResult otherLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(otherUsername, "password123"))))
                .andExpect(status().isOk())
                .andReturn();

        String otherToken = objectMapper.readTree(
                otherLogin.getResponse().getContentAsString()).get("accessToken").asText();

        // Other user tries to delete — should get 403
        mockMvc.perform(delete("/api/repos/{owner}/{repo}", username, "protected-repo")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/repos/{owner}/{repo}/stats — returns stats")
    void getStats_returns200() throws Exception {
        // Create a repo
        CreateRepoRequest request = new CreateRepoRequest(
                "stats-repo", "Stats test", false, "main");

        mockMvc.perform(post("/api/repos")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Get stats
        mockMvc.perform(get("/api/repos/{owner}/{repo}/stats", username, "stats-repo")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commitCount", notNullValue()))
                .andExpect(jsonPath("$.totalObjectSizeBytes", notNullValue()))
                .andExpect(jsonPath("$.contributorCount", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/repos/{owner}/{repo}/fork — forks a public repository")
    void forkRepo_publicRepo_returns201() throws Exception {
        // Create a public repo
        CreateRepoRequest request = new CreateRepoRequest(
                "fork-source", "Source repo", false, "main");

        mockMvc.perform(post("/api/repos")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Register another user to fork
        String forkerUsername = "forker_" + System.currentTimeMillis();
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(forkerUsername, forkerUsername + "@example.com", "password123"))))
                .andExpect(status().isCreated());

        MvcResult forkerLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(forkerUsername, "password123"))))
                .andExpect(status().isOk())
                .andReturn();

        String forkerToken = objectMapper.readTree(
                forkerLogin.getResponse().getContentAsString()).get("accessToken").asText();

        // Fork the repo
        mockMvc.perform(post("/api/repos/{owner}/{repo}/fork", username, "fork-source")
                        .header("Authorization", "Bearer " + forkerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerUsername", is(forkerUsername)))
                .andExpect(jsonPath("$.name", is("fork-source")))
                .andExpect(jsonPath("$.forkOf", notNullValue()));
    }
}
