package com.dvcs.diff.service;

import com.dvcs.common.exception.EntityNotFoundException;
import com.dvcs.diff.algorithm.BinaryDetector;
import com.dvcs.diff.algorithm.MergeResult;
import com.dvcs.diff.algorithm.MyersDiff;
import com.dvcs.diff.algorithm.ThreeWayMerge;
import com.dvcs.diff.model.DiffHunk;
import com.dvcs.repository.service.GitObjectReaderService;
import com.dvcs.repository.service.GitObjectReaderService.TreeEntryInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Service that computes diffs and three-way merges between Git objects.
 *
 * <p>This service bridges the diff algorithm layer ({@link MyersDiff},
 * {@link ThreeWayMerge}, {@link BinaryDetector}) with the Git object store
 * via {@link GitObjectReaderService}.
 *
 * <h2>Operations</h2>
 * <ul>
 *   <li>{@link #textDiff} — line-by-line unified diff between two commit SHAs at a path</li>
 *   <li>{@link #binaryDiff} — size-delta diff for binary files</li>
 *   <li>{@link #threeWayMerge} — three-way merge of base, ours, and theirs commits at a path</li>
 * </ul>
 *
 * <p>Requirement 9.7: Diff Engine — DiffService.
 */
@Service
public class DiffService {

    private static final Logger log = LoggerFactory.getLogger(DiffService.class);

    private final GitObjectReaderService gitObjectReaderService;

    /**
     * Constructs a {@code DiffService}.
     *
     * @param gitObjectReaderService the service used to read Git objects; must not be {@code null}
     */
    public DiffService(GitObjectReaderService gitObjectReaderService) {
        this.gitObjectReaderService = Objects.requireNonNull(
                gitObjectReaderService, "gitObjectReaderService must not be null");
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Computes a line-by-line unified diff for a text file between two commits.
     *
     * <p>Resolves the blob SHA for {@code filePath} in each commit, reads the
     * content, splits into lines, and runs {@link MyersDiff#diff}.
     *
     * @param repoId   the repository ID string
     * @param baseSha  the base commit SHA
     * @param headSha  the head commit SHA
     * @param filePath the slash-separated file path within the repository
     * @return an ordered list of {@link DiffHunk}s; empty if the file is identical
     * @throws EntityNotFoundException if the file path does not exist in either commit
     * @throws IOException             if an object cannot be read from the store
     * @throws NullPointerException    if any argument is {@code null}
     */
    public List<DiffHunk> textDiff(String repoId, String baseSha, String headSha, String filePath)
            throws IOException {
        Objects.requireNonNull(repoId,   "repoId must not be null");
        Objects.requireNonNull(baseSha,  "baseSha must not be null");
        Objects.requireNonNull(headSha,  "headSha must not be null");
        Objects.requireNonNull(filePath, "filePath must not be null");

        byte[] baseContent = readFileContent(repoId, baseSha, filePath);
        byte[] headContent = readFileContent(repoId, headSha, filePath);

        String[] baseLines = splitLines(baseContent);
        String[] headLines = splitLines(headContent);

        return MyersDiff.diff(baseLines, headLines);
    }

    /**
     * Computes a binary diff (size delta) for a binary file between two commits.
     *
     * <p>Reads the blob sizes for {@code filePath} in each commit and returns
     * a {@link BinaryDiffResult} with the size delta.
     *
     * @param repoId   the repository ID string
     * @param baseSha  the base commit SHA
     * @param headSha  the head commit SHA
     * @param filePath the slash-separated file path within the repository
     * @return a {@link BinaryDiffResult} with base and head sizes
     * @throws EntityNotFoundException if the file path does not exist in either commit
     * @throws IOException             if an object cannot be read from the store
     * @throws NullPointerException    if any argument is {@code null}
     */
    public BinaryDiffResult binaryDiff(String repoId, String baseSha, String headSha, String filePath)
            throws IOException {
        Objects.requireNonNull(repoId,   "repoId must not be null");
        Objects.requireNonNull(baseSha,  "baseSha must not be null");
        Objects.requireNonNull(headSha,  "headSha must not be null");
        Objects.requireNonNull(filePath, "filePath must not be null");

        String baseBlobSha = resolveBlobSha(repoId, baseSha, filePath);
        String headBlobSha = resolveBlobSha(repoId, headSha, filePath);

        long baseSize = gitObjectReaderService.getBlobContentSize(repoId, baseBlobSha);
        long headSize = gitObjectReaderService.getBlobContentSize(repoId, headBlobSha);

        return new BinaryDiffResult(true, baseSize, headSize);
    }

    /**
     * Performs a three-way merge of a file across three commits.
     *
     * <p>Reads the file content at {@code baseSha}, {@code oursSha}, and
     * {@code theirsSha}, then delegates to {@link ThreeWayMerge#merge}.
     *
     * @param repoId    the repository ID string
     * @param baseSha   the common ancestor commit SHA
     * @param oursSha   the "ours" commit SHA
     * @param theirsSha the "theirs" commit SHA
     * @param filePath  the slash-separated file path within the repository
     * @return a {@link MergeResult} with merged lines and a conflict flag
     * @throws EntityNotFoundException if the file path does not exist in any commit
     * @throws IOException             if an object cannot be read from the store
     * @throws NullPointerException    if any argument is {@code null}
     */
    public MergeResult threeWayMerge(String repoId, String baseSha, String oursSha,
                                     String theirsSha, String filePath) throws IOException {
        Objects.requireNonNull(repoId,    "repoId must not be null");
        Objects.requireNonNull(baseSha,   "baseSha must not be null");
        Objects.requireNonNull(oursSha,   "oursSha must not be null");
        Objects.requireNonNull(theirsSha, "theirsSha must not be null");
        Objects.requireNonNull(filePath,  "filePath must not be null");

        byte[] baseContent   = readFileContent(repoId, baseSha,   filePath);
        byte[] oursContent   = readFileContent(repoId, oursSha,   filePath);
        byte[] theirsContent = readFileContent(repoId, theirsSha, filePath);

        String[] baseLines   = splitLines(baseContent);
        String[] oursLines   = splitLines(oursContent);
        String[] theirsLines = splitLines(theirsContent);

        return ThreeWayMerge.merge(baseLines, oursLines, theirsLines);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Reads the raw content bytes of a file at the given commit SHA and path.
     *
     * @param repoId    the repository ID string
     * @param commitSha the commit SHA
     * @param filePath  the slash-separated file path
     * @return the raw content bytes
     * @throws EntityNotFoundException if the path does not exist in the commit
     * @throws IOException             if an object cannot be read
     */
    private byte[] readFileContent(String repoId, String commitSha, String filePath)
            throws IOException {
        String blobSha = resolveBlobSha(repoId, commitSha, filePath);
        return gitObjectReaderService.readBlobContent(repoId, blobSha);
    }

    /**
     * Resolves a file path within a commit's tree to the blob SHA.
     *
     * @param repoId    the repository ID string
     * @param commitSha the commit SHA
     * @param filePath  the slash-separated file path
     * @return the blob SHA
     * @throws EntityNotFoundException if the path does not exist
     * @throws IOException             if an object cannot be read
     */
    private String resolveBlobSha(String repoId, String commitSha, String filePath)
            throws IOException {
        String treeSha;
        try {
            treeSha = gitObjectReaderService.getCommitTreeSha(repoId, commitSha);
        } catch (IOException e) {
            throw new EntityNotFoundException("Commit '" + commitSha + "' not found in repo '" + repoId + "'.");
        }

        String[] segments = filePath.split("/");
        String currentTreeSha = treeSha;

        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.isEmpty()) continue;

            List<TreeEntryInfo> entries;
            try {
                entries = gitObjectReaderService.listTreeEntries(repoId, currentTreeSha);
            } catch (IOException e) {
                throw new EntityNotFoundException(
                        "Tree not found at path segment '" + segment + "' in commit '" + commitSha + "'.");
            }

            TreeEntryInfo found = entries.stream()
                    .filter(e -> e.name().equals(segment))
                    .findFirst()
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Path '" + filePath + "' not found in commit '" + commitSha + "'."));

            if (i == segments.length - 1) {
                if (found.isTree()) {
                    throw new EntityNotFoundException(
                            "Path '" + filePath + "' is a directory, not a file.");
                }
                return found.sha();
            } else {
                if (!found.isTree()) {
                    throw new EntityNotFoundException(
                            "Path segment '" + segment + "' is a file, not a directory.");
                }
                currentTreeSha = found.sha();
            }
        }

        throw new EntityNotFoundException("Path '" + filePath + "' not found in commit '" + commitSha + "'.");
    }

    /**
     * Splits raw file content bytes into an array of lines.
     *
     * <p>The content is decoded as UTF-8. Lines are split on {@code \n}, with
     * trailing {@code \r} stripped from each line (handles CRLF line endings).
     * An empty file returns an empty array.
     *
     * @param content the raw file bytes
     * @return array of lines (without line terminators)
     */
    static String[] splitLines(byte[] content) {
        if (content == null || content.length == 0) {
            return new String[0];
        }
        String text = new String(content, StandardCharsets.UTF_8);
        // Remove a single trailing newline to avoid a spurious empty last line
        if (text.endsWith("\n")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.isEmpty()) {
            return new String[0];
        }
        String[] lines = text.split("\n", -1);
        // Strip trailing \r from each line (CRLF support)
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].endsWith("\r")) {
                lines[i] = lines[i].substring(0, lines[i].length() - 1);
            }
        }
        return lines;
    }
}
