package com.dvcs.auth.controller;

import com.dvcs.auth.domain.User;
import com.dvcs.auth.dto.UserProfileDto;
import com.dvcs.auth.service.UserService;
import com.dvcs.common.security.RepoAccessGuard;
import com.dvcs.repository.dto.RepoDto;
import com.dvcs.repository.service.RepoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for user profiles and user-related listings.
 */
@Tag(name = "Users", description = "User profile and repository listings")
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final RepoService repoService;

    public UserController(UserService userService, RepoService repoService) {
        this.userService = userService;
        this.repoService = repoService;
    }

    /**
     * Returns a user's public profile.
     *
     * @param username the username of the user
     * @return HTTP 200 with the user profile DTO
     */
    @Operation(summary = "Get user profile information")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "User profile returned"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/{username}")
    public ResponseEntity<UserProfileDto> getProfile(@PathVariable String username) {
        UserProfileDto profile = userService.getProfile(username);
        return ResponseEntity.ok(profile);
    }

    /**
     * Returns a list of repositories owned by the user, filtered by visibility for the requester.
     *
     * @param username       the username of the profile owner
     * @param authentication the current authentication
     * @return HTTP 200 with the list of repository DTOs
     */
    @Operation(summary = "Get user's repositories")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of repositories returned"),
        @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/{username}/repos")
    public ResponseEntity<List<RepoDto>> getUserRepos(
            @PathVariable String username,
            Authentication authentication) {

        User user = RepoAccessGuard.extractUser(authentication);
        Long requesterId = user != null ? user.getId() : null;

        List<RepoDto> repos = repoService.getUserRepos(requesterId, username);
        return ResponseEntity.ok(repos);
    }
}
