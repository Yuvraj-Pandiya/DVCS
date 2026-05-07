package com.dvcs.search;

import com.dvcs.repository.service.GitObjectReaderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Test-only implementation of {@link GitObjectReaderService} that reads
 * blob content directly from the local filesystem.
 *
 * <p>This avoids the need to include {@code backend-git} as a test dependency,
 * which would cause entity name conflicts with {@code backend-core}.
 *
 * <p>Only {@link #readBlobContent} is implemented; other methods throw
 * {@link UnsupportedOperationException} since they are not needed for search tests.
 */
@Service
@ConditionalOnMissingBean(GitObjectReaderService.class)
public class TestGitObjectReaderService implements GitObjectReaderService {

    private final Path storageRoot;

    public TestGitObjectReaderService(
            @Value("${storage.root:./data}") String storageRoot) {
        this.storageRoot = Paths.get(storageRoot);
    }

    @Override
    public String getCommitTreeSha(String repoId, String commitSha) throws IOException {
        throw new UnsupportedOperationException("Not implemented in test stub");
    }

    @Override
    public List<String> getCommitParentShas(String repoId, String commitSha) throws IOException {
        throw new UnsupportedOperationException("Not implemented in test stub");
    }

    @Override
    public String getCommitMessage(String repoId, String commitSha) throws IOException {
        throw new UnsupportedOperationException("Not implemented in test stub");
    }

    @Override
    public List<TreeEntryInfo> listTreeEntries(String repoId, String treeSha) throws IOException {
        throw new UnsupportedOperationException("Not implemented in test stub");
    }

    @Override
    public long getBlobContentSize(String repoId, String blobSha) throws IOException {
        return readBlobContent(repoId, blobSha).length;
    }

    /**
     * Reads blob content from the local filesystem.
     *
     * <p>The blob is stored at:
     * {@code {storageRoot}/{repoId}/objects/{sha[0..1]}/{sha[2..]}}
     *
     * <p>The stored bytes include the blob header ({@code "blob {size}\0"}).
     * This method strips the header and returns only the raw content bytes.
     */
    @Override
    public byte[] readBlobContent(String repoId, String blobSha) throws IOException {
        String prefix = blobSha.substring(0, 2);
        String rest = blobSha.substring(2);
        Path objectPath = storageRoot.resolve(repoId).resolve("objects")
                .resolve(prefix).resolve(rest);

        if (!Files.exists(objectPath)) {
            throw new IOException("Object not found: " + blobSha);
        }

        byte[] rawBytes = Files.readAllBytes(objectPath);

        // Strip the blob header: "blob {size}\0"
        // Find the null byte that separates header from content
        for (int i = 0; i < rawBytes.length; i++) {
            if (rawBytes[i] == 0) {
                // Content starts after the null byte
                int contentLength = rawBytes.length - i - 1;
                byte[] content = new byte[contentLength];
                System.arraycopy(rawBytes, i + 1, content, 0, contentLength);
                return content;
            }
        }

        // No null byte found — return raw bytes as-is
        return rawBytes;
    }
}
