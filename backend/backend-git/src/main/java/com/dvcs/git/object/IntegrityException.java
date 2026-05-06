package com.dvcs.git.object;

/**
 * Thrown when a SHA-256 integrity check fails — i.e. the computed digest of
 * a byte array does not match the expected digest.
 *
 * <p>Requirement 4: Git Object Storage Engine — content-addressable storage
 * must verify object integrity on read.
 */
public class IntegrityException extends RuntimeException {

    /**
     * Constructs an {@code IntegrityException} with a message that includes
     * both the expected and actual SHA-256 hex strings.
     *
     * @param expectedSha the SHA-256 hex string that was expected
     * @param actualSha   the SHA-256 hex string that was actually computed
     */
    public IntegrityException(String expectedSha, String actualSha) {
        super(String.format(
                "Integrity check failed: expected SHA-256 [%s] but computed [%s]",
                expectedSha, actualSha));
    }
}
