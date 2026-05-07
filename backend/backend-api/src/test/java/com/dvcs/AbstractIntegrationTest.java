package com.dvcs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared base class for all integration tests.
 *
 * <p>Spins up a single PostgreSQL 16 and Redis 7 container per test suite run
 * (containers are static and reused across all subclasses). Provides helper
 * methods for registering users and obtaining JWT tokens.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
public abstract class AbstractIntegrationTest {

    // -------------------------------------------------------------------------
    // Shared containers (started once per JVM, reused across all subclasses)
    // -------------------------------------------------------------------------

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("dvcs_test")
                    .withUsername("dvcs")
                    .withPassword("dvcs");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("storage.backend", () -> "local");
        registry.add("storage.root",
                () -> System.getProperty("java.io.tmpdir") + "/dvcs-test-objects");
        registry.add("jwt.secret",
                () -> "test-secret-key-that-is-long-enough-for-hs256-algorithm");
    }

    // -------------------------------------------------------------------------
    // Shared test infrastructure
    // -------------------------------------------------------------------------

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * Flushes all Redis keys before each test to reset rate-limit buckets and caches.
     */
    @BeforeEach
    void flushRedis() {
        try (io.lettuce.core.RedisClient client = io.lettuce.core.RedisClient.create(
                "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379))) {
            try (var conn = client.connect()) {
                conn.sync().flushall();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helper: register + login → JWT
    // -------------------------------------------------------------------------

    /**
     * Registers a new user with a unique username and returns the JWT access token.
     *
     * @param username the username to register
     * @param password the password to use
     * @return the JWT access token
     */
    protected String registerAndLogin(String username, String password) throws Exception {
        String email = username + "@test.example.com";

        // Register
        String registerBody = objectMapper.writeValueAsString(
                new java.util.HashMap<String, String>() {{
                    put("username", username);
                    put("email", email);
                    put("password", password);
                }});

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated());

        // Login
        String loginBody = objectMapper.writeValueAsString(
                new java.util.HashMap<String, String>() {{
                    put("username", username);
                    put("password", password);
                }});

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson = loginResult.getResponse().getContentAsString();
        return objectMapper.readTree(responseJson).get("accessToken").asText();
    }

    /**
     * Generates a unique username with the given prefix to avoid conflicts between tests.
     *
     * @param prefix the username prefix
     * @return a unique username
     */
    protected static String uniqueUsername(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
