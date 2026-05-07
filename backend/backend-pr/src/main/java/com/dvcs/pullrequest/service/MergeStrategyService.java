package com.dvcs.pullrequest.service;

import com.dvcs.common.audit.Audited;
import com.dvcs.common.exception.EntityNotFoundException;
import com.dvcs.common.exception.MergeConflictException;
import com.dvcs.diff.algorithm.MergeResult;
import com.dvcs.diff.algorithm.ThreeWayMerge;
import com.dvcs.git.object.BlobObject;
import com.dvcs.git.object.CommitObject;
import com.dvcs.git.object.TreeEntry;
import com.dvcs.git.object.TreeObject;
import com.dvcs.git.storage.ObjectStoreService;
import com.dvcs.git.transport.UploadPackServiceImpl;
import com.dvcs.pullrequest.domain.PullRequest;
import com.dvcs.pullrequest.repository.PullRequestRepository;
import com.dvcs.repository.domain.Branch;
import com.dvcs.repository.repository.BranchRepository;
import com.dvcs.repository.repository.CommitMetaRepository;
import com.dvcs.repository.domain.CommitMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service implementing the three merge strategies for pull requests:
 * merge commit, squash merge, and rebase merge.
 *
 * <p>All strategies:
 * <ol>
 *   <li>Resolve head and base branch tip SHAs</li>
 *   <li>Find merge base (LCA) — simplified: uses base branch tip as merge base</li>
 *   <li>Run three-way merge on changed files</li>
 *   <li>If conflicts → throw {@link MergeConflictException}</li>
 *   <li>Write new objects via {@link ObjectStoreService}</li>
 *   <li>Update {@code branches.head_sha} for base branch</li>
 *   <li>Mark PR as merged</li>
 *   <li>Trigger webhook + notification (stubbed)</li>
 * </ol>
 *
 * <p>Requirement 10.6: MergeStrategyService.
 */
@Service
@Transactional
public class MergeStrategyService {

    private static final Logger log = LoggerFactory.getLogger(MergeStrategyService.class);

    private final ObjectStoreService objectStoreService;
    private final BranchRepository branchRepository;
    private final PullRequestRepository pullRequestRepository;
    private final CommitMetaRepository commitMetaRepository;

    /**
     * Optional notification port — injected if available.
     */
    @Autowired(required = false)
    private NotificationPort notificationPort;

