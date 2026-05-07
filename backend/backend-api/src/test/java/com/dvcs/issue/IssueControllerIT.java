package com.dvcs.issue;

import com.dvcs.AbstractIntegrationTest;
import com.dvcs.auth.domain.User;
import com.dvcs.auth.repository.UserRepository;
import com.dvcs.issue.service.LabelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code /api/repos/{owner}/{repo}/issues} endpoints.
 *
 * <p>Covers: create issue (201), add comment (201), apply label (200), close issue (200).
 */
@DisplayName("IssueController Integration Tests")
class IssueControllerIT extends AbstractIntegrationTest {

    @Autowired
    private LabelService labelService;

    @Autowired
    private UserRepository userRepository;

    private String ownerUsername;
    private String ownerToken;
    private String repoName;
    private Long repoId;
    private Long ownerId;

    @BeforeEach
    void setUpRepoAndUser() throws Exception {
        ownerUsername = uniqueUsername("issueowner");
        ownerToken = registerAndLogin(ownerUsername, "IssuePass123!");

        ownerId = userRepository.findByUsername(ownerUsername)
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + ownerUsername));

        repoName = "issue-test-repo";

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
    // POST /api/repos/{owner}/{repo}/issues
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST issues returns 201 with created issue")
    void createIssue_validRequest_returns201() throws Exception {
        mockMvc.perform(post("/api/repos/{owner}/{repo}/issues", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Bug: something is broken",
                                "body", "Steps to reproduce..."
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Bug: something is broken"))
                .andExpect(jsonPath("$.status").value("open"))
                .andExpect(jsonPath("$.number").isNumber());
    }

    @Test
    @DisplayName("POST issues returns 401 for unauthenticated request")
    void createIssue_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/repos/{owner}/{repo}/issues", ownerUsername, repoName)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Unauthorized issue"
                        ))))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // POST /api/repos/{owner}/{repo}/issues/{number}/comments
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST issues/{number}/comments returns 201 with created comment")
    void addComment_validRequest_returns201() throws Exception {
        // Create issue
        MvcResult issueResult = mockMvc.perform(post("/api/repos/{owner}/{repo}/issues", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Issue for comment test"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        int issueNumber = objectMapper.readTree(issueResult.getResponse().getContentAsString())
                .get("number").asInt();

        // Add comment
        mockMvc.perform(post("/api/repos/{owner}/{repo}/issues/{number}/comments",
                        ownerUsername, repoName, issueNumber)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "body", "This is a comment on the issue"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("This is a comment on the issue"));
    }

    // -------------------------------------------------------------------------
    // POST /api/repos/{owner}/{repo}/issues/{number}/labels
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST issues/{number}/labels returns 200 after applying label")
    void applyLabel_validLabel_returns200() throws Exception {
        // Create issue
        MvcResult issueResult = mockMvc.perform(post("/api/repos/{owner}/{repo}/issues", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Issue for label test"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        int issueNumber = objectMapper.readTree(issueResult.getResponse().getContentAsString())
                .get("number").asInt();

        // Create a label via service (owner is already a collaborator with OWNER role)
        var labelResponse = labelService.createLabel(repoId, "bug", "#ff0000", ownerId);

        // Apply label to issue
        mockMvc.perform(post("/api/repos/{owner}/{repo}/issues/{number}/labels",
                        ownerUsername, repoName, issueNumber)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "labelId", labelResponse.getId()
                        ))))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // POST /api/repos/{owner}/{repo}/issues/{number}/close
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("POST issues/{number}/close returns 200 with closed issue")
    void closeIssue_openIssue_returns200() throws Exception {
        // Create issue
        MvcResult issueResult = mockMvc.perform(post("/api/repos/{owner}/{repo}/issues", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Issue to close"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        int issueNumber = objectMapper.readTree(issueResult.getResponse().getContentAsString())
                .get("number").asInt();

        // Close issue
        mockMvc.perform(post("/api/repos/{owner}/{repo}/issues/{number}/close",
                        ownerUsername, repoName, issueNumber)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("closed"));
    }

    // -------------------------------------------------------------------------
    // GET /api/repos/{owner}/{repo}/issues
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET issues returns 200 with paginated list")
    void listIssues_returns200() throws Exception {
        // Create a couple of issues
        for (int i = 1; i <= 2; i++) {
            mockMvc.perform(post("/api/repos/{owner}/{repo}/issues", ownerUsername, repoName)
                            .header("Authorization", "Bearer " + ownerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "title", "Issue " + i
                            ))))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/repos/{owner}/{repo}/issues", ownerUsername, repoName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
