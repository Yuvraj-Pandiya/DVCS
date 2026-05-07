package com.dvcs.git.service;

import com.dvcs.git.object.CommitObject;
import com.dvcs.git.object.TreeEntry;
import com.dvcs.git.object.TreeObject;
import com.dvcs.git.storage.ObjectStoreService;
import com.dvcs.git.transport.UploadPackServiceImpl;
import com.dvcs.repository.service.GitObjectReaderService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of {@link GitObjectReaderService} that reads and parses Git objects
 * from the {@link ObjectStoreService}.
 *
 * <p>This service bridges the {@code backend-core} controller layer with the
 * {@code backend-git} object model, avoiding a circular module dependency.
 *
 * <p>Requirement 7: File Tree and Blob Retrieval.
 */
@Service
public class GitObjectReaderServiceImpl implements GitObjectReaderService {

    private final ObjectStoreService objectStoreService;

    public GitObjectReaderServiceImpl(ObjectStoreService objectStoreService) {
        this.objectStoreService = objectStoreService;
    }

    // -------------------------------------------------------------------------
    // Commit operations
    // -------------------------------------------------------------------------

    @Override
    public String getCommitTreeSha(String repoId, String commitSha) throws IOException {
        byte[] raw = objectStoreService.readObject(repoId, commitSha);
        CommitObject commit = UploadPackServiceImpl.parseCommit(raw);
        return commit.getTreeSha();
    }

    @Override
    public List<String> getCommitParentShas(String repoId, String commitSha) throws IOException {
        byte[] raw = objectStoreService.readObject(repoId, commitSha);
        CommitObject commit = UploadPackServiceImpl.parseCommit(raw);
        return commit.getParentShas();
    }

    @Override
    public String getCommitMessage(String repoId, String commitSha) throws IOException {
        byte[] raw = objectStoreService.readObject(repoId, commitSha);
        CommitObject commit = UploadPackServiceImpl.parseCommit(raw);
        return commit.getMessage();
    }

    // -------------------------------------------------------------------------
    // Tree operations
    // -------------------------------------------------------------------------

    @Override
    public List<TreeEntryInfo> listTreeEntries(String repoId, String treeSha) throws IOException {
        byte[] raw = objectStoreService.readObject(repoId, treeSha);
        TreeObject tree = UploadPackServiceImpl.parseTree(raw);
        return tree.getEntries().stream()
                .map(e -> new TreeEntryInfo(e.name(), e.mode(), e.sha()))
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Blob operations
    // -------------------------------------------------------------------------

    @Override
    public long getBlobContentSize(String repoId, String blobSha) throws IOException {
        byte[] raw = objectStoreService.readObject(repoId, blobSha);
        byte[] content = extractBlobContent(raw);
        return content.length;
    }

    @Override
    public byte[] readBlobContent(String repoId, String blobSha) throws IOException {
        byte[] raw = objectStoreService.readObject(repoId, blobSha);
        return extractBlobContent(raw);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts the raw content bytes from a blob's serialized form by stripping
     * the {@code "blob {size}\0"} header.
     *
     * @param rawData the full serialized blob bytes
     * @return the content bytes after the NUL separator
     */
    private static byte[] extractBlobContent(byte[] rawData) {
        for (int i = 0; i < rawData.length; i++) {
            if (rawData[i] == 0) {
                int contentLen = rawData.length - i - 1;
                byte[] content = new byte[contentLen];
                System.arraycopy(rawData, i + 1, content, 0, contentLen);
                return content;
            }
        }
        return rawData;
    }
}