    public MergeStrategyService(ObjectStoreService objectStoreService,
                                 BranchRepository branchRepository,
                                 PullRequestRepository pullRequestRepository,
                                 CommitMetaRepository commitMetaRepository) {
        this.objectStoreService = objectStoreService;
        this.branchRepository = branchRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.commitMetaRepository = commitMetaRepository;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Finds the lowest common ancestor (LCA) of two commits.
     *
     * <p><strong>Simplification note:</strong> The {@code commits_meta} table does not
     * store parent SHA information. Parent info is embedded in the {@link CommitObject}
     * stored in the object store. A full BFS traversal would require reading each commit
     * object from the store. For now, this method returns {@code sha1} (the base branch tip)
     * as the merge base, which is correct for the common case where the base branch has
     * not diverged from the feature branch's fork point.
     *
     * @param repoId the repository ID string
     * @param sha1   the first commit SHA (typically the base branch tip)
     * @param sha2   the second commit SHA (typically the head branch tip)
     * @return the merge base SHA (simplified: returns sha1)
     */
    public String findLCA(Long repoId, String sha1, String sha2) {
        // TODO: implement full BFS traversal using CommitObject.getParentShas()
        // For now, return sha1 (base branch tip) as the merge base.
        // This is correct when the base branch has not advanced since the feature branch was created.
        log.debug("findLCA(repoId={}, sha1={}, sha2={}) — returning sha1 as simplified merge base",
                repoId, sha1, sha2);
        return sha1;
    }

    /**
     * Performs a merge-commit merge strategy.
     *
     * <p>Creates a new merge commit with two parents (base tip and head tip).
     * Preserves full history from both branches.
     *
     * @param repoId      the repository ID
     * @param pr          the pull request to merge
     * @param requesterId the ID of the user requesting the merge
     * @throws EntityNotFoundException if a branch is not found
     * @throws MergeConflictException  if the merge has unresolvable conflicts
     */
    @Audited(action = "merge_pr", resourceType = "pull_request")
    public void mergeCommit(Long repoId, PullRequest pr, Long requesterId) {
        log.info("Performing merge-commit for PR #{} in repo {}", pr.getNumber(), repoId);

        Branch baseBranch = resolveBranch(repoId, pr.getBaseBranch());
        Branch headBranch = resolveBranch(repoId, pr.getHeadBranch());

        String baseSha = baseBranch.getHeadSha();
        String headSha = headBranch.getHeadSha();
        String mergeSha = findLCA(repoId, baseSha, headSha);

        // Perform three-way merge
        String mergedTreeSha = performThreeWayMerge(repoId, mergeSha, baseSha, headSha);

        // Create merge commit with two parents
        String commitMessage = "Merge pull request #" + pr.getNumber() + " from " + pr.getHeadBranch();
        String newCommitSha = createCommit(repoId, mergedTreeSha,
                List.of(baseSha, headSha), commitMessage, requesterId);

        // Update base branch and mark PR merged
        finalizeMerge(repoId, pr, baseBranch, newCommitSha, requesterId);
    }

    /**
     * Performs a squash merge strategy.
     *
     * <p>Creates a single new commit on the base branch containing all changes
     * from the head branch. The head branch's individual commit history is not
     * preserved in the base branch.
     *
     * @param repoId      the repository ID
     * @param pr          the pull request to merge
     * @param requesterId the ID of the user requesting the merge
     * @throws EntityNotFoundException if a branch is not found
     * @throws MergeConflictException  if the merge has unresolvable conflicts
     */
    @Audited(action = "merge_pr", resourceType = "pull_request")
    public void squashMerge(Long repoId, PullRequest pr, Long requesterId) {
        log.info("Performing squash-merge for PR #{} in repo {}", pr.getNumber(), repoId);

        Branch baseBranch = resolveBranch(repoId, pr.getBaseBranch());
        Branch headBranch = resolveBranch(repoId, pr.getHeadBranch());

        String baseSha = baseBranch.getHeadSha();
        String headSha = headBranch.getHeadSha();
        String mergeSha = findLCA(repoId, baseSha, headSha);

        // Perform three-way merge
        String mergedTreeSha = performThreeWayMerge(repoId, mergeSha, baseSha, headSha);

        // Create squash commit with single parent (base tip only)
        String commitMessage = "Squash merge pull request #" + pr.getNumber()
                + " from " + pr.getHeadBranch() + "\n\n"
                + (pr.getBody() != null ? pr.getBody() : "");
        String newCommitSha = createCommit(repoId, mergedTreeSha,
                List.of(baseSha), commitMessage, requesterId);

        // Update base branch and mark PR merged
        finalizeMerge(repoId, pr, baseBranch, newCommitSha, requesterId);
    }

    /**
     * Performs a rebase merge strategy.
     *
     * <p>Replays each commit from the head branch on top of the base branch,
     * creating a linear history. For simplicity, this is implemented as a
     * merge commit with a rebase note in the commit message.
     *
     * <p><strong>Simplification note:</strong> A full rebase would require reading
     * each commit from the head branch's history and replaying them individually.
     * This requires BFS traversal of the commit graph, which is not fully implemented
     * (see {@link #findLCA}). The current implementation creates a single commit
     * with the merged tree, equivalent to a squash merge with a rebase label.
     *
     * @param repoId      the repository ID
     * @param pr          the pull request to merge
     * @param requesterId the ID of the user requesting the merge
     * @throws EntityNotFoundException if a branch is not found
     * @throws MergeConflictException  if the merge has unresolvable conflicts
     */
    @Audited(action = "merge_pr", resourceType = "pull_request")
    public void rebaseMerge(Long repoId, PullRequest pr, Long requesterId) {
        log.info("Performing rebase-merge for PR #{} in repo {}", pr.getNumber(), repoId);

        Branch baseBranch = resolveBranch(repoId, pr.getBaseBranch());
        Branch headBranch = resolveBranch(repoId, pr.getHeadBranch());

        String baseSha = baseBranch.getHeadSha();
        String headSha = headBranch.getHeadSha();
        String mergeSha = findLCA(repoId, baseSha, headSha);

        // Perform three-way merge
        String mergedTreeSha = performThreeWayMerge(repoId, mergeSha, baseSha, headSha);

        // Create rebase commit with single parent (linear history)
        // TODO: implement proper rebase by replaying individual commits
        String commitMessage = "Rebase and merge pull request #" + pr.getNumber()
                + " from " + pr.getHeadBranch();
        String newCommitSha = createCommit(repoId, mergedTreeSha,
                List.of(baseSha), commitMessage, requesterId);

        // Update base branch and mark PR merged
        finalizeMerge(repoId, pr, baseBranch, newCommitSha, requesterId);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Resolves a branch by repository ID and name.
     *
     * @param repoId     the repository ID
     * @param branchName the branch name
     * @return the {@link Branch} entity
     * @throws EntityNotFoundException if the branch does not exist
     */
    private Branch resolveBranch(Long repoId, String branchName) {
        return branchRepository.findByRepoIdAndName(repoId, branchName)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Branch '" + branchName + "' not found in repository " + repoId));
    }

    /**
     * Performs a three-way merge between the merge base, base tip, and head tip.
     *
     * <p>Reads the tree objects for each commit, identifies changed files, and
     * runs {@link ThreeWayMerge} on each changed file. If any file has conflicts,
     * throws {@link MergeConflictException}.
     *
     * <p>For simplicity, this implementation reads the root tree entries and merges
     * files at the top level. Nested directory merging follows the same pattern.
     *
     * @param repoId   the repository ID string
     * @param baseSha  the merge base commit SHA
     * @param oursSha  the base branch tip commit SHA
     * @param theirsSha the head branch tip commit SHA
     * @return the SHA of the merged tree object
     * @throws MergeConflictException if any file has unresolvable conflicts
     */
    private String performThreeWayMerge(Long repoId, String baseSha, String oursSha, String theirsSha) {
        String repoIdStr = repoId.toString();
        try {
            // Read tree SHAs for each commit
            String baseTreeSha = getCommitTreeSha(repoIdStr, baseSha);
            String oursTreeSha = getCommitTreeSha(repoIdStr, oursSha);
            String theirsTreeSha = getCommitTreeSha(repoIdStr, theirsSha);

            // Merge the trees recursively
            return mergeTree(repoIdStr, baseTreeSha, oursTreeSha, theirsTreeSha);

        } catch (MergeConflictException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error during three-way merge for repo {}: {}", repoId, e.getMessage(), e);
            throw new MergeConflictException("Merge failed due to an error: " + e.getMessage());
        }
    }

    /**
     * Merges two tree objects relative to a base tree, returning the SHA of the merged tree.
     *
     * @param repoId       the repository ID string
     * @param baseTreeSha  the base tree SHA
     * @param oursTreeSha  the ours tree SHA
     * @param theirsTreeSha the theirs tree SHA
     * @return the SHA of the merged tree
     * @throws IOException            if an object cannot be read
     * @throws MergeConflictException if any file has conflicts
     */
    private String mergeTree(String repoId, String baseTreeSha, String oursTreeSha, String theirsTreeSha)
            throws IOException {
        List<TreeEntry> baseEntries = readTreeEntries(repoId, baseTreeSha);
        List<TreeEntry> oursEntries = readTreeEntries(repoId, oursTreeSha);
        List<TreeEntry> theirsEntries = readTreeEntries(repoId, theirsTreeSha);

        List<TreeEntry> mergedEntries = new ArrayList<>();
        List<String> conflictFiles = new ArrayList<>();

        // Build maps for quick lookup
        java.util.Map<String, TreeEntry> baseMap = toMap(baseEntries);
        java.util.Map<String, TreeEntry> oursMap = toMap(oursEntries);
        java.util.Map<String, TreeEntry> theirsMap = toMap(theirsEntries);

        // Collect all entry names
        java.util.Set<String> allNames = new java.util.LinkedHashSet<>();
        allNames.addAll(baseMap.keySet());
        allNames.addAll(oursMap.keySet());
        allNames.addAll(theirsMap.keySet());

        for (String name : allNames) {
            TreeEntry baseEntry = baseMap.get(name);
            TreeEntry oursEntry = oursMap.get(name);
            TreeEntry theirsEntry = theirsMap.get(name);

            if (oursEntry == null && theirsEntry == null) {
                // Deleted in both — skip
                continue;
            } else if (oursEntry == null) {
                // Deleted in ours, kept in theirs — if base had it, it's deleted; otherwise add theirs
                if (baseEntry == null) {
                    mergedEntries.add(theirsEntry);
                }
                // else: deleted in ours, existed in base — keep deletion
            } else if (theirsEntry == null) {
                // Deleted in theirs, kept in ours — if base had it, it's deleted; otherwise add ours
                if (baseEntry == null) {
                    mergedEntries.add(oursEntry);
                }
                // else: deleted in theirs, existed in base — keep deletion
            } else if (oursEntry.sha().equals(theirsEntry.sha())) {
                // Same in both — use either
                mergedEntries.add(oursEntry);
            } else if (baseEntry != null && oursEntry.sha().equals(baseEntry.sha())) {
                // Only theirs changed — use theirs
                mergedEntries.add(theirsEntry);
            } else if (baseEntry != null && theirsEntry.sha().equals(baseEntry.sha())) {
                // Only ours changed — use ours
                mergedEntries.add(oursEntry);
            } else {
                // Both changed — need content merge for blobs, recursive merge for trees
                boolean isTree = oursEntry.mode().startsWith("04");
                if (isTree) {
                    // Recursive tree merge
                    String baseSubTree = baseEntry != null ? baseEntry.sha() : oursEntry.sha();
                    String mergedSubTreeSha = mergeTree(repoId, baseSubTree, oursEntry.sha(), theirsEntry.sha());
                    mergedEntries.add(new TreeEntry(oursEntry.mode(), name, mergedSubTreeSha));
                } else {
                    // Blob merge
                    String baseContent = baseEntry != null
                            ? readBlobAsString(repoId, baseEntry.sha())
                            : "";
                    String oursContent = readBlobAsString(repoId, oursEntry.sha());
                    String theirsContent = readBlobAsString(repoId, theirsEntry.sha());

                    String[] baseLines = splitLines(baseContent);
                    String[] oursLines = splitLines(oursContent);
                    String[] theirsLines = splitLines(theirsContent);

                    MergeResult result = ThreeWayMerge.merge(baseLines, oursLines, theirsLines);

                    if (result.hasConflicts()) {
                        conflictFiles.add(name);
                        // Still write the conflicted content so we can report it
                        String conflictedContent = String.join("\n", result.getMergedLines());
                        BlobObject conflictBlob = new BlobObject(
                                conflictedContent.getBytes(StandardCharsets.UTF_8));
                        try {
                            objectStoreService.writeObject(repoId, conflictBlob);
                        } catch (IOException ex) {
                            log.warn("Failed to write conflict blob for {}: {}", name, ex.getMessage());
                        }
                    } else {
                        String mergedContent = String.join("\n", result.getMergedLines());
                        BlobObject mergedBlob = new BlobObject(
                                mergedContent.getBytes(StandardCharsets.UTF_8));
                        String mergedBlobSha = objectStoreService.writeObject(repoId, mergedBlob);
                        mergedEntries.add(new TreeEntry(oursEntry.mode(), name, mergedBlobSha));
                    }
                }
            }
        }

        if (!conflictFiles.isEmpty()) {
            throw new MergeConflictException(
                    "Merge conflicts detected in: " + String.join(", ", conflictFiles));
        }

        // Write merged tree
        TreeObject mergedTree = new TreeObject(mergedEntries);
        return objectStoreService.writeObject(repoId, mergedTree);
    }

    /**
     * Creates a new commit object and persists it.
     *
     * @param repoId        the repository ID
     * @param treeSha       the tree SHA for the commit
     * @param parentShas    the parent commit SHAs
     * @param message       the commit message
     * @param requesterId   the ID of the committer
     * @return the new commit SHA
     */
    private String createCommit(Long repoId, String treeSha, List<String> parentShas,
                                 String message, Long requesterId) {
        String repoIdStr = repoId.toString();
        long now = Instant.now().getEpochSecond();

        // Use a generic committer identity for merge commits
        // In a full implementation, this would use the requester's name/email
        CommitObject commit = new CommitObject(
                treeSha,
                parentShas,
                "DVCS Platform",
                "noreply@dvcs.local",
                now,
                "DVCS Platform",
                "noreply@dvcs.local",
                now,
                message
        );

        try {
            String commitSha = objectStoreService.writeObject(repoIdStr, commit);

            // Record in commits_meta
            CommitMeta meta = CommitMeta.builder()
                    .repoId(repoId)
                    .sha(commitSha)
                    .authorId(requesterId)
                    .message(message)
                    .authoredAt(OffsetDateTime.now())
                    .committedAt(OffsetDateTime.now())
                    .build();
            commitMetaRepository.save(meta);

            return commitSha;
        } catch (IOException e) {
            throw new MergeConflictException("Failed to write merge commit: " + e.getMessage());
        }
    }

    /**
     * Finalizes a merge by updating the base branch head SHA, marking the PR as merged,
     * and triggering notifications.
     *
     * @param repoId      the repository ID
     * @param pr          the pull request
     * @param baseBranch  the base branch entity
     * @param newCommitSha the new commit SHA to set as the branch head
     * @param requesterId the ID of the user who performed the merge
     */
    private void finalizeMerge(Long repoId, PullRequest pr, Branch baseBranch,
                                String newCommitSha, Long requesterId) {
        // Update base branch head SHA
        baseBranch.setHeadSha(newCommitSha);
        branchRepository.save(baseBranch);

        // Mark PR as merged
        pr.setStatus("merged");
        pr.setMergedAt(OffsetDateTime.now());
        pullRequestRepository.save(pr);

        // Stub: trigger webhook event
        log.info("Webhook triggered for merge of PR #{} in repo {} (stub)", pr.getNumber(), repoId);

        // Notify PR author
        if (notificationPort != null && !requesterId.equals(pr.getAuthorId())) {
            try {
                notificationPort.createNotification(pr.getAuthorId(), "pull_request", pr.getId(), "merged");
            } catch (Exception e) {
                log.warn("Failed to send merge notification for PR #{}: {}", pr.getNumber(), e.getMessage());
            }
        }

        log.info("PR #{} merged successfully in repo {}. New base branch head: {}",
                pr.getNumber(), repoId, newCommitSha);
    }

    /**
     * Reads the tree SHA from a commit object.
     *
     * @param repoId    the repository ID string
     * @param commitSha the commit SHA
     * @return the tree SHA
     * @throws IOException if the commit cannot be read
     */
    private String getCommitTreeSha(String repoId, String commitSha) throws IOException {
        byte[] raw = objectStoreService.readObject(repoId, commitSha);
        CommitObject commit = UploadPackServiceImpl.parseCommit(raw);
        return commit.getTreeSha();
    }

    /**
     * Reads tree entries from a tree object.
     *
     * @param repoId  the repository ID string
     * @param treeSha the tree SHA
     * @return list of tree entries
     * @throws IOException if the tree cannot be read
     */
    private List<TreeEntry> readTreeEntries(String repoId, String treeSha) throws IOException {
        byte[] raw = objectStoreService.readObject(repoId, treeSha);
        TreeObject tree = UploadPackServiceImpl.parseTree(raw);
        return tree.getEntries();
    }

    /**
     * Reads a blob object and returns its content as a string.
     *
     * @param repoId  the repository ID string
     * @param blobSha the blob SHA
     * @return the blob content as a UTF-8 string
     * @throws IOException if the blob cannot be read
     */
    private String readBlobAsString(String repoId, String blobSha) throws IOException {
        byte[] raw = objectStoreService.readObject(repoId, blobSha);
        // Strip the "blob {size}\0" header
        for (int i = 0; i < raw.length; i++) {
            if (raw[i] == 0) {
                int contentLen = raw.length - i - 1;
                byte[] content = new byte[contentLen];
                System.arraycopy(raw, i + 1, content, 0, contentLen);
                return new String(content, StandardCharsets.UTF_8);
            }
        }
        return new String(raw, StandardCharsets.UTF_8);
    }

    /**
     * Converts a list of tree entries to a name-keyed map.
     *
     * @param entries the list of tree entries
     * @return map from entry name to entry
     */
    private java.util.Map<String, TreeEntry> toMap(List<TreeEntry> entries) {
        java.util.Map<String, TreeEntry> map = new java.util.LinkedHashMap<>();
        for (TreeEntry entry : entries) {
            map.put(entry.name(), entry);
        }
        return map;
    }

    /**
     * Splits a string into lines.
     *
     * @param content the string to split
     * @return array of lines
     */
    private static String[] splitLines(String content) {
        if (content == null || content.isEmpty()) {
            return new String[0];
        }
        return content.split("\n", -1);
    }
}
