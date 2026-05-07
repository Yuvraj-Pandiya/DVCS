package com.dvcs.diff.algorithm;

import com.dvcs.diff.model.DiffHunk;
import com.dvcs.diff.model.DiffLine;
import com.dvcs.diff.model.LineType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Applies a list of {@link DiffHunk}s to a base file to reconstruct the head file.
 *
 * <p>The patch application algorithm works by walking through the base lines in
 * order, emitting CONTEXT lines as-is, skipping REMOVE lines, and inserting ADD
 * lines at the appropriate positions. This satisfies the round-trip property:
 * <pre>
 *   apply(A, diff(A, B)) == B
 * </pre>
 *
 * <p>No external diff library is used.
 *
 * <p>Requirement 9.4: Diff Engine — PatchApplier.
 */
public class PatchApplier {

    // Prevent instantiation — all methods are static.
    private PatchApplier() {}

    /**
     * Reconstructs the head file by applying the given hunks to the base lines.
     *
     * <p>The algorithm processes the diff lines from all hunks in order of their
     * base-file line numbers. For each diff line:
     * <ul>
     *   <li>{@link LineType#CONTEXT} — the corresponding base line is emitted unchanged.</li>
     *   <li>{@link LineType#REMOVE}  — the corresponding base line is skipped.</li>
     *   <li>{@link LineType#ADD}     — the new line is inserted into the output.</li>
     * </ul>
     * Base lines that fall outside all hunk windows (i.e., lines not mentioned in
     * any hunk) are emitted verbatim.
     *
     * @param base  the original lines; must not be {@code null}
     * @param hunks the list of hunks to apply; must not be {@code null}
     * @return the reconstructed head lines as a {@code String[]}
     * @throws NullPointerException if {@code base} or {@code hunks} is {@code null}
     */
    public static String[] apply(String[] base, List<DiffHunk> hunks) {
        Objects.requireNonNull(base,  "base must not be null");
        Objects.requireNonNull(hunks, "hunks must not be null");

        if (hunks.isEmpty()) {
            return base.clone();
        }

        // Build a flat, ordered list of instructions by merging all hunk lines.
        // We process hunks in order of their baseStart, and within each hunk we
        // process lines in their natural order (as produced by MyersDiff).
        // This preserves the interleaving of REMOVE and ADD lines.
        List<DiffHunk> sortedHunks = new ArrayList<>(hunks);
        sortedHunks.sort((a, b) -> Integer.compare(a.getBaseStart(), b.getBaseStart()));

        List<String> result = new ArrayList<>(base.length + 16);

        // nextBase is the 1-based index of the next base line we haven't yet processed
        int nextBase = 1;

        for (DiffHunk hunk : sortedHunks) {
            // Emit any base lines that come before this hunk's window
            int hunkBaseStart = hunk.getBaseStart();

            // Find the first base line referenced in this hunk
            int firstBaseRef = findFirstBaseRef(hunk.getLines());
            if (firstBaseRef > 0) {
                while (nextBase < firstBaseRef) {
                    result.add(base[nextBase - 1]);
                    nextBase++;
                }
            }

            // Process the hunk lines in order
            for (DiffLine dl : hunk.getLines()) {
                switch (dl.type()) {
                    case CONTEXT -> {
                        // Emit any base lines that come before this context line
                        while (nextBase < dl.baseLineNo()) {
                            result.add(base[nextBase - 1]);
                            nextBase++;
                        }
                        // Emit the context line
                        result.add(dl.content());
                        nextBase = dl.baseLineNo() + 1;
                    }
                    case REMOVE -> {
                        // Emit any base lines that come before this removed line
                        while (nextBase < dl.baseLineNo()) {
                            result.add(base[nextBase - 1]);
                            nextBase++;
                        }
                        // Skip the removed base line
                        nextBase = dl.baseLineNo() + 1;
                    }
                    case ADD -> {
                        // Insert the new line at the current position
                        result.add(dl.content());
                    }
                }
            }
        }

        // Emit any remaining base lines after the last hunk
        while (nextBase <= base.length) {
            result.add(base[nextBase - 1]);
            nextBase++;
        }

        return result.toArray(new String[0]);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Finds the first base line number referenced in the given list of diff lines.
     *
     * @param lines the diff lines
     * @return the first positive base line number, or {@code -1} if none
     */
    private static int findFirstBaseRef(List<DiffLine> lines) {
        for (DiffLine dl : lines) {
            if (dl.baseLineNo() > 0) {
                return dl.baseLineNo();
            }
        }
        return -1;
    }
}
