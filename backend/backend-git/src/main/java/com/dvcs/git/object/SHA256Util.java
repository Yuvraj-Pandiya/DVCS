package com.dvcs.git.object;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Utility class for SHA-256 hashing and integrity verification.
 *
 * <p>Uses only {@link java.security.MessageDigest} — no external libraries.
 *
 * <p>Requirement 4: Git Object Storage Engine — SHA-256 hash as unique key,
 * content-addressable storage with integrity verification.
 */
public final class SHA256Util {

    private static final HexFormat HEX = HexFormat.of(); // lowercase by default

    /** Utility class — not instantiable. */
    private SHA256Util() {
        throw new UnsupportedOperationException("SHA256Util is a utility class");
    }

    /**
     * Computes the SHA-256 digest of {@code data} and returns it as a
     * 64-character lowercase hexadecimal string.
     *
     * @param data the bytes to hash; must not be {@code null}
     * @return 64-character lowercase hex SHA-256 digest
     * @throws NullPointerException if {@code data} is {@code null}
     */
    public static String computeHex(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data);
            return HEX.formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the Java SE specification — this cannot happen.
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Verifies that the SHA-256 digest of {@code data} matches {@code expectedSha}.
     *
     * @param expectedSha the expected 64-character lowercase hex SHA-256 digest;
     *                    must not be {@code null}
     * @param data        the bytes whose digest is to be verified; must not be {@code null}
     * @throws IntegrityException   if the computed digest does not equal {@code expectedSha}
     * @throws NullPointerException if either argument is {@code null}
     */
    public static void verifyIntegrity(String expectedSha, byte[] data) {
        Objects.requireNonNull(expectedSha, "expectedSha must not be null");
        Objects.requireNonNull(data, "data must not be null");
        String actualSha = computeHex(data);
        if (!actualSha.equals(expectedSha)) {
            throw new IntegrityException(expectedSha, actualSha);
        }
    }
}
