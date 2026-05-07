package com.dvcs.repository.controller;

import com.dvcs.auth.domain.User;
import com.dvcs.common.exception.EntityNotFoundException;
import com.dvcs.common.security.RepoAccessGuard;
import com.dvcs.repository.domain.Repository;
import com.dvcs.repository.dto.BranchDto;
import com.dvcs.repository.dto.CreateBranchRequest;
import com.dvcs.repository.dto.ToggleProtectionRequest;
import com.dvcs.repository.repository.RepoRepository;
import com.dvcs.repository.service.BranchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for branch management operations.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /api/repos/{owner}/{repo}/branches — list branches</li>
 *   <li>POST /api/repos/{owner}/{repo}/branches — create branch</li>
 *   <li>DELETE /api/repos/{owner}/{repo}/branches/{name} — delete branch</li>
 *   <li>PATCH /api/repos/{owner}/{repo}/branches/{name}/protect — toggle protection</li>
 * </ul>
 */
@Tag(name = "Branches", description = "Branch management operations")
@RestController
@RequestMapping("/api/repos/{owner}/{repo}/branches")
public class BranchController {

    private final BranchService branchService;
    private final RepoRepository repoRepository;
    private final com.dvcs.auth.repository.UserRepository userRepository;

    public BranchController(BranchService branchService,
                             RepoRepository repoRepository,
                             com.dvcs.auth.repository.UserRepository userRepository) {
        this.branchService = branchService;
        this.repoRepository = repoRepository;
        this.userRepository = userRepository;
    }

    // -------------------------------------------------------------------------
    // GET /api/repos/{owner}/{repo}/branches
    // -------------------------------------------------------------------------

    /**
     * Lists all branches for a repository.
     *
     * @param owner the repository owner's username
     * @param repo  the repository name
     * @return HTTP 200 with list of branch DTOs
     */
    @Operation(summary = "List all branches for a repository")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Branch list returned"),
        @ApiResponse(responseCode = "404", description = "Repository not found")
    })
    @GetMapping
    public ResponseEntity<List<BranchDto>> listBranches(
            @PathVariable String owner,
            @PathVariable String repo) {

        Long repoId = resolveRepoId(owner, repo);
        List<BranchDto> branches = branchService.listBranches(repoId);
        return ResponseEntity.ok(branches);
    }

    // -------------------------------------------------------------------------
    // POST /api/repos/{owner}/{repo}/branches
    // -------------------------------------------------------------------------

    /**
     * Creates a new branch.
     *
     * @param owner          the repository owner's username
     * @param repo           the repository name
     * @param request        the creation request
     * @param authentication the current authentication
     * @return HTTP 201 with the created branch DTO
     */
    @Operation(summary = "Create a new branch from a source SHA")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Branch created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request payload"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Write access required"),
        @ApiResponse(responseCode = "404", description = "Repository or source SHA not found"),
        @ApiResponse(responseCode = "409", description = "Branch name already exists")
    })
    @PostMapping
    public ResponseEntity<BranchDto> createBranch(
            @PathVariable String owner,
            @PathVariable String repo,
            @Valid @RequestBody CreateBranchRequest request,
            Authentication authentication) {

        Long repoId = resolveRepoId(owner, repo);
        BranchDto dto = branchService.createBranch(repoId, request.name(), request.sourceSha());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    // -------------------------------------------------------------------------
    // DELETE /api/repos/{owner}/{repo}/branches/{name}
    // -------------------------------------------------------------------------

    /**
     * Deletes a branch. Protected branches cannot be deleted.
     *
     * @param owner          the repository owner's username
     * @param repo           the repository name
     * @param name           the branch name
     * @param authentication the current authentication
     * @return HTTP 204 No Content
     */
    @Operation(summary = "Delete a branch (protected branches cannot be deleted)")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Branch deleted successfully"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Branch is protected or write access required"),
        @ApiResponse(responseCode = "404", description = "Repository or branch not found")
    })
    @DeleteMapping("/{name}")
    public ResponseEntity<Void> deleteBranch(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable String name,
            Authentication authentication) {

        User user = RepoAccessGuard.extractUser(authentication);
        Long repoId = resolveRepoId(owner, repo);
        branchService.deleteBranch(repoId, name, user != null ? user.getId() : null);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // PATCH /api/repos/{owner}/{repo}/branches/{name}/protect
    // -------------------------------------------------------------------------

    /**
     * Toggles branch protection.
     *
     * @param owner          the repository owner's username
     * @param repo           the repository name
     * @param name           the branch name
     * @param request        the protection toggle request
     * @param authentication the current authentication
     * @return HTTP 200 with the updated branch DTO
     */
    @Operation(summary = "Toggle branch protection on or off")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Branch protection updated"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Owner access required"),
        @ApiResponse(responseCode = "404", description = "Repository or branch not found")
    })
    @PatchMapping("/{name}/protect")
    public ResponseEntity<BranchDto> toggleProtection(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable String name,
            @RequestBody ToggleProtectionRequest request,
            Authentication authentication) {

        User user = RepoAccessGuard.extractUser(authentication);
        Long repoId = resolveRepoId(owner, repo);
        BranchDto dto = branchService.toggleProtection(
                repoId, name, request.protect(), user.getId());
        return ResponseEntity.ok(dto);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Long resolveRepoId(String owner, String repoName) {
        com.dvcs.auth.domain.User ownerUser = userRepository.findByUsername(owner)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Repository '" + owner + "/" + repoName + "' not found."));

        Repository repository = repoRepository.findByOwnerIdAndName(ownerUser.getId(), repoName)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Repository '" + owner + "/" + repoName + "' not found."));

        return repository.getId();
    }
}
