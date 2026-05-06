package com.dvcs.git.transport;

import com.dvcs.common.security.RepoAccessGuard;
import com.dvcs.git.ref.BranchRepository;
import com.dvcs.repository.repository.CollaboratorRepository;
import com.dvcs.repository.repository.RepoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration test for the Git smart HTTP transport protocol.
 *
 * <p>Starts real PostgreSQL and Redis containers via Testcontainers, boots the full
 * Spring Boot application on a random port, and exercises the complete clone → commit
 * → push flow using the real {@code git} CLI via {@link ProcessBuilder}.
 *
 * <h2>Auth strategy</h2>
 * <p>Rather than wiring up HTTP Basic auth for the git CLI, this test uses
 * {@code @MockBean RepoAccessGuard} configured to always return {@code true}.
 * This keeps the test focused on transport protocol correctness rather than
 * authentication mechanics.
 *
 * <h2>Repo lookup</h2>
 * <p>A {@link TestConfiguration} provides a real {@link RepoLookupService} backed
 * by the JPA {@link RepoRepository}, replacing the stub that is active in production
 * when task 7.1 is not yet complete.
 *
 * <p>Requirement 6: HTTP Smart Git Transport — clone and push.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@DisplayName("GitTransportIT — clone and push integration test")
class GitTransportIT {

