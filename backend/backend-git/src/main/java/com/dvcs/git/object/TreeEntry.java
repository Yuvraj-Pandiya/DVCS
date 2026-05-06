package com.dvcs.git.object;

/**
 * Represents a single entry in a Git tree object.
 *
 * <p>Each entry describes one file or sub-directory within a tree, identified by
 * its Unix file mode, name, and the SHA-256 hash of the referenced object.
 *
 * <p>Requirement 4: Git Object Storage Engine — tree entry format.
 *
 * @param mode the Unix file-mode string (e.g. {@code "100644"} for a regular file,
 *             {@code "040000"} for a directory); must not be {@code null}
 * @param name the entry name (file or directory name); must not be {@code null}
 * @param sha  the 64-character lowercase hex SHA-256 digest of the referenced object;
 *             must not be {@code null}
 */
public record TreeEntry(String mode, String name, String sha) {

    /**
     * Compact canonical constructor — validates that no field is {@code null}.
     *
     * @throws NullPointerException if any parameter is {@code null}
     */
    public TreeEntry {
        java.util.Objects.requireNonNull(mode, "mode must not be null");
        java.util.Objects.requireNonNull(name, "name must not be null");
        java.util.Objects.requireNonNull(sha,  "sha must not be null");
    }
}
