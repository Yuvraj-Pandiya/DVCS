package com.dvcs.diff.model;

import java.util.List;
import java.util.Objects;

/**
 * Represents a contiguous block of changes (a "hunk") in a unified diff.
 *
 * <p>A hunk groups a set of {@link DiffLine}s that are close enough together to
 * be displayed as a single unit, surrounded by up to ±3 context lines on each side.
 *
 * <p>Line ranges are 1-based and inclusive. For a hunk that only adds lines,
 * {@code baseStart} and {@code baseEnd} indicate the insertion point in the base
 * file (they may be equal, indicating insertion between two lines).
 *
 * <p>Requirement 9.2: Diff Engine — DiffHunk model.
 */
public class DiffHunk {

    /**
     * The dominant change type of this hunk.
     *
     * <p>A hunk may contain a mix of ADD, REMOVE, and CONTEXT lines; this field
     * reflects the primary change type for display purposes. Mixed hunks use
     * {@link LineType#ADD} by convention when both additions and removals are present.
     */
    private final LineType type;

    /** 1-based start line in the base file (inclusive). */
    private final int baseStart;

    /** 1-based end line in the base file (inclusive). */
    private final int baseEnd;

    /** 1-based start line in the head file (inclusive). */
    private final int headStart;

    /** 1-based end line in the head file (inclusive). */
    private final int headEnd;

    /** The ordered list of diff lines in this hunk. */
    private final List<DiffLine> lines;

    /**
     * Constructs a {@code DiffHunk}.
     *
     * @param type      the dominant change type; must not be {@code null}
     * @param baseStart 1-based start line in the base file
     * @param baseEnd   1-based end line in the base file
     * @param headStart 1-based start line in the head file
     * @param headEnd   1-based end line in the head file
     * @param lines     the ordered list of diff lines; must not be {@code null}
     * @throws NullPointerException if {@code type} or {@code lines} is {@code null}
     */
    public DiffHunk(LineType type, int baseStart, int baseEnd,
                    int headStart, int headEnd, List<DiffLine> lines) {
        this.type      = Objects.requireNonNull(type,  "type must not be null");
        this.lines     = List.copyOf(Objects.requireNonNull(lines, "lines must not be null"));
        this.baseStart = baseStart;
        this.baseEnd   = baseEnd;
        this.headStart = headStart;
        this.headEnd   = headEnd;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** @return the dominant change type of this hunk */
    public LineType getType() { return type; }

    /** @return 1-based start line in the base file */
    public int getBaseStart() { return baseStart; }

    /** @return 1-based end line in the base file */
    public int getBaseEnd() { return baseEnd; }

    /** @return 1-based start line in the head file */
    public int getHeadStart() { return headStart; }

    /** @return 1-based end line in the head file */
    public int getHeadEnd() { return headEnd; }

    /** @return an unmodifiable list of diff lines in this hunk */
    public List<DiffLine> getLines() { return lines; }

    // -------------------------------------------------------------------------
    // Convenience queries
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if this hunk contains any ADD lines.
     *
     * @return {@code true} if at least one line has type {@link LineType#ADD}
     */
    public boolean hasAdditions() {
        return lines.stream().anyMatch(l -> l.type() == LineType.ADD);
    }

    /**
     * Returns {@code true} if this hunk contains any REMOVE lines.
     *
     * @return {@code true} if at least one line has type {@link LineType#REMOVE}
     */
    public boolean hasRemovals() {
        return lines.stream().anyMatch(l -> l.type() == LineType.REMOVE);
    }

    @Override
    public String toString() {
        return "DiffHunk{type=" + type
                + ", base=" + baseStart + "-" + baseEnd
                + ", head=" + headStart + "-" + headEnd
                + ", lines=" + lines.size() + "}";
    }
}
