package com.dvcs.diff.model;

/**
 * Represents a single line in a unified diff output.
 *
 * <p>Each line carries its classification ({@link LineType}), the raw text content,
 * and the 1-based line numbers in the base and head files. For {@link LineType#ADD}
 * lines, {@code baseLineNo} is {@code -1} (the line does not exist in the base).
 * For {@link LineType#REMOVE} lines, {@code headLineNo} is {@code -1}.
 * For {@link LineType#CONTEXT} lines, both numbers are valid.
 *
 * <p>Requirement 9.1: Diff Engine — DiffLine model.
 *
 * @param type       the classification of this line
 * @param content    the raw text of the line (without a trailing newline)
 * @param baseLineNo 1-based line number in the base file, or {@code -1} if not applicable
 * @param headLineNo 1-based line number in the head file, or {@code -1} if not applicable
 */
public record DiffLine(LineType type, String content, int baseLineNo, int headLineNo) {

    /**
     * Compact constructor that validates invariants.
     *
     * @throws NullPointerException     if {@code type} or {@code content} is {@code null}
     * @throws IllegalArgumentException if line numbers are inconsistent with the type
     */
    public DiffLine {
        if (type == null) throw new NullPointerException("type must not be null");
        if (content == null) throw new NullPointerException("content must not be null");
    }

    // -------------------------------------------------------------------------
    // Factory helpers
    // -------------------------------------------------------------------------

    /**
     * Creates an ADD line (present only in the head file).
     *
     * @param content    the line text
     * @param headLineNo 1-based line number in the head file
     * @return a new {@code DiffLine} with type {@link LineType#ADD}
     */
    public static DiffLine add(String content, int headLineNo) {
        return new DiffLine(LineType.ADD, content, -1, headLineNo);
    }

    /**
     * Creates a REMOVE line (present only in the base file).
     *
     * @param content    the line text
     * @param baseLineNo 1-based line number in the base file
     * @return a new {@code DiffLine} with type {@link LineType#REMOVE}
     */
    public static DiffLine remove(String content, int baseLineNo) {
        return new DiffLine(LineType.REMOVE, content, baseLineNo, -1);
    }

    /**
     * Creates a CONTEXT line (present in both files, unchanged).
     *
     * @param content    the line text
     * @param baseLineNo 1-based line number in the base file
     * @param headLineNo 1-based line number in the head file
     * @return a new {@code DiffLine} with type {@link LineType#CONTEXT}
     */
    public static DiffLine context(String content, int baseLineNo, int headLineNo) {
        return new DiffLine(LineType.CONTEXT, content, baseLineNo, headLineNo);
    }
}
