package com.dvcs.issue;

import com.dvcs.AbstractIssueIntegrationTest;
import com.dvcs.auth.dto.LoginRequest;
import com.dvcs.auth.dto.RegisterRequest;
import com.dvcs.auth.repository.UserRepository;
import com.dvcs.issue.domain.Label;
import com.dvcs.issue.dto.AddCommentRequest;
import com.dvcs.issue.dto.ApplyLabelRequest;
import com.dvcs.issue.dto.CreateIssueRequest;
import com.dvcs.issue.dto.UpdateIssueRequest;
import com.dvcs.issue.event.IssueClosedEvent;
import com.dvcs.issue.event.IssueCommentCreatedEvent;
import com.dvcs.issue.repository.LabelRepository;
import com.dvcs.repository.domain.Collaborator;
import com.dvcs.repository.domain.Repository;
import com.dvcs.repository.repository.CollaboratorRepository;
import com.dvcs.repository.repository.RepoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link com.dvcs.issue.controller.IssueController}.
 *
 * <p>Tests the full issue lifecycle: create, comment, label, close, and error cases.
 * Uses Testcontainers for PostgreSQL and Redis.
 *
 * <p>Requirement 11: Issue Tracker.
 */
class IssueControllerIT extends AbstractIssueIntegrationTest {

    // =========================================================================
    // Test event listener — captures application events for verification
    // =========================================================================

    /**
     * Spring component that captures issue-related application events during tests.
     * Registered as a bean in the test application context.
     */
    @Component
    static class TestEventCapture {

        final List<IssueClosedEvent> closedEvents = new ArrayList<>();
        final List<IssueCommentCreatedEvent> commentEvents = new ArrayList<>();

        @EventListener
        void onIssueClosed(IssueClosedEvent event) {
            closedEvents.add(event);
        }

        @EventListener
        void onIssueCommentCreated(IssueCommentCreatedEvent event) {
            commentEvents.add(event);
        }

        void reset() {
            closedEvents.clear();
            commentEvents.clear();
        }
    }

    // =========================================================================
    // Injected beans
    // =========================================================================

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RepoRepository repoRepository;

    @Autowired
    private CollaboratorRepository collaboratorRepository;

    @Autowired
    private LabelRepository labelRepository;

    @Autowired
    private TestEventCapture eventCapture;

    // =========================================================================
    // Test state
    // =========================================================================

    private String ownerToken;
    private String ownerUsername;
    private String collaboratorToken;
    private String collaboratorUsername;
    private String unauthorizedToken;
    private String unauthorizedUsername;
    private String repoName;
    private Long repoId;
    private Long ownerId;
    private Long collaboratorId;

    // Second repo for cross-repo label test
    private String otherRepoName;
    private Long otherRepoId;