    // -------------------------------------------------------------------------
    // Testcontainers — PostgreSQL and Redis
    // -------------------------------------------------------------------------

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("dvcs_test")
                    .withUsername("dvcs_test")
                    .withPassword("dvcs_test");

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379);

    // -------------------------------------------------------------------------
    // Dynamic property source — wire containers into Spring context
    // -------------------------------------------------------------------------

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Datasource
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Redis
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

        // Object store — use a temp directory so tests are isolated
        registry.add("storage.backend", () -> "local");
        registry.add("storage.root", () -> {
            try {
                return Files.createTempDirectory("dvcs-it-objects").toAbsolutePath().toString();
            } catch (IOException e) {
                throw new RuntimeException("Failed to create temp storage root", e);
            }
        });

        // Disable rate limiting for tests
        registry.add("cors.allowed-origins", () -> "http://localhost:3000");
    }

    // -------------------------------------------------------------------------
    // Test configuration — real RepoLookupService + permissive RepoAccessGuard
    // -------------------------------------------------------------------------

    /**
     * Provides a real {@link RepoLookupService} backed by JPA repositories.
     * This replaces the {@link StubRepoLookupService} that is active when task 7.1
     * is not yet complete.
     */
    @TestConfiguration
    static class TestRepoLookupConfig {

        @Bean
        @Primary
        RepoLookupService realRepoLookupService(
                com.dvcs.auth.repository.UserRepository userRepository,
                RepoRepository repoRepository) {
            return (owner, repoName) -> {
                com.dvcs.auth.domain.User user = userRepository.findByUsername(owner)
                        .orElseThrow(() -> new RepoNotFoundException(owner, repoName));
                com.dvcs.repository.domain.Repository repo =
                        repoRepository.findByOwnerIdAndName(user.getId(), repoName)
                                .orElseThrow(() -> new RepoNotFoundException(owner, repoName));
                return repo.getId();
            };
        }
    }

    // -------------------------------------------------------------------------
    // MockBean — bypass access control for transport tests
    // -------------------------------------------------------------------------

    /**
     * Replaces the real {@link RepoAccessGuard} with a permissive mock that always
     * grants read and write access. This keeps the test focused on transport protocol
     * correctness rather than authentication mechanics.
     */
    @MockBean
    RepoAccessGuard repoAccessGuard;

    // -------------------------------------------------------------------------
    // Injected beans
    // -------------------------------------------------------------------------

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    BranchRepository branchRepository;

    @Autowired
    CollaboratorRepository collaboratorRepository;

    @Autowired
    RepoRepository repoRepository;

    @Autowired
    com.dvcs.auth.repository.UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    // -------------------------------------------------------------------------
    // Test state
    // -------------------------------------------------------------------------

    private Long userId;
    private Long repoId;

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    @BeforeEach
    void setupAccessGuard() {
        when(repoAccessGuard.canRead(any(), any(), any())).thenReturn(true);
        when(repoAccessGuard.canWrite(any(), any(), any())).thenReturn(true);
    }

    // -------------------------------------------------------------------------
    // Main test
    // -------------------------------------------------------------------------

    /**
     * Full end-to-end test:
     * <ol>
     *   <li>Register a user via {@code POST /api/auth/register}</li>
     *   <li>Create a repository and collaborator row directly in the DB</li>
     *   <li>Clone the (empty) repository via {@code git clone}</li>
     *   <li>Create a file, commit, and push via {@code git push}</li>
     *   <li>Assert that the branch {@code head_sha} was updated in the DB</li>
     * </ol>
     *
     * <p>Validates: Requirement 6.3 — receive-pack updates branch head_sha.
     */
    @Test
    @DisplayName("git clone empty repo, add file, git push → branch head_sha updated in DB")
    void gitCloneAndPush_updatesHeadSha() throws Exception {
        // Prerequisite: git must be installed on the machine running the tests
        assumeTrue(isGitAvailable(), "git CLI is not available — skipping integration test");

        // Step 1: Register a user
        registerUser("testuser", "test@example.com", "password123");

        // Retrieve the created user's ID
        userId = userRepository.findByUsername("testuser")
                .orElseThrow(() -> new AssertionError("User 'testuser' not found after registration"))
                .getId();

        // Step 2: Create a repository and collaborator row directly in the DB
        jdbcTemplate.update(
                "INSERT INTO repositories (owner_id, name, is_private, default_branch) " +
                "VALUES (?, ?, ?, ?)",
                userId, "testrepo", false, "main");

        repoId = jdbcTemplate.queryForObject(
                "SELECT id FROM repositories WHERE owner_id = ? AND name = ?",
                Long.class, userId, "testrepo");

        assertThat(repoId).isNotNull();

        jdbcTemplate.update(
                "INSERT INTO collaborators (repo_id, user_id, role) VALUES (?, ?, ?)",
                repoId, userId, "OWNER");

        // Step 3: Create a temp directory for the git clone
        Path cloneDir = Files.createTempDirectory("git-clone-test");

        // Step 4: git clone (empty repo — exits 0 with a warning about empty repo)
        String cloneUrl = "http://localhost:" + port + "/api/git/testuser/testrepo";
        int cloneExit = runGit(cloneDir, "clone", cloneUrl, ".");
        // git clone of an empty repo exits 0 (with a "warning: You appear to have cloned an empty repository" message)
        assertThat(cloneExit)
                .as("git clone should succeed (exit 0) even for an empty repository")
                .isEqualTo(0);

        // Step 5: Configure git identity in the cloned repo
        runGit(cloneDir, "config", "user.email", "test@example.com");
        runGit(cloneDir, "config", "user.name", "Test User");

        // Step 6: Create a file and commit
        Path readmeFile = cloneDir.resolve("README.md");
        Files.writeString(readmeFile, "Hello World\n");

        runGit(cloneDir, "add", "README.md");
        int commitExit = runGit(cloneDir, "commit", "-m", "Initial commit");
        assertThat(commitExit)
                .as("git commit should succeed")
                .isEqualTo(0);

        // Step 7: git push
        int pushExit = runGit(cloneDir, "push", "origin", "main");
        assertThat(pushExit)
                .as("git push should succeed (exit 0)")
                .isEqualTo(0);

        // Step 8: Assert branch head_sha was updated in the DB
        Optional<com.dvcs.git.ref.Branch> branchOpt =
                branchRepository.findByRepoIdAndName(repoId, "main");

        assertThat(branchOpt)
                .as("Branch 'main' should exist in the DB after push")
                .isPresent();

        String headSha = branchOpt.get().getHeadSha();
        assertThat(headSha)
                .as("Branch head_sha should be non-null and non-empty after push")
                .isNotNull()
                .isNotBlank();

        System.out.println("✓ Branch 'main' head_sha after push: " + headSha);
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    /**
     * Registers a new user via {@code POST /api/auth/register}.
     *
     * @param username the username
     * @param email    the email address
     * @param password the plain-text password
     * @throws Exception if the HTTP request fails
     */
    private void registerUser(String username, String email, String password) throws Exception {
        String body = objectMapper.writeValueAsString(
                new java.util.HashMap<String, String>() {{
                    put("username", username);
                    put("email", email);
                    put("password", password);
                }});

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/auth/register"))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode())
                .as("Registration should return HTTP 201")
                .isEqualTo(201);
    }

    /**
     * Runs a {@code git} command in the given working directory via {@link ProcessBuilder}.
     *
     * <p>Captures stdout and stderr (merged) and prints them to {@code System.out}
     * for debugging. Sets {@code GIT_TERMINAL_PROMPT=0} to prevent git from
     * prompting for credentials.
     *
     * @param workDir the working directory for the git command
     * @param args    the git sub-command and arguments (without the leading "git")
     * @return the process exit code
     * @throws Exception if the process cannot be started or interrupted
     */
    private int runGit(Path workDir, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.addAll(Arrays.asList(args));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir.toFile());
        pb.environment().put("GIT_TERMINAL_PROMPT", "0");
        // Disable credential helpers to avoid interactive prompts
        pb.environment().put("GIT_ASKPASS", "echo");
        pb.redirectErrorStream(true);

        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();

        System.out.println("git " + String.join(" ", args) + " → exit=" + exitCode);
        if (!output.isBlank()) {
            System.out.println(output);
        }

        return exitCode;
    }

    /**
     * Checks whether the {@code git} CLI is available on the current machine.
     *
     * @return {@code true} if {@code git --version} exits with code 0
     */
    private static boolean isGitAvailable() {
        try {
            Process process = new ProcessBuilder("git", "--version")
                    .redirectErrorStream(true)
                    .start();
            process.getInputStream().readAllBytes(); // drain output
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
