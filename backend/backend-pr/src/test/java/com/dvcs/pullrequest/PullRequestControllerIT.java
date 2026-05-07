package com.dvcs.pullrequest;

import com.dvcs.AbstractPrIntegrationTest;
import com.dvcs.auth.dto.LoginRequest;
import com.dvcs.auth.dto.RegisterRequest;
import com.dvcs.pullrequest.dto.AddCommentRequest;
import com.dvcs.pullrequest.dto.CreatePrRequest;
import com.dvcs.pullrequest.dto.SubmitReviewRequest;
import com.dvcs.repository.domain.Branch;
import com.dvcs.repository.domain.Repository;
import com.dvcs.repository.repository.BranchRepository;
import com.dvcs.repository.repository.CollaboratorRepository;
import com.dvcs.repository.repository.RepoRepository;
import com.dvcs.auth.repository.UserRepository;
import com.dvcs.auth.domain.User;
import com.dvcs.repository.domain.Collaborator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link com.dvcs.pullrequest.controller.PullRequestController}.
 *
 * <p>Tests the full PR lifecycle: open, review, merge, and error cases.
 * Uses Testcontainers for PostgreSQL and Redis.
 *
 * <p>Requirement 10: Pull Request Lifecycle.
 */
class PullRequestControllerIT extends AbstractPrIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RepoRepository repoRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private CollaboratorRepository collaboratorRepository;

    private String ownerToken;
    private String ownerUsername;
    private String reviewerToken;
    private String reviewerUsername;
    private String repoName;
    private Long repoId;
    private Long ownerId;
    private Long reviewerId;

    @BeforeEach
    void setUp() throws Exception {
        // Create unique usernames for each test
        long ts = System.currentTimeMillis();
        ownerUsername = "prowner_" + ts;
        reviewerUsername = "prreviewer_" + ts;
        repoName = "pr-test-repo-" + ts;

        // Register owner
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(ownerUsername, ownerUsername + "@example.com", "password123"))))
                .andExpect(status().isCreated());

        // Login owner
        MvcResult ownerLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(ownerUsername, "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        ownerToken = objectMapper.readTree(ownerLogin.getResponse().getContentAsString())
                .get("accessToken").asText();

        // Register reviewer
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(reviewerUsername, reviewerUsername + "@example.com", "password123"))))
                .andExpect(status().isCreated());

        // Login reviewer
        MvcResult reviewerLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(reviewerUsername, "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        reviewerToken = objectMapper.readTree(reviewerLogin.getResponse().getContentAsString())
                .get("accessToken").asText();

        // Look up user IDs
        ownerId = userRepository.findByUsername(ownerUsername).orElseThrow().getId();
        reviewerId = userRepository.findByUsername(reviewerUsername).orElseThrow().getId();

        // Create repository directly in DB (bypassing git transport for simplicity)
        Repository repo = Repository.builder()
                .ownerId(ownerId)
                .name(repoName)
                .description("PR test repository")
                .isPrivate(false)
                .defaultBranch("main")
                .createdAt(OffsetDateTime.now())
                .build();
        repo = repoRepository.save(repo);
        repoId = repo.getId();

        // Add owner as OWNER collaborator
        Collaborator ownerCollab = new Collaborator();
        ownerCollab.setRepoId(repoId);
        ownerCollab.setUserId(ownerId);
        ownerCollab.setRole("OWNER");
        collaboratorRepository.save(ownerCollab);

        // Add reviewer as WRITE collaborator (so they can submit reviews)
        Collaborator reviewerCollab = new Collaborator();
        reviewerCollab.setRepoId(repoId);
        reviewerCollab.setUserId(reviewerId);
        reviewerCollab.setRole("WRITE");
        collaboratorRepository.save(reviewerCollab);

        // Create main branch with a fake SHA
        Branch mainBranch = Branch.builder()
                .repoId(repoId)
                .name("main")
                .headSha("a".repeat(64))
                .isProtected(false)
                .createdAt(OffsetDateTime.now())
                .build();
        branchRepository.save(mainBranch);

        // Create feature branch with a different fake SHA
        Branch featureBranch = Branch.builder()
                .repoId(repoId)
                .name("feature/my-feature")
                .headSha("b".repeat(64))
                .isProtected(false)
                .createdAt(OffsetDateTime.now())
                .build();
        branchRepository.save(featureBranch);
    }

    // =========================================================================
    // Test: Open PR
    // =========================================================================

    @Test
    @DisplayName("POST /pulls — opens a PR and returns 201")
    void openPr_success_returns201() throws Exception {
        CreatePrRequest request = new CreatePrRequest(
                "Add new feature", "This PR adds a new feature.", "feature/my-feature", "main");

        MvcResult result = mockMvc.perform(post("/api/repos/{owner}/{repo}/pulls", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("Add new feature")))
                .andExpect(jsonPath("$.status", is("open")))
                .andExpect(jsonPath("$.number", is(1)))
                .andExpect(jsonPath("$.headBranch", is("feature/my-feature")))
                .andExpect(jsonPath("$.baseBranch", is("main")))
                .andReturn();

        // Verify the PR was created
        JsonNode pr = objectMapper.readTree(result.getResponse().getContentAsString());
        long prId = pr.get("id").asLong();
        assert prId > 0;
    }

    @Test
    @DisplayName("POST /pulls — same head and base branch returns 422")
    void openPr_sameHeadAndBase_returns422() throws Exception {
        CreatePrRequest request = new CreatePrRequest(
                "Invalid PR", "Same branches.", "main", "main");

        mockMvc.perform(post("/api/repos/{owner}/{repo}/pulls", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", is("INVALID_REQUEST")));
    }

    // =========================================================================
    // Test: List PRs
    // =========================================================================

    @Test
    @DisplayName("GET /pulls — lists open PRs")
    void listPrs_returnsOpenPrs() throws Exception {
        // Open a PR first
        CreatePrRequest request = new CreatePrRequest(
                "List test PR", null, "feature/my-feature", "main");

        mockMvc.perform(post("/api/repos/{owner}/{repo}/pulls", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // List PRs
        mockMvc.perform(get("/api/repos/{owner}/{repo}/pulls", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("status", "open"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].title", is("List test PR")))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    // =========================================================================
    // Test: Get PR Detail
    // =========================================================================

    @Test
    @DisplayName("GET /pulls/{number} — returns PR detail")
    void getPrDetail_returns200() throws Exception {
        // Open a PR
        CreatePrRequest request = new CreatePrRequest(
                "Detail test PR", "PR body", "feature/my-feature", "main");

        mockMvc.perform(post("/api/repos/{owner}/{repo}/pulls", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Get detail
        mockMvc.perform(get("/api/repos/{owner}/{repo}/pulls/{number}", ownerUsername, repoName, 1)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pr.title", is("Detail test PR")))
                .andExpect(jsonPath("$.pr.status", is("open")))
                .andExpect(jsonPath("$.reviews").isArray())
                .andExpect(jsonPath("$.comments").isArray());
    }

    // =========================================================================
    // Test: Submit Review
    // =========================================================================

    @Test
    @DisplayName("POST /pulls/{number}/review — submits APPROVE review")
    void submitReview_approve_returns201() throws Exception {
        // Open a PR
        CreatePrRequest prRequest = new CreatePrRequest(
                "Review test PR", null, "feature/my-feature", "main");

        mockMvc.perform(post("/api/repos/{owner}/{repo}/pulls", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prRequest)))
                .andExpect(status().isCreated());

        // Submit APPROVE review
        SubmitReviewRequest reviewRequest = new SubmitReviewRequest("APPROVE", "Looks good!");

        mockMvc.perform(post("/api/repos/{owner}/{repo}/pulls/{number}/review",
                        ownerUsername, repoName, 1)
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verdict", is("APPROVE")))
                .andExpect(jsonPath("$.body", is("Looks good!")));
    }

    @Test
    @DisplayName("POST /pulls/{number}/review — invalid verdict returns 422")
    void submitReview_invalidVerdict_returns422() throws Exception {
        // Open a PR
        CreatePrRequest prRequest = new CreatePrRequest(
                "Review test PR", null, "feature/my-feature", "main");

        mockMvc.perform(post("/api/repos/{owner}/{repo}/pulls", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prRequest)))
                .andExpect(status().isCreated());

        // Submit invalid verdict
        SubmitReviewRequest reviewRequest = new SubmitReviewRequest("INVALID_VERDICT", null);

        mockMvc.perform(post("/api/repos/{owner}/{repo}/pulls/{number}/review",
                        ownerUsername, repoName, 1)
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewRequest)))
                .andExpect(status().isBadRequest()); // @Pattern validation returns 400
    }

    // =========================================================================
    // Test: Merge — not mergeable (CHANGES_REQUESTED)
    // =========================================================================

    @Test
    @DisplayName("POST /pulls/{number}/merge — CHANGES_REQUESTED blocks merge, returns 422")
    void merge_changesRequested_returns422() throws Exception {
        // Open a PR
        CreatePrRequest prRequest = new CreatePrRequest(
                "Blocked PR", null, "feature/my-feature", "main");

        mockMvc.perform(post("/api/repos/{owner}/{repo}/pulls", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prRequest)))
                .andExpect(status().isCreated());

        // Submit CHANGES_REQUESTED review
        SubmitReviewRequest reviewRequest = new SubmitReviewRequest("CHANGES_REQUESTED", "Needs work.");

        mockMvc.perform(post("/api/repos/{owner}/{repo}/pulls/{number}/review",
                        ownerUsername, repoName, 1)
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reviewRequest)))
                .andExpect(status().isCreated());

        // Attempt merge — should fail with 422
        mockMvc.perform(post("/api/repos/{owner}/{repo}/pulls/{number}/merge",
                        ownerUsername, repoName, 1)
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("strategy", "merge-commit"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", is("INVALID_REQUEST")));
    }

    @Test
    @DisplayName("POST /pulls/{number}/merge — no reviews blocks merge, returns 422")
    void merge_noReviews_returns422() throws Exception {
        // Open a PR
        CreatePrRequest prRequest = new CreatePrRequest(
                "No review PR", null, "feature/my-feature", "main");

        mockMvc.perform(post("/api/repos/{owner}/{repo}/pulls", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prRequest)))
                .andExpect(status().isCreated());

        // Attempt merge without any reviews — should fail with 422
        mockMvc.perform(post("/api/repos/{owner}/{repo}/pulls/{number}/merge",
                        ownerUsername, repoName, 1)
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("strategy", "merge-commit"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", is("INVALID_REQUEST")));
    }

    // =========================================================================
    // Test: Add Comment
    // =========================================================================

    @Test
    @DisplayName("POST /pulls/{number}/comments — adds a comment and returns 201")
    void addComment_success_returns201() throws Exception {
        // Open a PR
        CreatePrRequest prRequest = new CreatePrRequest(
                "Comment test PR", null, "feature/my-feature", "main");

        mockMvc.perform(post("/api/repos/{owner}/{repo}/pulls", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prRequest)))
                .andExpect(status().isCreated());

        // Add a general comment
        AddCommentRequest commentRequest = new AddCommentRequest("Great work!", null, null);

        mockMvc.perform(post("/api/repos/{owner}/{repo}/pulls/{number}/comments",
                        ownerUsername, repoName, 1)
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commentRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body", is("Great work!")))
                .andExpect(jsonPath("$.id", notNullValue()));
    }

    @Test
    @DisplayName("POST /pulls/{number}/comments — adds inline comment with file path and line number")
    void addComment_inline_returns201() throws Exception {
        // Open a PR
        CreatePrRequest prRequest = new CreatePrRequest(
                "Inline comment PR", null, "feature/my-feature", "main");

        mockMvc.perform(post("/api/repos/{owner}/{repo}/pulls", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prRequest)))
                .andExpect(status().isCreated());

        // Add an inline comment
        AddCommentRequest commentRequest = new AddCommentRequest(
                "This line has a bug.", "src/main/java/Foo.java", 42);

        mockMvc.perform(post("/api/repos/{owner}/{repo}/pulls/{number}/comments",
                        ownerUsername, repoName, 1)
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commentRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body", is("This line has a bug.")))
                .andExpect(jsonPath("$.filePath", is("src/main/java/Foo.java")))
                .andExpect(jsonPath("$.lineNumber", is(42)));
    }

    // =========================================================================
    // Test: Sequential PR numbering
    // =========================================================================

    @Test
    @DisplayName("Opening multiple PRs assigns sequential numbers")
    void openMultiplePrs_sequentialNumbers() throws Exception {
        // Create a second feature branch
        Branch secondFeature = Branch.builder()
                .repoId(repoId)
                .name("feature/second")
                .headSha("c".repeat(64))
                .isProtected(false)
                .createdAt(OffsetDateTime.now())
                .build();
        branchRepository.save(secondFeature);

        // Open first PR
        CreatePrRequest pr1 = new CreatePrRequest("First PR", null, "feature/my-feature", "main");
        mockMvc.perform(post("/api/repos/{owner}/{repo}/pulls", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pr1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number", is(1)));

        // Open second PR
        CreatePrRequest pr2 = new CreatePrRequest("Second PR", null, "feature/second", "main");
        mockMvc.perform(post("/api/repos/{owner}/{repo}/pulls", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pr2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.number", is(2)));
    }

    // =========================================================================
    // Test: Unauthenticated access
    // =========================================================================

    @Test
    @DisplayName("POST /pulls — unauthenticated returns 403")
    void openPr_unauthenticated_returns403() throws Exception {
        CreatePrRequest request = new CreatePrRequest(
                "Unauth PR", null, "feature/my-feature", "main");

        mockMvc.perform(post("/api/repos/{owner}/{repo}/pulls", ownerUsername, repoName)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
