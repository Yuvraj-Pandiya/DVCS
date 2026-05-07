package com.dvcs.diff.algorithm;

/**
 * Detects whether a byte array represents binary (non-text) content.
 *
 * <p>A file is classified as binary if:
 * <ol>
 *   <li>Any of the first {@value #SCAN_LIMIT} bytes is a null byte ({@code \0}), OR</li>
 *   <li>The byte sequence starts with a known binary magic number:
 *       <ul>
 *         <li>PNG: {@code \x89PNG} ({@code 0x89 0x50 0x4E 0x47})</li>
 *         <li>PDF: {@code %PDF} ({@code 0x25 0x50 0x44 0x46})</li>
 *         <li>ZIP: {@code PK\x03\x04} ({@code 0x50 0x4B 0x03 0x04})</li>
 *         <li>ELF: {@code \x7fELF} ({@code 0x7F 0x45 0x4C 0x46})</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <p>No external library is used.
 *
 * <p>Requirement 9.5: Diff Engine — binary file detection.
 */
public class BinaryDetector {

    /** Maximum number of bytes to scan for null bytes. */
    private static final int SCAN_LIMIT = 8_000;

    // Magic byte sequences for known binary formats
    private static final byte[] MAGIC_PNG = {(byte) 0x89, 'P', 'N', 'G'};
    private static final byte[] MAGIC_PDF = {'%', 'P', 'D', 'F'};
    private static final byte[] MAGIC_ZIP = {'P', 'K', 0x03, 0x04};
    private static final byte[] MAGIC_ELF = {0x7F, 'E', 'L', 'F'};

    // Prevent instantiation — all methods are static.
    private BinaryDetector() {}

    /**
     * Returns {@code true} if the given byte array appears to be binary content.
     *
     * <p>The check is performed in two phases:
     * <ol>
     *   <li>Magic-byte check: if the data starts with a known binary signature,
     *       return {@code true} immediately without scanning the rest.</li>
     *   <li>Null-byte scan: scan up to the first {@value #SCAN_LIMIT} bytes;
     *       if any byte is {@code 0x00}, return {@code true}.</li>
     * </ol>
     *
     * @param data the byte array to inspect; must not be {@code null}
     * @return {@code true} if the content is binary; {@code false} if it appears
     *         to be text
     * @throws NullPointerException if {@code data} is {@code null}
     */
    public static boolean isBinary(byte[] data) {
        if (data == null) throw new NullPointerException("data must not be null");

        // Phase 1: magic-byte check (fast path for common binary formats)
        if (startsWith(data, MAGIC_PNG)) return true;
        if (startsWith(data, MAGIC_PDF)) return true;
        if (startsWith(data, MAGIC_ZIP)) return true;
        if (startsWith(data, MAGIC_ELF)) return true;

        // Phase 2: null-byte scan
        int limit = Math.min(data.length, SCAN_LIMIT);
        for (int i = 0; i < limit; i++) {
            if (data[i] == 0) {
                return true;
            }
        }

        return false;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if {@code data} starts with the given {@code magic} bytes.
     *
     * @param data  the byte array to check
     * @param magic the magic byte sequence to look for
     * @return {@code true} if {@code data} begins with {@code magic}
     */
    private static boolean startsWith(byte[] data, byte[] magic) {
        if (data.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (data[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }
}
