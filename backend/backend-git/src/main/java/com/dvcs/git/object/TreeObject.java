package com.dvcs.git.object;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;

/**
 * Represents a Git tree object — a directory snapshot that maps names and modes
 * to the SHA-256 hashes of blobs or other trees.
 *
 * <p>Serialization format:
 * <pre>
 *   tree {total-size}\0{entries...}
 * </pre>
 * where each entry is encoded as:
 * <pre>
 *   {mode} {name}\0{20-byte binary sha}
 * </pre>
 * The 20-byte binary SHA is derived by converting the 64-character hex SHA-256
 * string into 32 raw bytes (each pair of hex characters → one byte).
 *
 * <p>The SHA-256 hash of the {@code TreeObject} itself is computed from the full
 * serialized form and stored in the parent class.
 *
 * <p>Requirement 4: Git Object Storage Engine — tree serialization and SHA-256 hashing.
 */
public class TreeObject extends GitObject {

    /** The ordered list of entries in this tree. */
    private final List<TreeEntry> entries;

    /**
     * Constructs a {@code TreeObject} from a list of tree entries.
     *
     * <p>The SHA-256 hash is computed from the full serialized form and stored
     * in the parent class.
     *
     * @param entries the list of tree entries; must not be {@code null}
     * @throws NullPointerException if {@code entries} is {@code null}
     */
    public TreeObject(List<TreeEntry> entries) {
        super(computeSha(Objects.requireNonNull(entries, "entries must not be null")),
              ObjectType.TREE);
        this.entries = List.copyOf(entries);
    }

    /**
     * Returns the canonical byte encoding of this tree object.
     *
     * <p>Format: {@code "tree {size}\0"} (UTF-8) followed by the concatenated
     * binary-encoded entries, where {@code {size}} is the byte length of the
     * entries content.
     *
     * @return canonical byte array; never {@code null}
     */
    @Override
    public byte[] serialize() {
        byte[] entriesBytes = encodeEntries(entries);
        byte[] header = buildHeader(entriesBytes.length);
        byte[] result = new byte[header.length + entriesBytes.length];
        System.arraycopy(header, 0, result, 0, header.length);
        System.arraycopy(entriesBytes, 0, result, header.length, entriesBytes.length);
        return result;
    }

    /**
     * Returns an unmodifiable view of the tree entries.
     *
     * @return list of {@link TreeEntry}; never {@code null}
     */
    public List<TreeEntry> getEntries() {
        return entries;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Encodes all entries into their binary representation.
     *
     * <p>Each entry is encoded as: {@code "{mode} {name}\0{20-byte binary sha}"}.
     *
     * @param entries the list of entries to encode
     * @return concatenated binary encoding of all entries
     */
    private static byte[] encodeEntries(List<TreeEntry> entries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (TreeEntry entry : entries) {
            try {
                // "{mode} {name}\0"
                String prefix = entry.mode() + " " + entry.name() + "\0";
                out.write(prefix.getBytes(StandardCharsets.UTF_8));
                // 20-byte binary SHA (convert 64-char hex → 32 bytes for SHA-256)
                out.write(hexToBytes(entry.sha()));
            } catch (IOException e) {
                // ByteArrayOutputStream never throws IOException
                throw new IllegalStateException("Unexpected I/O error", e);
            }
        }
        return out.toByteArray();
    }

    /**
     * Builds the tree header bytes: {@code "tree {size}\0"} encoded as UTF-8.
     *
     * @param size the byte length of the entries content
     * @return header bytes
     */
    private static byte[] buildHeader(int size) {
        String headerStr = "tree " + size + "\0";
        return headerStr.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Computes the SHA-256 hex digest of the serialized tree
     * ({@code "tree {size}\0"} + entries bytes).
     *
     * @param entries the list of tree entries
     * @return 64-character lowercase hex SHA-256 string
     */
    private static String computeSha(List<TreeEntry> entries) {
        byte[] entriesBytes = encodeEntries(entries);
        byte[] header = buildHeader(entriesBytes.length);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(header);
            digest.update(entriesBytes);
            byte[] hashBytes = digest.digest();
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in every JVM
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Converts a hexadecimal string to a byte array.
     *
     * <p>Each pair of hex characters is converted to one byte. The input length
     * must be even.
     *
     * @param hex the hex string to convert; must have even length
     * @return byte array of length {@code hex.length() / 2}
     * @throws IllegalArgumentException if {@code hex} has odd length or contains
     *                                  non-hex characters
     */
    static byte[] hexToBytes(String hex) {
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException(
                    "Hex string must have even length, got: " + hex.length());
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int high = Character.digit(hex.charAt(i * 2),     16);
            int low  = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (high == -1 || low == -1) {
                throw new IllegalArgumentException(
                        "Invalid hex character at position " + (i * 2) + " in: " + hex);
            }
            bytes[i] = (byte) ((high << 4) | low);
        }
        return bytes;
    }

    /**
     * Converts a byte array to a lowercase hexadecimal string.
     *
     * @param bytes the bytes to convert
     * @return lowercase hex string
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
