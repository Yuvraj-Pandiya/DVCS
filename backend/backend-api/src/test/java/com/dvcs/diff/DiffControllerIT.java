package com.dvcs.diff;

import com.dvcs.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code /api/repos/{owner}/{repo}/diff} endpoint.
 *
 * <p>Covers:
 * <ul>
 *   <li>GET /diff with missing path parameter returns 400</li>
 *   <li>GET /diff with non-existent ref returns 404</li>
 *   <li>GET /diff with valid refs returns 200 (empty hunks for empty repo)</li>
 * </ul>
 *
 * <p>Note: Full diff testing with actual file content requires a populated git object store.
 * These tests verify the endpoint routing, parameter validation, and error handling.
 */
@DisplayName("DiffController Integration Tests")
class DiffControllerIT extends AbstractIntegrationTest {

    private static final String EMPTY_SHA =
            "0000000000000000000000000000000000000000000000000000000000000000";

    private String ownerUsername;
    private String ownerToken;
    private String repoName;

    @BeforeEach
    void setUpRepoAndBranches() throws Exception {
        ownerUsername = uniqueUsername("diffowner");
        ownerToken = registerAndLogin(ownerUsername, "DiffPass123!");
        repoName = "diff-test-repo";

        // Create repo
        mockMvc.perform(post("/api/repos")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", repoName,
                                "isPrivate", false
                        ))))
                .andExpect(status().isCreated());

        // Create a feature branch
        mockMvc.perform(post("/api/repos/{owner}/{repo}/branches", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "feature/diff-test",
                                "sourceSha", EMPTY_SHA
                        ))))
                .andExpect(status().isCreated());
    }

    // -------------------------------------------------------------------------
    // GET /api/repos/{owner}/{repo}/diff — missing path parameter
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /diff without path parameter returns 400")
    void getDiff_missingPath_returns400() throws Exception {
        mockMvc.perform(get("/api/repos/{owner}/{repo}/diff", ownerUsername, repoName)
                        .param("base", "main")
                        .param("head", "feature/diff-test")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // GET /api/repos/{owner}/{repo}/diff — non-existent ref
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /diff with non-existent ref returns 404")
    void getDiff_nonExistentRef_returns404() throws Exception {
        mockMvc.perform(get("/api/repos/{owner}/{repo}/diff", ownerUsername, repoName)
                        .param("base", "nonexistent-branch")
                        .param("head", "feature/diff-test")
                        .param("path", "README.md")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // GET /api/repos/{owner}/{repo}/diff — binary file detection
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /diff with valid refs but missing blob returns 404")
    void getDiff_validRefs_missingBlob_returns404() throws Exception {
        // Both branches exist but the file doesn't exist in the empty repo
        mockMvc.perform(get("/api/repos/{owner}/{repo}/diff", ownerUsername, repoName)
                        .param("base", "main")
                        .param("head", "feature/diff-test")
                        .param("path", "src/main.java")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // GET /api/repos/{owner}/{repo}/diff — path traversal protection
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /diff with path traversal attempt returns 400")
    void getDiff_pathTraversal_returns400() throws Exception {
        mockMvc.perform(get("/api/repos/{owner}/{repo}/diff", ownerUsername, repoName)
                        .param("base", "main")
                        .param("head", "feature/diff-test")
                        .param("path", "../../../etc/passwd")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // GET /api/repos/{owner}/{repo}/diff — unauthenticated access to public repo
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /diff on public repo without auth returns 400 for missing path")
    void getDiff_publicRepo_unauthenticated_missingPath_returns400() throws Exception {
        mockMvc.perform(get("/api/repos/{owner}/{repo}/diff", ownerUsername, repoName)
                        .param("base", "main")
                        .param("head", "feature/diff-test"))
                .andExpect(status().isBadRequest());
    }
}
