package com.dvcs.git.transport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PktLineUtil}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>encodeLine(String) produces correct 4-hex-digit length prefix + data</li>
 *   <li>encodeLine(byte[]) produces correct prefix + data</li>
 *   <li>readLine returns null for flush-pkt (0000)</li>
 *   <li>readLine returns the data string for a normal pkt-line</li>
 *   <li>round-trip: encode then read returns the original string</li>
 *   <li>FLUSH_PKT constant is "0000" in ASCII</li>
 * </ul>
 */
@DisplayName("PktLineUtil")
class PktLineUtilTest {

    @Test
    @DisplayName("encodeLine(String) produces correct length prefix")
    void encodeLineString_correctLengthPrefix() {
        // "hello\n" is 6 bytes; total pkt-line length = 4 + 6 = 10 = "000a"
        byte[] encoded = PktLineUtil.encodeLine("hello\n");
        String prefix = new String(encoded, 0, 4, StandardCharsets.US_ASCII);
        assertThat(prefix).isEqualTo("000a");
        String data = new String(encoded, 4, encoded.length - 4, StandardCharsets.UTF_8);
        assertThat(data).isEqualTo("hello\n");
    }

    @Test
    @DisplayName("encodeLine(byte[]) produces correct length prefix")
    void encodeLineBytes_correctLengthPrefix() {
        byte[] data = "ab".getBytes(StandardCharsets.UTF_8);
        // total = 4 + 2 = 6 = "0006"
        byte[] encoded = PktLineUtil.encodeLine(data);
        String prefix = new String(encoded, 0, 4, StandardCharsets.US_ASCII);
        assertThat(prefix).isEqualTo("0006");
        assertThat(encoded[4]).isEqualTo((byte) 'a');
        assertThat(encoded[5]).isEqualTo((byte) 'b');
    }

    @Test
    @DisplayName("readLine returns null for flush-pkt 0000")
    void readLine_flushPkt_returnsNull() throws IOException {
        byte[] flushPkt = "0000".getBytes(StandardCharsets.US_ASCII);
        ByteArrayInputStream in = new ByteArrayInputStream(flushPkt);
        String result = PktLineUtil.readLine(in);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("readLine returns data string for normal pkt-line")
    void readLine_normalLine_returnsData() throws IOException {
        // "hello\n" is 6 bytes; total pkt-line length = 4 + 6 = 10 = "000a"
        byte[] pktLine = "000ahello\n".getBytes(StandardCharsets.US_ASCII);
        ByteArrayInputStream in = new ByteArrayInputStream(pktLine);
        String result = PktLineUtil.readLine(in);
        assertThat(result).isEqualTo("hello\n");
    }

    @Test
    @DisplayName("round-trip: encodeLine then readLine returns original string")
    void roundTrip_encodeAndRead() throws IOException {
        String original = "want abc123def456\n";
        byte[] encoded = PktLineUtil.encodeLine(original);
        ByteArrayInputStream in = new ByteArrayInputStream(encoded);
        String decoded = PktLineUtil.readLine(in);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    @DisplayName("FLUSH_PKT constant is ASCII '0000'")
    void flushPktConstant_isCorrect() {
        assertThat(PktLineUtil.FLUSH_PKT)
                .isEqualTo("0000".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    @DisplayName("readLine returns null at end of stream")
    void readLine_endOfStream_returnsNull() throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
        String result = PktLineUtil.readLine(in);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("multiple pkt-lines can be read sequentially")
    void readLine_multipleLines_readSequentially() throws IOException {
        byte[] line1 = PktLineUtil.encodeLine("want sha1\n");
        byte[] line2 = PktLineUtil.encodeLine("have sha2\n");
        byte[] flush = PktLineUtil.FLUSH_PKT;

        byte[] combined = new byte[line1.length + line2.length + flush.length];
        System.arraycopy(line1, 0, combined, 0, line1.length);
        System.arraycopy(line2, 0, combined, line1.length, line2.length);
        System.arraycopy(flush, 0, combined, line1.length + line2.length, flush.length);

        ByteArrayInputStream in = new ByteArrayInputStream(combined);
        assertThat(PktLineUtil.readLine(in)).isEqualTo("want sha1\n");
        assertThat(PktLineUtil.readLine(in)).isEqualTo("have sha2\n");
        assertThat(PktLineUtil.readLine(in)).isNull(); // flush-pkt
    }
}
