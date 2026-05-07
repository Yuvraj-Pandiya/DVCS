package com.dvcs.search.controller;

import com.dvcs.common.exception.InvalidRequestException;
import com.dvcs.search.dto.CodeSearchResult;
import com.dvcs.search.dto.RepositorySearchResult;
import com.dvcs.search.dto.UserSearchResult;
import com.dvcs.search.service.SearchService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for search operations.
 *
 * <p>Endpoint:
 * <ul>
 *   <li>GET /api/search?q=&amp;type=repositories|code|users — search across the platform</li>
 * </ul>
 *
 * <p>Requirement 15: Search — full-text search across repositories, code, and users.
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private static final int MIN_QUERY_LENGTH = 2;

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * Searches across repositories, code, or users based on the {@code type} parameter.
     *
     * <p>The query string {@code q} must be at least 2 characters long.
     * The {@code type} parameter determines the search scope:
     * <ul>
     *   <li>{@code repositories} — search by repository name or description</li>
     *   <li>{@code code} — search file content within public repositories</li>
     *   <li>{@code users} — search by username or bio</li>
     * </ul>
     *
     * @param q        the search query string; must be at least 2 characters
     * @param type     the search type: {@code repositories}, {@code code}, or {@code users}
     * @param pageable pagination parameters (default page size: 20)
     * @return HTTP 200 with paginated search results, or HTTP 400 if query is too short
     * @throws InvalidRequestException if {@code q} is shorter than 2 characters or
     *                                 {@code type} is not one of the supported values
     */
    @GetMapping
    public ResponseEntity<?> search(
            @RequestParam String q,
            @RequestParam String type,
            @PageableDefault(size = 20) Pageable pageable) {

        // Validate query length
        if (q == null || q.length() < MIN_QUERY_LENGTH) {
            throw new InvalidRequestException(
                    "Search query must be at least " + MIN_QUERY_LENGTH + " characters long.");
        }

        return switch (type.toLowerCase()) {
            case "repositories" -> {
                Page<RepositorySearchResult> results =
                        searchService.searchRepositories(q, pageable);
                yield ResponseEntity.ok(results);
            }
            case "code" -> {
                Page<CodeSearchResult> results =
                        searchService.searchCode(q, pageable);
                yield ResponseEntity.ok(results);
            }
            case "users" -> {
                Page<UserSearchResult> results =
                        searchService.searchUsers(q, pageable);
                yield ResponseEntity.ok(results);
            }
            default -> throw new InvalidRequestException(
                    "Invalid search type '" + type + "'. Must be one of: repositories, code, users.");
        };
    }
}
