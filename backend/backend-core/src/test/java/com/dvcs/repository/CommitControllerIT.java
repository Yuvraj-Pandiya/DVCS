package com.dvcs.repository;

import com.dvcs.AbstractIntegrationTest;
import com.dvcs.auth.dto.LoginRequest;
import com.dvcs.auth.dto.RegisterRequest;
import com.dvcs.repository.domain.CommitMeta;
import com.dvcs.repository.dto.CreateBranchRequest;
import com.dvcs.repository.dto.CreateRepoRequest;
import com.dvcs.repository.repository.BranchRepository;
import com.dvcs.repository.repository.CommitMetaRepository;
import com.dvcs.repository.repository.RepoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link com.dvcs.repository.controller.CommitController}.
 *
 * <p>Tests: paginated commit log, single commit detail, compare two branches.
 */
class CommitControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CommitMetaRepository commitMetaRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private RepoRepository repoRepository;

    @Autowired
    private com.dvcs.auth.repository.UserRepository userRepository;

    private String accessToken;
    private String username;
    private String repoName;
    private Long repoId;

    private static final String SHA_1 =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String SHA_2 =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String SHA_3 =
            "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";

    @BeforeEach
    void setUp() throws Exception {
        username = "commituser_" + System.currentTimeMillis();
        repoName = "commit-test-repo";
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
        MvcResult createResult = mockMvc.perform(post("/api/repos")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateRepoRequest(repoName, "Commit test repo", false, "main"))))
                .andExpect(status().isCreated())
                .andReturn();

        repoId = objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asLong();

        // Seed some commits directly via repository
        OffsetDateTime now = OffsetDateTime.now();
        commitMetaRepository.save(CommitMeta.builder()
                .repoId(repoId)
                .sha(SHA_1)
                .message("Initial commit")
                .authoredAt(now.minusHours(3))
                .committedAt(now.minusHours(3))
                .build());

        commitMetaRepository.save(CommitMeta.builder()
                .repoId(repoId)
                .sha(SHA_2)
                .message("Second commit")
                .authoredAt(now.minusHours(2))
                .committedAt(now.minusHours(2))
                .build());

        commitMetaRepository.save(CommitMeta.builder()
                .repoId(repoId)
                .sha(SHA_3)
                .message("Third commit")
                .authoredAt(now.minusHours(1))
                .committedAt(now.minusHours(1))
                .build());

        // Update main branch to point to SHA_3
        branchRepository.findByRepoIdAndName(repoId, "main").ifPresent(branch -> {
            branch.setHeadSha(SHA_3);
            branchRepository.save(branch);
        });
    }

    @Test
    @DisplayName("GET /commits/{branch} — returns paginated commit log")
    void getCommitLog_returnsCommits() throws Exception {
        mockMvc.perform(get("/api/repos/{owner}/{repo}/commits/{branch}",
                        username, repoName, "main")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commits", notNullValue()))
                .andExpect(jsonPath("$.commits", hasSize(3)))
                .andExpect(jsonPath("$.totalElements", is(3)))
                .andExpect(jsonPath("$.page", is(0)));
    }

    @Test
    @DisplayName("GET /commits/{branch} — pagination works correctly")
    void getCommitLog_pagination_works() throws Exception {
        // Page 0, size 2 — should return 2 commits
        mockMvc.perform(get("/api/repos/{owner}/{repo}/commits/{branch}",
                        username, repoName, "main")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commits", hasSize(2)))
                .andExpect(jsonPath("$.totalPages", is(2)));

        // Page 1, size 2 — should return 1 commit
        mockMvc.perform(get("/api/repos/{owner}/{repo}/commits/{branch}",
                        username, repoName, "main")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commits", hasSize(1)));
    }

    @Test
    @DisplayName("GET /commits/{branch} — non-existent branch returns 404")
    void getCommitLog_nonExistentBranch_returns404() throws Exception {
        mockMvc.perform(get("/api/repos/{owner}/{repo}/commits/{branch}",
                        username, repoName, "nonexistent"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /commits/sha/{sha} — returns single commit detail")
    void getCommit_bySha_returns200() throws Exception {
        mockMvc.perform(get("/api/repos/{owner}/{repo}/commits/sha/{sha}",
                        username, repoName, SHA_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commit.sha", is(SHA_1)))
                .andExpect(jsonPath("$.commit.message", is("Initial commit")));
    }

    @Test
    @DisplayName("GET /commits/sha/{sha} — non-existent SHA returns 404")
    void getCommit_nonExistentSha_returns404() throws Exception {
        String unknownSha = "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd";
        mockMvc.perform(get("/api/repos/{owner}/{repo}/commits/sha/{sha}",
                        username, repoName, unknownSha))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /compare/{base}...{head} — compares two branches")
    void compare_twoBranches_returnsCommits() throws Exception {
        // Create a feature branch pointing to SHA_2
        mockMvc.perform(post("/api/repos/{owner}/{repo}/branches", username, repoName)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateBranchRequest("feature", SHA_2))))
                .andExpect(status().isCreated());

        // Compare feature (SHA_2) with main (SHA_3)
        // Should return commits between SHA_2 and SHA_3
        mockMvc.perform(get("/api/repos/{owner}/{repo}/compare/{base}...{head}",
                        username, repoName, "feature", "main"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.base", is("feature")))
                .andExpect(jsonPath("$.head", is("main")))
                .andExpect(jsonPath("$.commits", notNullValue()));
    }
}
