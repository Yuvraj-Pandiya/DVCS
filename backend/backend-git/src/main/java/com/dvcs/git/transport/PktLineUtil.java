package com.dvcs.git.transport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for encoding and decoding Git pkt-line format.
 *
 * <h2>Pkt-line format</h2>
 * <p>Each pkt-line consists of a 4-hex-digit length prefix (inclusive of the
 * 4 prefix bytes themselves) followed by the data bytes. A flush-pkt is the
 * special sequence {@code "0000"} with no data.
 *
 * <p>Examples:
 * <pre>
 *   "0006ab\n"  → length=6 (4 prefix + 2 data), data="ab\n"
 *   "0000"      → flush-pkt (no data)
 * </pre>
 *
 * <p>Requirement 6: HTTP Smart Git Transport — pkt-line encoding/decoding.
 */
public final class PktLineUtil {

    /** The flush-pkt bytes: ASCII {@code "0000"}. */
    public static final byte[] FLUSH_PKT = "0000".getBytes(StandardCharsets.US_ASCII);

    private PktLineUtil() {
        // utility class — no instances
    }

    /**
     * Encodes a string as a single pkt-line.
     *
     * <p>The length prefix is computed as {@code 4 + data.length()} (UTF-8 bytes).
     *
     * @param data the string to encode; must not be {@code null}
     * @return pkt-line encoded bytes (length prefix + data bytes)
     */
    public static byte[] encodeLine(String data) {
        return encodeLine(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Encodes raw bytes as a single pkt-line.
     *
     * <p>The length prefix is computed as {@code 4 + data.length}.
     *
     * @param data the bytes to encode; must not be {@code null}
     * @return pkt-line encoded bytes (4-char hex length prefix + data bytes)
     */
    public static byte[] encodeLine(byte[] data) {
        int totalLen = 4 + data.length;
        String hexLen = String.format("%04x", totalLen);
        byte[] prefix = hexLen.getBytes(StandardCharsets.US_ASCII);
        byte[] result = new byte[prefix.length + data.length];
        System.arraycopy(prefix, 0, result, 0, prefix.length);
        System.arraycopy(data, 0, result, prefix.length, data.length);
        return result;
    }

    /**
     * Reads one pkt-line from the given {@link InputStream}.
     *
     * <p>Reads the 4-byte hex length prefix, then reads {@code length - 4} data
     * bytes. Returns {@code null} for a flush-pkt ({@code "0000"}).
     *
     * @param in the input stream to read from; must not be {@code null}
     * @return the decoded line string, or {@code null} for a flush-pkt
     * @throws IOException if an I/O error occurs or the stream ends prematurely
     */
    public static String readLine(InputStream in) throws IOException {
        byte[] lenBuf = new byte[4];
        int read = readFully(in, lenBuf, 0, 4);
        if (read < 4) {
            return null; // end of stream
        }

        String hexLen = new String(lenBuf, StandardCharsets.US_ASCII);
        int totalLen;
        try {
            totalLen = Integer.parseInt(hexLen, 16);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid pkt-line length prefix: " + hexLen);
        }

        if (totalLen == 0) {
            return null; // flush-pkt
        }

        if (totalLen < 4) {
            throw new IOException("Invalid pkt-line length: " + totalLen);
        }

        int dataLen = totalLen - 4;
        if (dataLen == 0) {
            return "";
        }

        byte[] dataBuf = new byte[dataLen];
        int dataRead = readFully(in, dataBuf, 0, dataLen);
        if (dataRead < dataLen) {
            throw new IOException("Unexpected end of stream reading pkt-line data");
        }

        return new String(dataBuf, StandardCharsets.UTF_8);
    }

    /**
     * Reads exactly {@code len} bytes from {@code in} into {@code buf} starting
     * at {@code off}.
     *
     * @param in  the input stream
     * @param buf the destination buffer
     * @param off the start offset in {@code buf}
     * @param len the number of bytes to read
     * @return the number of bytes actually read (may be less than {@code len} at EOF)
     * @throws IOException if an I/O error occurs
     */
    private static int readFully(InputStream in, byte[] buf, int off, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int n = in.read(buf, off + total, len - total);
            if (n < 0) break;
            total += n;
        }
        return total;
    }
}
