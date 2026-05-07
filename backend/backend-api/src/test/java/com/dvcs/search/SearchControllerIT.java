package com.dvcs.search;

import com.dvcs.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code /api/search} endpoint.
 *
 * <p>Covers: search repos (200), search users (200), search with q &lt; 2 chars (400).
 */
@DisplayName("SearchController Integration Tests")
class SearchControllerIT extends AbstractIntegrationTest {

    private String ownerUsername;
    private String ownerToken;

    @BeforeEach
    void setUpData() throws Exception {
        ownerUsername = uniqueUsername("searchowner");
        ownerToken = registerAndLogin(ownerUsername, "SearchPass123!");

        // Create a public repo for search
        mockMvc.perform(post("/api/repos")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "searchable-repo",
                                "description", "A repository for search testing",
                                "isPrivate", false
                        ))))
                .andExpect(status().isCreated());
    }

    // -------------------------------------------------------------------------
    // GET /api/search?type=repositories
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/search?type=repositories returns 200 with paginated results")
    void searchRepositories_validQuery_returns200() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "searchable")
                        .param("type", "repositories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("GET /api/search?type=repositories with no matches returns 200 with empty page")
    void searchRepositories_noMatches_returns200Empty() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "zzznomatch999")
                        .param("type", "repositories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    // -------------------------------------------------------------------------
    // GET /api/search?type=users
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/search?type=users returns 200 with paginated results")
    void searchUsers_validQuery_returns200() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", ownerUsername.substring(0, 5))
                        .param("type", "users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // -------------------------------------------------------------------------
    // GET /api/search?type=code
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/search?type=code returns 200 with paginated results")
    void searchCode_validQuery_returns200() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "function")
                        .param("type", "code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // -------------------------------------------------------------------------
    // Short query validation (q < 2 chars → 400)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/search with q='' (empty) returns 400")
    void search_emptyQuery_returns400() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "")
                        .param("type", "repositories"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/search with q='a' (1 char) returns 400")
    void search_singleCharQuery_returns400() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "a")
                        .param("type", "repositories"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/search with q='ab' (2 chars) returns 200")
    void search_twoCharQuery_returns200() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "ab")
                        .param("type", "repositories"))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // Invalid type
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /api/search with invalid type returns 400")
    void search_invalidType_returns400() throws Exception {
        mockMvc.perform(get("/api/search")
                        .param("q", "test")
                        .param("type", "invalid"))
                .andExpect(status().isBadRequest());
    }
}
