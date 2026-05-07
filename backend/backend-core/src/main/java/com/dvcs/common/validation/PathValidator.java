package com.dvcs.common.validation;

import com.dvcs.common.exception.PathTraversalException;
import org.springframework.stereotype.Component;

/**
 * Validates file path parameters to prevent path-traversal attacks.
 *
 * <p>Rejects any path that contains:
 * <ul>
 *   <li>{@code ../} — Unix-style parent directory traversal</li>
 *   <li>{@code ..\} — Windows-style parent directory traversal</li>
 *   <li>{@code %2e%2e} — URL-encoded double-dot (case-insensitive)</li>
 * </ul>
 *
 * <p>Requirement 18.2 / Req 7.5: The System SHALL reject any file path parameter
 * containing path-traversal sequences with HTTP 400.
 */
@Component
public class PathValidator {

    /**
     * Validates that the given path does not contain path-traversal sequences.
     *
     * @param path the file path to validate; may be {@code null} or empty
     * @throws PathTraversalException if the path contains a traversal sequence
     */
    public void validate(String path) {
        if (path == null || path.isEmpty()) {
            return;
        }

        // Check for literal traversal sequences
        if (path.contains("../") || path.contains("..\\")) {
            throw new PathTraversalException(
                    "Path contains illegal traversal sequence: " + path);
        }

        // Check for a trailing ".." (e.g. "foo/..")
        if (path.endsWith("..")) {
            throw new PathTraversalException(
                    "Path contains illegal traversal sequence: " + path);
        }

        // Check for URL-encoded double-dot (case-insensitive: %2e%2e, %2E%2E, %2e%2E, etc.)
        String lower = path.toLowerCase();
        if (lower.contains("%2e%2e")) {
            throw new PathTraversalException(
                    "Path contains URL-encoded traversal sequence: " + path);
        }
    }
}
