package com.dvcs.repository.service;

import java.io.IOException;
import java.util.List;

/**
 * Service interface for reading and parsing Git objects from the object store.
 *
 * <p>This interface is defined in {@code backend-core} and implemented in
 * {@code backend-git} to avoid a circular module dependency. Controllers in
 * {@code backend-core} use this interface to read commit and tree data without
 * directly depending on the Git object model classes.
 *
 * <p>Requirement 7: File Tree and Blob Retrieval.
 */
public interface GitObjectReaderService {

    /**
     * Reads a commit object and returns its tree SHA.
     *
     * @param repoId    the repository ID string
     * @param commitSha the commit SHA
     * @return the tree SHA referenced by the commit
     * @throws IOException             if the object cannot be read
     * @throws IllegalArgumentException if the bytes cannot be parsed as a commit
     */
    String getCommitTreeSha(String repoId, String commitSha) throws IOException;

    /**
     * Reads a commit object and returns its parent SHAs.
     *
     * @param repoId    the repository ID string
     * @param commitSha the commit SHA
     * @return list of parent commit SHAs (empty for root commits)
     * @throws IOException             if the object cannot be read
     * @throws IllegalArgumentException if the bytes cannot be parsed as a commit
     */
    List<String> getCommitParentShas(String repoId, String commitSha) throws IOException;

    /**
     * Reads a commit object and returns its message.
     *
     * @param repoId    the repository ID string
     * @param commitSha the commit SHA
     * @return the commit message
     * @throws IOException             if the object cannot be read
     * @throws IllegalArgumentException if the bytes cannot be parsed as a commit
     */
    String getCommitMessage(String repoId, String commitSha) throws IOException;

    /**
     * Lists the entries of a tree object.
     *
     * <p>Each entry is represented as a {@link TreeEntryInfo} with name, mode, and SHA.
     *
     * @param repoId  the repository ID string
     * @param treeSha the tree SHA
     * @return list of tree entries
     * @throws IOException             if the object cannot be read
     * @throws IllegalArgumentException if the bytes cannot be parsed as a tree
     */
    List<TreeEntryInfo> listTreeEntries(String repoId, String treeSha) throws IOException;

    /**
     * Returns the size of a blob's content (excluding the header).
     *
     * @param repoId  the repository ID string
     * @param blobSha the blob SHA
     * @return the content size in bytes
     * @throws IOException if the object cannot be read
     */
    long getBlobContentSize(String repoId, String blobSha) throws IOException;

    /**
     * Reads a blob object and returns its raw content bytes (excluding the header).
     *
     * @param repoId  the repository ID string
     * @param blobSha the blob SHA
     * @return the raw content bytes
     * @throws IOException if the object cannot be read
     */
    byte[] readBlobContent(String repoId, String blobSha) throws IOException;

    /**
     * Immutable record representing a single tree entry.
     *
     * @param name the entry name (file or directory name)
     * @param mode the Unix file mode string (e.g. {@code "100644"} or {@code "040000"})
     * @param sha  the 64-character hex SHA of the referenced object
     */
    record TreeEntryInfo(String name, String mode, String sha) {

        /** Returns {@code true} if this entry is a directory (tree). */
        public boolean isTree() {
            return "040000".equals(mode);
        }

        /** Returns {@code "tree"} for directories, {@code "blob"} for files. */
        public String type() {
            return isTree() ? "tree" : "blob";
        }
    }
}
