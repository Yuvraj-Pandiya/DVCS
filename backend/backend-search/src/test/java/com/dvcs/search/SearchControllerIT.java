package com.dvcs.search;

import com.dvcs.AbstractSearchIntegrationTest;
import com.dvcs.auth.domain.User;
import com.dvcs.auth.dto.LoginRequest;
import com.dvcs.auth.dto.RegisterRequest;
import com.dvcs.auth.repository.UserRepository;
import com.dvcs.repository.domain.GitObject;
import com.dvcs.repository.domain.Repository;
import com.dvcs.repository.repository.GitObjectRepository;
import com.dvcs.repository.repository.RepoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link com.dvcs.search.controller.SearchController}.
 *
 * <p>Tests search functionality across repositories, code, and users.
 * Uses Testcontainers for PostgreSQL and Redis.
 *
 * <p>Requirement 15: Search.
 */
class SearchControllerIT extends AbstractSearchIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RepoRepository repoRepository;

    @Autowired
    private GitObjectRepository gitObjectRepository;

    @Value("${storage.root:./data}")
    private String storageRoot;

    private String userToken;
    private String username;
    private Long userId;
    private String repoName;
    private Long repoId;

    @BeforeEach
    void setUp() throws Exception {
        long ts = System.currentTimeMillis();
        username = "searchuser_" + ts;
        repoName = "searchable-repo-" + ts;

        // Register and login user
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest(username,
                                        username + "@example.com", "password123"))))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(username, "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        userToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        userId = userRepository.findByUsername(username).orElseThrow().getId();

        // Create a public repository with a known name
        Repository repo = Repository.builder()
                .ownerId(userId)
                .name(repoName)
                .description("A searchable test repository for integration tests")
                .isPrivate(false)
                .defaultBranch("main")
                .createdAt(OffsetDateTime.now())
                .build();
        repo = repoRepository.save(repo);
        repoId = repo.getId();
    }

    // =========================================================================
    // Test: Search repositories by name
    // =========================================================================

    @Test
    @DisplayName("GET /api/search?q=searchable&type=repositories — finds public repo by name")
    void searchRepositories_byName_findsRepo() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "searchable")
                        .param("type", "repositories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()", greaterThan(0)))
                .andExpect(jsonPath("$.content[0].name", is(repoName)))
                .andExpect(jsonPath("$.content[0].ownerUsername", is(username)))
                .andExpect(jsonPath("$.content[0].description", containsString("searchable")));
    }

    // =========================================================================
    // Test: Search repositories by description
    // =========================================================================

    @Test
    @DisplayName("GET /api/search?q=integration&type=repositories — finds repo by description")
    void searchRepositories_byDescription_findsRepo() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "integration")
                        .param("type", "repositories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()", greaterThan(0)))
                .andExpect(jsonPath("$.content[0].name", is(repoName)));
    }

    // =========================================================================
    // Test: Search with query < 2 chars → 400
    // =========================================================================

    @Test
    @DisplayName("GET /api/search?q=a&type=repositories — query too short returns 400")
    void search_queryTooShort_returns400() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "a")
                        .param("type", "repositories"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("BAD_REQUEST")))
                .andExpect(jsonPath("$.message", containsString("at least 2 characters")));
    }

    // =========================================================================
    // Test: Search users by username
    // =========================================================================

    @Test
    @DisplayName("GET /api/search?q=searchuser&type=users — finds user by username")
    void searchUsers_byUsername_findsUser() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "searchuser")
                        .param("type", "users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()", greaterThan(0)))
                .andExpect(jsonPath("$.content[0].username", is(username)));
    }

    // =========================================================================
    // Test: Search users by bio
    // =========================================================================

    @Test
    @DisplayName("GET /api/search?q=developer&type=users — finds user by bio")
    void searchUsers_byBio_findsUser() throws Exception {
        // Update user bio
        User user = userRepository.findById(userId).orElseThrow();
        user.setBio("I am a software developer");
        userRepository.save(user);

        mockMvc.perform(get("/api/search")
                        .param("q", "developer")
                        .param("type", "users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()", greaterThan(0)))
                .andExpect(jsonPath("$.content[0].username", is(username)))
                .andExpect(jsonPath("$.content[0].bio", containsString("developer")));
    }

    // =========================================================================
    // Test: Search code for known file content
    // =========================================================================

    @Test
    @DisplayName("GET /api/search?q=HelloWorld&type=code — finds code snippet in blob")
    void searchCode_findsSnippet() throws Exception {
        // Create a blob with known content
        String fileContent = "public class HelloWorld {\n" +
                "    public static void main(String[] args) {\n" +
                "        System.out.println(\"Hello, World!\");\n" +
                "    }\n" +
                "}\n";

        byte[] contentBytes = fileContent.getBytes(StandardCharsets.UTF_8);

        // Compute SHA-256 of the serialized blob (header + content)
        String header = "blob " + contentBytes.length + "\0";
        byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
        byte[] serialized = new byte[headerBytes.length + contentBytes.length];
        System.arraycopy(headerBytes, 0, serialized, 0, headerBytes.length);
        System.arraycopy(contentBytes, 0, serialized, headerBytes.length, contentBytes.length);

        String sha = computeSha256Hex(serialized);

        // Write the blob to the local filesystem
        writeBlobToFilesystem(String.valueOf(repoId), sha, serialized);

        // Create git_objects table entry
        GitObject gitObject = GitObject.builder()
                .repoId(repoId)
                .sha(sha)
                .type("BLOB")
                .size((long) contentBytes.length)
                .storedPath("objects/" + sha.substring(0, 2) + "/" + sha.substring(2))
                .build();
        gitObjectRepository.save(gitObject);

        // Search for "HelloWorld" in code
        mockMvc.perform(get("/api/search")
                        .param("q", "HelloWorld")
                        .param("type", "code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()", greaterThan(0)))
                .andExpect(jsonPath("$.content[0].repoOwner", is(username)))
                .andExpect(jsonPath("$.content[0].repoName", is(repoName)))
                .andExpect(jsonPath("$.content[0].snippet", containsString("HelloWorld")));
    }

    // =========================================================================
    // Test: Invalid search type → 400
    // =========================================================================

    @Test
    @DisplayName("GET /api/search?q=test&type=invalid — invalid type returns 400")
    void search_invalidType_returns400() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "test")
                        .param("type", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("BAD_REQUEST")))
                .andExpect(jsonPath("$.message", containsString("Invalid search type")));
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Writes blob bytes to the local filesystem at the expected path.
     */
    private void writeBlobToFilesystem(String repoId, String sha, byte[] data) throws IOException {
        String prefix = sha.substring(0, 2);
        String rest = sha.substring(2);
        Path objectPath = Paths.get(storageRoot).resolve(repoId)
                .resolve("objects").resolve(prefix).resolve(rest);
        Files.createDirectories(objectPath.getParent());
        Files.write(objectPath, data);
    }

    /**
     * Computes the SHA-256 hex digest of the given bytes.
     */
    private static String computeSha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data);
            StringBuilder sb = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
