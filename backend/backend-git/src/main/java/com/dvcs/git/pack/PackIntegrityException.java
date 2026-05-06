package com.dvcs.git.pack;

/**
 * Thrown when a pack-file's SHA-256 trailer does not match the computed digest
 * of the pack data, indicating corruption or tampering.
 *
 * <p>Requirement 4 / Req 6: Pack-File Transfer Format — integrity verification
 * of the 32-byte SHA-256 trailer appended to every pack stream.
 */
public class PackIntegrityException extends RuntimeException {

    /**
     * Constructs a {@code PackIntegrityException} with a detail message.
     *
     * @param message description of the integrity failure
     */
    public PackIntegrityException(String message) {
        super(message);
    }

    /**
     * Constructs a {@code PackIntegrityException} with a detail message and cause.
     *
     * @param message description of the integrity failure
     * @param cause   the underlying exception
     */
    public PackIntegrityException(String message, Throwable cause) {
        super(message, cause);
    }
}
