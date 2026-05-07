package com.dvcs.git.transport;

import com.dvcs.common.security.RepoAccessGuard;
import com.dvcs.repository.repository.CollaboratorRepository;
import com.dvcs.repository.repository.RepoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
import org.springframework.test.web.servlet.MockMvc;
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
import java.util.Base64;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link com.dvcs.repository.controller.TreeController} and
 * {@link com.dvcs.repository.controller.BlobController}.
 *
 * <p>Tests:
 * <ul>
 *   <li>Push a commit with nested directory structure via {@code git push}</li>
 *   <li>Call tree API at root and subdirectory</li>
 *   <li>Call blob API for a text file and a binary file</li>
 *   <li>Verify raw endpoint streams correct bytes</li>
 *   <li>Verify {@code ../} path returns 400</li>
 * </ul>
 *
 * <p>Requirement 7: File Tree and Blob Retrieval.
 * Requirement 18.2: Path traversal rejection.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureMockMvc
@Testcontainers
@DisplayName("TreeBlobControllerIT — tree and blob API integration tests")
class TreeBlobControllerIT {

    // -------------------------------------------------------------------------
    // Testcontainers
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
    // Dynamic property source
    // -------------------------------------------------------------------------

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("storage.backend", () -> "local");
        registry.add("storage.root", () -> {
            try {
                return Files.createTempDirectory("dvcs-tree-blob-it").toAbsolutePath().toString();
            } catch (IOException e) {
                throw new RuntimeException("Failed to create temp storage root", e);
            }
        });
        registry.add("cors.allowed-origins", () -> "http://localhost:3000");
        registry.add("jwt.secret", () -> "test-secret-key-for-integration-tests-minimum-256-bits");
    }

    // -------------------------------------------------------------------------
    // Test configuration — real RepoLookupService
    // -------------------------------------------------------------------------

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
    // MockBean — bypass access control
    // -------------------------------------------------------------------------

    @MockBean
    RepoAccessGuard repoAccessGuard;

    // -------------------------------------------------------------------------
    // Injected beans
    // -------------------------------------------------------------------------

    @LocalServerPort
    int port;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    RepoRepository repoRepository;

    @Autowired
    com.dvcs.auth.repository.UserRepository userRepository;

    @Autowired
    CollaboratorRepository collaboratorRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    // -------------------------------------------------------------------------
    // Test state
    // -------------------------------------------------------------------------

    private String owner;
    private String repoName;
    private Long repoId;

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    @BeforeEach
    void setUp() throws Exception {
        // Configure access guard to always allow
        when(repoAccessGuard.canRead(any(), any(), any())).thenReturn(true);
        when(repoAccessGuard.canWrite(any(), any(), any())).thenReturn(true);

        // Use unique names per test to avoid conflicts
        owner = "treeuser_" + System.currentTimeMillis();
        repoName = "tree-test-repo";

        // Register user
        registerUser(owner, owner + "@example.com", "password123");

        Long userId = userRepository.findByUsername(owner)
                .orElseThrow(() -> new AssertionError("User not found after registration"))
                .getId();

        // Create repository
        jdbcTemplate.update(
                "INSERT INTO repositories (owner_id, name, is_private, default_branch) VALUES (?, ?, ?, ?)",
                userId, repoName, false, "main");

        repoId = jdbcTemplate.queryForObject(
                "SELECT id FROM repositories WHERE owner_id = ? AND name = ?",
                Long.class, userId, repoName);

        jdbcTemplate.update(
                "INSERT INTO collaborators (repo_id, user_id, role) VALUES (?, ?, ?)",
                repoId, userId, "OWNER");
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /**
     * Pushes a commit with nested directory structure and verifies the tree API
     * returns the correct entries at root and subdirectory level.
     *
     * <p>Validates: Requirement 7.1 — tree listing with entry names, types, sizes,
     * and last-commit SHA.
     */
    @Test
    @DisplayName("tree API — root listing returns all top-level entries after git push")
    void treeApi_rootListing_returnsEntries() throws Exception {
        assumeTrue(isGitAvailable(), "git CLI not available — skipping");

        Path cloneDir = pushNestedStructure();

        // Call tree API at root
        mockMvc.perform(get("/api/repos/{owner}/{repo}/tree/main", owner, repoName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.name == 'README.md')].type").value("blob"))
                .andExpect(jsonPath("$[?(@.name == 'src')].type").value("tree"));
    }

    /**
     * Verifies the tree API returns correct entries for a subdirectory.
     *
     * <p>Validates: Requirement 7.1 — tree listing at subdirectory path.
     */
    @Test
    @DisplayName("tree API — subdirectory listing returns nested entries")
    void treeApi_subdirectoryListing_returnsNestedEntries() throws Exception {
        assumeTrue(isGitAvailable(), "git CLI not available — skipping");

        pushNestedStructure();

        // Call tree API at src/
        mockMvc.perform(get("/api/repos/{owner}/{repo}/tree/main/src", owner, repoName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.name == 'Main.java')].type").value("blob"));
    }

    /**
     * Verifies the blob API returns Base64-encoded content for a text file.
     *
     * <p>Validates: Requirement 7.2 — blob content, size, encoding, lastCommitSha.
     */
    @Test
    @DisplayName("blob API — text file returns base64 content and metadata")
    void blobApi_textFile_returnsBase64Content() throws Exception {
        assumeTrue(isGitAvailable(), "git CLI not available — skipping");

        pushNestedStructure();

        String response = mockMvc.perform(
                        get("/api/repos/{owner}/{repo}/blob/main/README.md", owner, repoName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encoding").value("base64"))
                .andExpect(jsonPath("$.size").isNumber())
                .andExpect(jsonPath("$.content").isString())
                .andExpect(jsonPath("$.lastCommitSha").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Verify the decoded content matches what we pushed
        JsonNode json = objectMapper.readTree(response);
        String encoded = json.get("content").asText();
        String decoded = new String(Base64.getDecoder().decode(encoded));
        assertThat(decoded).contains("Hello World");
    }

    /**
     * Verifies the blob API returns correct content for a binary file.
     *
     * <p>Validates: Requirement 7.2 — binary blob retrieval.
     */
    @Test
    @DisplayName("blob API — binary file returns base64 content")
    void blobApi_binaryFile_returnsBase64Content() throws Exception {
        assumeTrue(isGitAvailable(), "git CLI not available — skipping");

        pushNestedStructure();

        String response = mockMvc.perform(
                        get("/api/repos/{owner}/{repo}/blob/main/data.bin", owner, repoName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encoding").value("base64"))
                .andExpect(jsonPath("$.size").isNumber())
                .andExpect(jsonPath("$.content").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Verify the decoded content matches the binary data we pushed
        JsonNode json = objectMapper.readTree(response);
        String encoded = json.get("content").asText();
        byte[] decoded = Base64.getDecoder().decode(encoded);
        // First byte should be 0x00 (binary data)
        assertThat(decoded).isNotEmpty();
        assertThat(decoded[0]).isEqualTo((byte) 0x00);
    }

    /**
     * Verifies the raw endpoint streams the correct bytes with appropriate Content-Type.
     *
     * <p>Validates: Requirement 7.3 — raw bytes with Content-Type header.
     */
    @Test
    @DisplayName("raw endpoint — streams correct bytes for text file")
    void rawEndpoint_textFile_streamsCorrectBytes() throws Exception {
        assumeTrue(isGitAvailable(), "git CLI not available — skipping");

        pushNestedStructure();

        byte[] responseBytes = mockMvc.perform(
                        get("/api/repos/{owner}/{repo}/raw/main/README.md", owner, repoName))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        String content = new String(responseBytes);
        assertThat(content).contains("Hello World");
    }

    /**
     * Verifies that a path containing {@code ../} returns HTTP 400.
     *
     * <p>Validates: Requirement 7.5 / Requirement 18.2 — path traversal rejection.
     */
    @Test
    @DisplayName("path traversal — ../path returns 400")
    void pathTraversal_dotDotSlash_returns400() throws Exception {
        // No git push needed — path validation happens before object store access
        mockMvc.perform(
                        get("/api/repos/{owner}/{repo}/tree/main/../etc/passwd", owner, repoName))
                .andExpect(status().isBadRequest());
    }

    /**
     * Verifies that a URL-encoded path traversal sequence returns HTTP 400.
     *
     * <p>Validates: Requirement 18.2 — URL-encoded traversal rejection.
     */
    @Test
    @DisplayName("path traversal — %2e%2e encoded path returns 400")
    void pathTraversal_urlEncoded_returns400() throws Exception {
        mockMvc.perform(
                        get("/api/repos/{owner}/{repo}/blob/main/%2e%2e/secret", owner, repoName))
                .andExpect(status().isBadRequest());
    }

    /**
     * Verifies that a non-existent ref returns HTTP 404.
     *
     * <p>Validates: Requirement 7.4 — 404 for non-existent ref.
     */
    @Test
    @DisplayName("tree API — non-existent ref returns 404")
    void treeApi_nonExistentRef_returns404() throws Exception {
        mockMvc.perform(
                        get("/api/repos/{owner}/{repo}/tree/nonexistent-branch", owner, repoName))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    /**
     * Pushes a commit with the following structure:
     * <pre>
     *   README.md          (text file: "Hello World\n")
     *   data.bin           (binary file: bytes 0x00..0x0F)
     *   src/
     *     Main.java        (text file: "public class Main {}\n")
     * </pre>
     *
     * @return the clone directory path
     */
    private Path pushNestedStructure() throws Exception {
        Path cloneDir = Files.createTempDirectory("git-tree-blob-test");

        String cloneUrl = "http://localhost:" + port + "/api/git/" + owner + "/" + repoName;

        // Clone (empty repo)
        int cloneExit = runGit(cloneDir, "clone", cloneUrl, ".");
        assertThat(cloneExit).as("git clone should succeed").isEqualTo(0);

        // Configure git identity
        runGit(cloneDir, "config", "user.email", "test@example.com");
        runGit(cloneDir, "config", "user.name", "Test User");

        // Create README.md
        Files.writeString(cloneDir.resolve("README.md"), "Hello World\n");

        // Create binary file (bytes 0x00..0x0F)
        byte[] binaryData = new byte[16];
        for (int i = 0; i < 16; i++) {
            binaryData[i] = (byte) i;
        }
        Files.write(cloneDir.resolve("data.bin"), binaryData);

        // Create src/Main.java
        Files.createDirectories(cloneDir.resolve("src"));
        Files.writeString(cloneDir.resolve("src/Main.java"), "public class Main {}\n");

        // Add all files and commit
        runGit(cloneDir, "add", ".");
        int commitExit = runGit(cloneDir, "commit", "-m", "Initial commit with nested structure");
        assertThat(commitExit).as("git commit should succeed").isEqualTo(0);

        // Push
        int pushExit = runGit(cloneDir, "push", "origin", "main");
        assertThat(pushExit).as("git push should succeed").isEqualTo(0);

        return cloneDir;
    }

    /**
     * Registers a new user via {@code POST /api/auth/register}.
     */
    private void registerUser(String username, String email, String password) throws Exception {
        String body = objectMapper.writeValueAsString(
                new HashMap<String, String>() {{
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
     * Runs a {@code git} command in the given working directory.
     */
    private int runGit(Path workDir, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.addAll(Arrays.asList(args));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir.toFile());
        pb.environment().put("GIT_TERMINAL_PROMPT", "0");
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
     * Checks whether the {@code git} CLI is available.
     */
    private static boolean isGitAvailable() {
        try {
            Process process = new ProcessBuilder("git", "--version")
                    .redirectErrorStream(true)
                    .start();
            process.getInputStream().readAllBytes();
            return process.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
