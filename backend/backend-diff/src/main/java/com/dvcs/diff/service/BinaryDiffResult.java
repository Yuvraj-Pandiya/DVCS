package com.dvcs.diff.service;

/**
 * The result of a binary diff operation.
 *
 * <p>Since binary files cannot be diffed line-by-line, this record captures only
 * the size of each version and the size delta between them.
 *
 * <p>Requirement 9.7: Diff Engine — binary diff result.
 *
 * @param binary        always {@code true} to indicate this is a binary diff
 * @param baseSizeBytes the size of the base file in bytes
 * @param headSizeBytes the size of the head file in bytes
 */
public record BinaryDiffResult(boolean binary, long baseSizeBytes, long headSizeBytes) {

    /**
     * Returns the size delta ({@code headSizeBytes - baseSizeBytes}).
     *
     * @return positive if the head is larger, negative if smaller, zero if equal
     */
    public long sizeDelta() {
        return headSizeBytes - baseSizeBytes;
    }
}
