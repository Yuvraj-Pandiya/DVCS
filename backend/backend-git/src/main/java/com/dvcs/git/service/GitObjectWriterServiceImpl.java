package com.dvcs.git.service;

import com.dvcs.git.object.BlobObject;
import com.dvcs.git.object.CommitObject;
import com.dvcs.git.object.TreeEntry;
import com.dvcs.git.object.TreeObject;
import com.dvcs.git.storage.ObjectStoreService;
import com.dvcs.repository.service.GitObjectWriterService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * Implementation of {@link GitObjectWriterService} that writes Git objects
 * using {@link ObjectStoreService}.
 *
 * <p>Requirement 4: Git Object Storage Engine.
 */
@Service
public class GitObjectWriterServiceImpl implements GitObjectWriterService {

    private final ObjectStoreService objectStoreService;

    public GitObjectWriterServiceImpl(ObjectStoreService objectStoreService) {
        this.objectStoreService = objectStoreService;
    }

    @Override
    public String initializeRepo(String repoId, String ownerUsername, String repoName) throws IOException {
        // 1. Create initial README.md blob
        String readmeContent = "# " + repoName + "\n\nInitial repository creation.\n";
        BlobObject readmeBlob = new BlobObject(readmeContent.getBytes(StandardCharsets.UTF_8));
        String blobSha = objectStoreService.writeObject(repoId, readmeBlob);

        // 2. Create root tree containing README.md
        TreeEntry readmeEntry = new TreeEntry("100644", "README.md", blobSha);
        TreeObject rootTree = new TreeObject(List.of(readmeEntry));
        String treeSha = objectStoreService.writeObject(repoId, rootTree);

        // 3. Create initial commit
        long now = System.currentTimeMillis() / 1000;
        CommitObject initialCommit = new CommitObject(
                treeSha,
                Collections.emptyList(), // no parents
                ownerUsername,
                ownerUsername + "@localhost",
                now,
                ownerUsername,
                ownerUsername + "@localhost",
                now,
                "Initial commit"
        );
        return objectStoreService.writeObject(repoId, initialCommit);
    }
}
