package com.dvcs.git.object;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SHA256Util}.
 *
 * <p>Verifies that {@code computeHex} produces a correct lowercase 64-char hex
 * digest and that {@code verifyIntegrity} throws {@link IntegrityException} on
 * mismatch while passing silently on a match.
 */
class SHA256UtilTest {

    // -------------------------------------------------------------------------
    // computeHex — format
    // -------------------------------------------------------------------------

    @Test
    void computeHex_emptyArray_returns64LowercaseHexChars() {
        String hex = SHA256Util.computeHex(new byte[0]);
        assertNotNull(hex);
        assertEquals(64, hex.length(), "SHA-256 hex must be 64 characters");
        assertTrue(hex.matches("[0-9a-f]{64}"), "SHA-256 hex must be lowercase");
    }

    @Test
    void computeHex_knownInput_matchesExpectedDigest() throws Exception {
        // "hello" → well-known SHA-256
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
        String expected = referenceHex(data);
        assertEquals(expected, SHA256Util.computeHex(data));
    }

    @Test
    void computeHex_allZeroBytes_returnsLowercaseHex() {
        byte[] data = new byte[32]; // all zeros
        String hex = SHA256Util.computeHex(data);
        assertEquals(64, hex.length());
        assertTrue(hex.matches("[0-9a-f]{64}"), "Must be lowercase hex");
    }

    @Test
    void computeHex_differentInputs_produceDifferentDigests() {
        byte[] a = "foo".getBytes(StandardCharsets.UTF_8);
        byte[] b = "bar".getBytes(StandardCharsets.UTF_8);
        assertNotEquals(SHA256Util.computeHex(a), SHA256Util.computeHex(b));
    }

    @Test
    void computeHex_sameInput_producesSameDigest() {
        byte[] data = "deterministic".getBytes(StandardCharsets.UTF_8);
        assertEquals(SHA256Util.computeHex(data), SHA256Util.computeHex(data));
    }

    @Test
    void computeHex_nullData_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> SHA256Util.computeHex(null));
    }

    // -------------------------------------------------------------------------
    // verifyIntegrity — happy path
    // -------------------------------------------------------------------------

    @Test
    void verifyIntegrity_correctSha_doesNotThrow() {
        byte[] data = "content".getBytes(StandardCharsets.UTF_8);
        String sha = SHA256Util.computeHex(data);
        assertDoesNotThrow(() -> SHA256Util.verifyIntegrity(sha, data));
    }

    @Test
    void verifyIntegrity_emptyData_correctSha_doesNotThrow() {
        byte[] data = new byte[0];
        String sha = SHA256Util.computeHex(data);
        assertDoesNotThrow(() -> SHA256Util.verifyIntegrity(sha, data));
    }

    // -------------------------------------------------------------------------
    // verifyIntegrity — mismatch
    // -------------------------------------------------------------------------

    @Test
    void verifyIntegrity_wrongSha_throwsIntegrityException() {
        byte[] data = "content".getBytes(StandardCharsets.UTF_8);
        String wrongSha = "0".repeat(64);
        assertThrows(IntegrityException.class,
                () -> SHA256Util.verifyIntegrity(wrongSha, data));
    }

    @Test
    void verifyIntegrity_exceptionMessage_containsExpectedAndActualSha() {
        byte[] data = "content".getBytes(StandardCharsets.UTF_8);
        String wrongSha = "0".repeat(64);
        String actualSha = SHA256Util.computeHex(data);

        IntegrityException ex = assertThrows(IntegrityException.class,
                () -> SHA256Util.verifyIntegrity(wrongSha, data));

        assertTrue(ex.getMessage().contains(wrongSha),
                "Exception message must contain expected SHA");
        assertTrue(ex.getMessage().contains(actualSha),
                "Exception message must contain actual SHA");
    }

    @Test
    void verifyIntegrity_modifiedData_throwsIntegrityException() {
        byte[] original = "original".getBytes(StandardCharsets.UTF_8);
        String sha = SHA256Util.computeHex(original);

        byte[] tampered = "tampered".getBytes(StandardCharsets.UTF_8);
        assertThrows(IntegrityException.class,
                () -> SHA256Util.verifyIntegrity(sha, tampered));
    }

    // -------------------------------------------------------------------------
    // verifyIntegrity — null guards
    // -------------------------------------------------------------------------

    @Test
    void verifyIntegrity_nullExpectedSha_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> SHA256Util.verifyIntegrity(null, new byte[0]));
    }

    @Test
    void verifyIntegrity_nullData_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> SHA256Util.verifyIntegrity("0".repeat(64), null));
    }

    // -------------------------------------------------------------------------
    // IntegrityException — structure
    // -------------------------------------------------------------------------

    @Test
    void integrityException_isRuntimeException() {
        IntegrityException ex = new IntegrityException("a".repeat(64), "b".repeat(64));
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void integrityException_message_containsBothShas() {
        String expected = "a".repeat(64);
        String actual   = "b".repeat(64);
        IntegrityException ex = new IntegrityException(expected, actual);
        assertTrue(ex.getMessage().contains(expected));
        assertTrue(ex.getMessage().contains(actual));
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /** Reference implementation using MessageDigest directly. */
    private static String referenceHex(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(data);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