    @BeforeEach
    void setUp() throws Exception {
        eventCapture.reset();

        long ts = System.currentTimeMillis();
        ownerUsername        = "issueowner_"  + ts;
        collaboratorUsername = "issuecol_"    + ts;
        unauthorizedUsername = "issueunauth_" + ts;
        repoName             = "issue-test-repo-" + ts;
        otherRepoName        = "other-repo-"      + ts;

        // ---- Register & login owner ----
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(ownerUsername,
                                        ownerUsername + "@example.com", "password123"))))
                .andExpect(status().isCreated());

        MvcResult ownerLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(ownerUsername, "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        ownerToken = objectMapper.readTree(ownerLogin.getResponse().getContentAsString())
                .get("accessToken").asText();

        // ---- Register & login collaborator (WRITE role) ----
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(collaboratorUsername,
                                        collaboratorUsername + "@example.com", "password123"))))
                .andExpect(status().isCreated());

        MvcResult colLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(collaboratorUsername, "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        collaboratorToken = objectMapper.readTree(colLogin.getResponse().getContentAsString())
                .get("accessToken").asText();

        // ---- Register & login unauthorized user (no repo role) ----
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(unauthorizedUsername,
                                        unauthorizedUsername + "@example.com", "password123"))))
                .andExpect(status().isCreated());

        MvcResult unauthLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(unauthorizedUsername, "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        unauthorizedToken = objectMapper.readTree(unauthLogin.getResponse().getContentAsString())
                .get("accessToken").asText();

        // ---- Look up user IDs ----
        ownerId        = userRepository.findByUsername(ownerUsername).orElseThrow().getId();
        collaboratorId = userRepository.findByUsername(collaboratorUsername).orElseThrow().getId();

        // ---- Create primary test repository ----
        Repository repo = Repository.builder()
                .ownerId(ownerId)
                .name(repoName)
                .description("Issue test repository")
                .isPrivate(false)
                .defaultBranch("main")
                .createdAt(OffsetDateTime.now())
                .build();
        repo = repoRepository.save(repo);
        repoId = repo.getId();

        // Owner as OWNER collaborator
        Collaborator ownerCollab = new Collaborator();
        ownerCollab.setRepoId(repoId);
        ownerCollab.setUserId(ownerId);
        ownerCollab.setRole("OWNER");
        collaboratorRepository.save(ownerCollab);

        // Collaborator as WRITE collaborator
        Collaborator writeCollab = new Collaborator();
        writeCollab.setRepoId(repoId);
        writeCollab.setUserId(collaboratorId);
        writeCollab.setRole("WRITE");
        collaboratorRepository.save(writeCollab);

        // ---- Create a second repository (for cross-repo label test) ----
        Repository otherRepo = Repository.builder()
                .ownerId(ownerId)
                .name(otherRepoName)
                .description("Other repo for label isolation test")
                .isPrivate(false)
                .defaultBranch("main")
                .createdAt(OffsetDateTime.now())
                .build();
        otherRepo = repoRepository.save(otherRepo);
        otherRepoId = otherRepo.getId();
    }

    // =========================================================================
    // Test: Create issue
    // =========================================================================

    @Test
    @DisplayName("POST /issues — creates an issue and returns 201")
    void createIssue_success_returns201() throws Exception {
        CreateIssueRequest request = new CreateIssueRequest("First issue", "Issue body text");

        mockMvc.perform(post("/api/repos/{owner}/{repo}/issues", ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("First issue")))
                .andExpect(jsonPath("$.body", is("Issue body text")))
                .andExpect(jsonPath("$.status", is("open")))
                .andExpect(jsonPath("$.number", is(1)))
                .andExpect(jsonPath("$.id", notNullValue()));
    }

    // =========================================================================
    // Test: Add comment — verifies IssueCommentCreatedEvent is published
    // =========================================================================

    @Test
    @DisplayName("POST /issues/{number}/comments — adds comment, returns 201, publishes event")
    void addComment_success_returns201_andPublishesEvent() throws Exception {
        // Create an issue as the owner
        long issueId = createIssueAndGetId("Comment test issue", null);

        // Add a comment as the collaborator (so the owner gets notified)
        AddCommentRequest commentRequest = new AddCommentRequest("This is a comment");

        mockMvc.perform(post("/api/repos/{owner}/{repo}/issues/{number}/comments",
                        ownerUsername, repoName, 1)
                        .header("Authorization", "Bearer " + collaboratorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(commentRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body", is("This is a comment")))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.issueId", is((int) issueId)));

        // Verify IssueCommentCreatedEvent was published
        assertThat(eventCapture.commentEvents)
                .as("IssueCommentCreatedEvent should have been published")
                .hasSize(1);
        IssueCommentCreatedEvent event = eventCapture.commentEvents.get(0);
        assertThat(event.issueId()).isEqualTo(issueId);
        assertThat(event.commenterUserId()).isEqualTo(collaboratorId);
        // Owner should be in the notification targets since they authored the issue
        assertThat(event.targetUserIds()).contains(ownerId);
    }

    // =========================================================================
    // Test: Apply label — success
    // =========================================================================

    @Test
    @DisplayName("POST /issues/{number}/labels — applies label and returns 200")
    void applyLabel_success_returns200() throws Exception {
        // Create an issue
        createIssueAndGetId("Label test issue", null);

        // Create a label directly in the DB (scoped to the primary repo)
        Label label = labelRepository.save(Label.builder()
                .repoId(repoId)
                .name("bug")
                .color("#ff0000")
                .build());

        // Apply the label via the API
        ApplyLabelRequest applyRequest = new ApplyLabelRequest(label.getId());

        mockMvc.perform(post("/api/repos/{owner}/{repo}/issues/{number}/labels",
                        ownerUsername, repoName, 1)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(applyRequest)))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // Test: Apply non-existent / cross-repo label → 422
    // =========================================================================

    @Test
    @DisplayName("POST /issues/{number}/labels — label from different repo returns 422")
    void applyLabel_crossRepoLabel_returns422() throws Exception {
        // Create an issue in the primary repo
        createIssueAndGetId("Cross-repo label test issue", null);

        // Create a label in the OTHER repo (different repoId)
        Label otherLabel = labelRepository.save(Label.builder()
                .repoId(otherRepoId)
                .name("other-label")
                .color("#00ff00")
                .build());

        // Try to apply the other-repo label to the issue in the primary repo → 422
        ApplyLabelRequest applyRequest = new ApplyLabelRequest(otherLabel.getId());

        mockMvc.perform(post("/api/repos/{owner}/{repo}/issues/{number}/labels",
                        ownerUsername, repoName, 1)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(applyRequest)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", is("INVALID_REQUEST")));
    }

    // =========================================================================
    // Test: Close issue — verifies IssueClosedEvent is published
    // =========================================================================

    @Test
    @DisplayName("POST /issues/{number}/close — closes issue, returns 200 with status=closed, publishes event")
    void closeIssue_success_returns200_andPublishesEvent() throws Exception {
        // Create an issue
        long issueId = createIssueAndGetId("Issue to close", null);

        // Close it
        mockMvc.perform(post("/api/repos/{owner}/{repo}/issues/{number}/close",
                        ownerUsername, repoName, 1)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("closed")))
                .andExpect(jsonPath("$.id", is((int) issueId)));

        // Verify IssueClosedEvent was published
        assertThat(eventCapture.closedEvents)
                .as("IssueClosedEvent should have been published")
                .hasSize(1);
        IssueClosedEvent event = eventCapture.closedEvents.get(0);
        assertThat(event.issueId()).isEqualTo(issueId);
        assertThat(event.repoId()).isEqualTo(repoId);
    }

    // =========================================================================
    // Test: Unauthorized update → 403
    // =========================================================================

    @Test
    @DisplayName("PATCH /issues/{number} — unauthorized user (no WRITE/OWNER role) returns 403")
    void updateIssue_unauthorizedUser_returns403() throws Exception {
        // Create an issue as the owner
        createIssueAndGetId("Issue to update", "Original body");

        // Attempt to update as the unauthorized user (not the author, no WRITE/OWNER role)
        UpdateIssueRequest updateRequest = new UpdateIssueRequest("Hacked title", null);

        mockMvc.perform(patch("/api/repos/{owner}/{repo}/issues/{number}",
                        ownerUsername, repoName, 1)
                        .header("Authorization", "Bearer " + unauthorizedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Creates an issue via the API and returns its ID.
     */
    private long createIssueAndGetId(String title, String body) throws Exception {
        CreateIssueRequest request = new CreateIssueRequest(title, body);

        MvcResult result = mockMvc.perform(post("/api/repos/{owner}/{repo}/issues",
                        ownerUsername, repoName)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("id").asLong();
    }
}
