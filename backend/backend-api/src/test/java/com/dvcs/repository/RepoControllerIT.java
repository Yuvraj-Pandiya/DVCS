package com.dvcs.repository;

import com.dvcs.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code /api/repos} endpoints.
 *
 * <p>Covers: create repo (201), get repo (200), fork repo (201), delete repo (204).
 */
@DisplayName("RepoController Integration Tests")
class RepoControllerIT extends AbstractIntegrationTest {

    private String ownerUsername;
    private String ownerToken;

    @BeforeEach
    void setUpUser() throws Exception {
        ownerUsername = uniqueUsername("repoowner");
        ownerToken = registerAndLogin(ownerUsername, "RepoPass123!");
    }

    // -------------------------------------------------------------------------
    // POST /api/repos
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/repos returns 201 with repo metadata")
    void createRepo_validRequest_returns201() throws Exception {
        String repoName = "my-test-repo";

        mockMvc.perform(post("/api/repos")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", repoName,
                                "description", "A test repository",
                                "isPrivate", false
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(repoName))
                .andExpect(jsonPath("$.ownerUsername").value(ownerUsername))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    @DisplayName("POST /api/repos returns 409 for duplicate repo name")
    void createRepo_duplicateName_returns409() throws Exception {
        String repoName = "duplicate-repo";
        String body = objectMapper.writeValueAsString(Map.of(
                "name", repoName,
                "isPrivate", false
        ));

        mockMvc.perform(post("/api/repos")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/repos")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    // -------------------------------------------------------------------------
    // GET /api/repos/{owner}/{repo}
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/repos/{owner}/{repo} returns 200 with repo metadata")
    void getRepo_existingRepo_returns200() throws Exception {
        String repoName = "get-test-repo";

        // Create repo
        mockMvc.perform(post("/api/repos")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", repoName,
                                "isPrivate", false
                        ))))
                .andExpect(status().isCreated());

        // Get repo
        mockMvc.perform(get("/api/repos/{owner}/{repo}", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(repoName))
                .andExpect(jsonPath("$.ownerUsername").value(ownerUsername));
    }

    @Test
    @DisplayName("GET /api/repos/{owner}/{repo} returns 404 for non-existent repo")
    void getRepo_nonExistent_returns404() throws Exception {
        mockMvc.perform(get("/api/repos/{owner}/{repo}", ownerUsername, "nonexistent-repo")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // POST /api/repos/{owner}/{repo}/fork
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST /api/repos/{owner}/{repo}/fork returns 201 with forked repo")
    void forkRepo_publicRepo_returns201() throws Exception {
        String repoName = "fork-source-repo";

        // Create source repo
        mockMvc.perform(post("/api/repos")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", repoName,
                                "isPrivate", false
                        ))))
                .andExpect(status().isCreated());

        // Create a second user to fork
        String forkerUsername = uniqueUsername("forker");
        String forkerToken = registerAndLogin(forkerUsername, "ForkerPass123!");

        // Fork the repo
        mockMvc.perform(post("/api/repos/{owner}/{repo}/fork", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + forkerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerUsername").value(forkerUsername))
                .andExpect(jsonPath("$.name").value(repoName))
                .andExpect(jsonPath("$.forkOf").isNumber());
    }

    // -------------------------------------------------------------------------
    // DELETE /api/repos/{owner}/{repo}
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("DELETE /api/repos/{owner}/{repo} returns 204 for owner")
    void deleteRepo_owner_returns204() throws Exception {
        String repoName = "delete-test-repo";

        // Create repo
        mockMvc.perform(post("/api/repos")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", repoName,
                                "isPrivate", false
                        ))))
                .andExpect(status().isCreated());

        // Delete repo
        mockMvc.perform(delete("/api/repos/{owner}/{repo}", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        // Verify it's gone
        mockMvc.perform(get("/api/repos/{owner}/{repo}", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/repos/{owner}/{repo} returns 403 for non-owner")
    void deleteRepo_nonOwner_returns403() throws Exception {
        String repoName = "protected-repo";

        // Create repo
        mockMvc.perform(post("/api/repos")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", repoName,
                                "isPrivate", false
                        ))))
                .andExpect(status().isCreated());

        // Another user tries to delete
        String otherUsername = uniqueUsername("other");
        String otherToken = registerAndLogin(otherUsername, "OtherPass123!");

        mockMvc.perform(delete("/api/repos/{owner}/{repo}", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }
}
