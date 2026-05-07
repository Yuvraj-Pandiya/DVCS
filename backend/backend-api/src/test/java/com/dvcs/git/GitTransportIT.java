package com.dvcs.git;

import com.dvcs.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the Git smart HTTP transport endpoints.
 *
 * <p>Covers:
 * <ul>
 *   <li>GET /api/git/{owner}/{repo}/info/refs?service=git-upload-pack (200)</li>
 *   <li>GET /api/git/{owner}/{repo}/info/refs?service=git-receive-pack (200)</li>
 *   <li>GET /api/git/{owner}/{repo}/info/refs with unknown service (400)</li>
 * </ul>
 */
@DisplayName("GitTransport Integration Tests")
class GitTransportIT extends AbstractIntegrationTest {

    private String ownerUsername;
    private String ownerToken;
    private String repoName;

    @BeforeEach
    void setUpRepoAndUser() throws Exception {
        ownerUsername = uniqueUsername("gitowner");
        ownerToken = registerAndLogin(ownerUsername, "GitPass123!");
        repoName = "git-transport-repo";

        // Create a public repo
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
    // GET /info/refs?service=git-upload-pack
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /info/refs?service=git-upload-pack returns 200 with correct content-type")
    void infoRefs_uploadPack_returns200() throws Exception {
        var result = mockMvc.perform(get("/api/git/{owner}/{repo}/info/refs", ownerUsername, repoName)
                        .param("service", "git-upload-pack")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        org.hamcrest.Matchers.containsString("application/x-git-upload-pack-advertisement")))
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body).isNotEmpty();

        // Verify pkt-line service announcement prefix
        String bodyStr = new String(body, java.nio.charset.StandardCharsets.US_ASCII);
        assertThat(bodyStr).contains("# service=git-upload-pack");
    }

    // -------------------------------------------------------------------------
    // GET /info/refs?service=git-receive-pack
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /info/refs?service=git-receive-pack returns 200 with correct content-type")
    void infoRefs_receivePack_returns200() throws Exception {
        var result = mockMvc.perform(get("/api/git/{owner}/{repo}/info/refs", ownerUsername, repoName)
                        .param("service", "git-receive-pack")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        org.hamcrest.Matchers.containsString("application/x-git-receive-pack-advertisement")))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body).isNotEmpty();

        String bodyStr = new String(body, java.nio.charset.StandardCharsets.US_ASCII);
        assertThat(bodyStr).contains("# service=git-receive-pack");
    }

    // -------------------------------------------------------------------------
    // GET /info/refs with unknown service
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /info/refs with unknown service returns 400")
    void infoRefs_unknownService_returns400() throws Exception {
        mockMvc.perform(get("/api/git/{owner}/{repo}/info/refs", ownerUsername, repoName)
                        .param("service", "git-unknown-service")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // Access control
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /info/refs on non-existent repo returns 404")
    void infoRefs_nonExistentRepo_returns404() throws Exception {
        mockMvc.perform(get("/api/git/{owner}/{repo}/info/refs", ownerUsername, "nonexistent")
                        .param("service", "git-upload-pack")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }
}
