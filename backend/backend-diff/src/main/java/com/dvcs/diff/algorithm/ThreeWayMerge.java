package com.dvcs.diff.algorithm;

import com.dvcs.diff.model.DiffHunk;
import com.dvcs.diff.model.DiffLine;
import com.dvcs.diff.model.LineType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Performs a three-way merge of three line sequences: {@code base}, {@code ours},
 * and {@code theirs}.
 *
 * <h2>Algorithm</h2>
 * <ol>
 *   <li>Compute {@code diff(base, ours)} → edit script E1.</li>
 *   <li>Compute {@code diff(base, theirs)} → edit script E2.</li>
 *   <li>Walk through the base lines. For each base line:
 *     <ul>
 *       <li>If neither E1 nor E2 touches it → emit the base line unchanged.</li>
 *       <li>If only E1 touches it → apply E1's change (auto-merge).</li>
 *       <li>If only E2 touches it → apply E2's change (auto-merge).</li>
 *       <li>If both E1 and E2 touch the same base line(s) → emit conflict markers.</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>Conflict markers follow the standard format:
 * <pre>
 *   &lt;&lt;&lt;&lt;&lt;&lt;&lt; OURS
 *   {ours lines}
 *   =======
 *   {theirs lines}
 *   &gt;&gt;&gt;&gt;&gt;&gt;&gt; THEIRS
 * </pre>
 *
 * <p>No external diff library is used.
 *
 * <p>Requirement 9.6: Diff Engine — three-way merge.
 */
public class ThreeWayMerge {

    private static final String MARKER_OURS   = "<<<<<<< OURS";
    private static final String MARKER_SEP    = "=======";
    private static final String MARKER_THEIRS = ">>>>>>> THEIRS";

    // Prevent instantiation — all methods are static.
    private ThreeWayMerge() {}

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Merges {@code ours} and {@code theirs} relative to their common {@code base}.
     *
     * @param base   the common ancestor lines; must not be {@code null}
     * @param ours   the "ours" (left) side lines; must not be {@code null}
     * @param theirs the "theirs" (right) side lines; must not be {@code null}
     * @return a {@link MergeResult} containing the merged lines and a conflict flag
     * @throws NullPointerException if any argument is {@code null}
     */
    public static MergeResult merge(String[] base, String[] ours, String[] theirs) {
        Objects.requireNonNull(base,   "base must not be null");
        Objects.requireNonNull(ours,   "ours must not be null");
        Objects.requireNonNull(theirs, "theirs must not be null");

        // Compute diffs from base to each side
        List<DiffHunk> oursHunks   = MyersDiff.diff(base, ours);
        List<DiffHunk> theirsHunks = MyersDiff.diff(base, theirs);

        // Build per-base-line change maps.
        // changeOurs[i]   = what replaces base[i] in "ours"   (null = unchanged)
        // changeTheirs[i] = what replaces base[i] in "theirs" (null = unchanged)
        // A null entry means "keep the base line".
        // A non-null entry contains the replacement lines (may be empty = deletion).
        BaseLineChange[] oursChanges   = buildChangeMap(base, oursHunks);
        BaseLineChange[] theirsChanges = buildChangeMap(base, theirsHunks);

        List<String> result = new ArrayList<>();
        boolean hasConflicts = false;

        for (int i = 0; i < base.length; i++) {
            BaseLineChange oursChange   = oursChanges[i];
            BaseLineChange theirsChange = theirsChanges[i];

            boolean oursModified   = oursChange   != null;
            boolean theirsModified = theirsChange != null;

            if (!oursModified && !theirsModified) {
                // Neither side touched this line — emit base line
                result.add(base[i]);
            } else if (oursModified && !theirsModified) {
                // Only ours changed — auto-merge: emit ours replacement
                result.addAll(oursChange.replacementLines());
            } else if (!oursModified) {
                // Only theirs changed — auto-merge: emit theirs replacement
                result.addAll(theirsChange.replacementLines());
            } else {
                // Both sides changed the same base line — check for identical change
                List<String> oursLines   = oursChange.replacementLines();
                List<String> theirsLines = theirsChange.replacementLines();

                if (oursLines.equals(theirsLines)) {
                    // Identical change on both sides — auto-merge
                    result.addAll(oursLines);
                } else {
                    // True conflict
                    hasConflicts = true;
                    result.add(MARKER_OURS);
                    result.addAll(oursLines);
                    result.add(MARKER_SEP);
                    result.addAll(theirsLines);
                    result.add(MARKER_THEIRS);
                }
            }
        }

        // Handle pure insertions at the end (ADD lines with no preceding base line)
        // These are captured in the trailing-insertion slots of the change maps.
        appendTrailingInsertions(result, oursChanges, theirsChanges, base.length);

        return new MergeResult(result, hasConflicts);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Builds a per-base-line change map from a list of diff hunks.
     *
     * <p>The returned array has one entry per base line (0-indexed). An entry is
     * {@code null} if the base line is unchanged (CONTEXT). Otherwise it contains
     * a {@link BaseLineChange} describing what replaces the base line.
     *
     * <p>The algorithm walks through each hunk's diff lines in order:
     * <ul>
     *   <li>CONTEXT lines → no change recorded.</li>
     *   <li>REMOVE lines → mark the base line as changed; collect any immediately
     *       following ADD lines as the replacement.</li>
     *   <li>Pure ADD lines (not preceded by a REMOVE in the same hunk) → attach
     *       them as an insertion after the preceding base line.</li>
     * </ul>
     *
     * @param base  the base lines
     * @param hunks the diff hunks
     * @return array of {@link BaseLineChange} entries, one per base line
     */
    private static BaseLineChange[] buildChangeMap(String[] base, List<DiffHunk> hunks) {
        // One slot per base line, plus one extra slot for insertions after the last line
        BaseLineChange[] changes = new BaseLineChange[base.length + 1];

        for (DiffHunk hunk : hunks) {
            List<DiffLine> lines = hunk.getLines();
            int i = 0;
            // Track the last base line we saw (for anchoring pure ADD lines)
            int lastBaseIdx = -1; // 0-based

            while (i < lines.size()) {
                DiffLine dl = lines.get(i);

                if (dl.type() == LineType.CONTEXT) {
                    lastBaseIdx = dl.baseLineNo() - 1; // 0-based
                    i++;

                } else if (dl.type() == LineType.REMOVE) {
                    int baseIdx = dl.baseLineNo() - 1; // 0-based
                    lastBaseIdx = baseIdx;

                    // Collect all ADD lines that immediately follow this REMOVE
                    List<String> replacement = new ArrayList<>();
                    i++;
                    while (i < lines.size() && lines.get(i).type() == LineType.ADD) {
                        replacement.add(lines.get(i).content());
                        i++;
                    }

                    if (baseIdx >= 0 && baseIdx < base.length) {
                        // Mark this base line as replaced (or deleted if replacement is empty)
                        changes[baseIdx] = BaseLineChange.replacement(replacement);
                    }

                } else if (dl.type() == LineType.ADD) {
                    // Pure insertion (not preceded by a REMOVE in this hunk).
                    // Anchor it to the slot after the last seen base line.
                    int anchorSlot = lastBaseIdx + 1; // 0-based slot after lastBaseIdx
                    // anchorSlot == 0 means "before the first base line"
                    // anchorSlot == base.length means "after the last base line"

                    // Collect all consecutive ADD lines
                    List<String> insertions = new ArrayList<>();
                    while (i < lines.size() && lines.get(i).type() == LineType.ADD) {
                        insertions.add(lines.get(i).content());
                        i++;
                    }

                    // Attach as a pure insertion at this anchor slot.
                    // We use a special "insertion" change that doesn't consume a base line.
                    if (anchorSlot >= 0 && anchorSlot <= base.length) {
                        BaseLineChange existing = changes[anchorSlot];
                        if (existing == null) {
                            changes[anchorSlot] = BaseLineChange.insertion(insertions);
                        } else {
                            // Append to existing insertions
                            List<String> combined = new ArrayList<>(existing.replacementLines());
                            combined.addAll(insertions);
                            changes[anchorSlot] = new BaseLineChange(combined, existing.isPureInsertion());
                        }
                    }
                } else {
                    i++;
                }
            }
        }

        return changes;
    }

    /**
     * Appends any trailing insertions (pure ADD lines anchored after the last base line)
     * to the result.
     *
     * @param result        the output list to append to
     * @param oursChanges   the ours change map
     * @param theirsChanges the theirs change map
     * @param baseLen       the number of base lines
     */
    private static void appendTrailingInsertions(List<String> result,
                                                  BaseLineChange[] oursChanges,
                                                  BaseLineChange[] theirsChanges,
                                                  int baseLen) {
        // The extra slot at index baseLen holds insertions after the last base line
        BaseLineChange oursTrailing   = baseLen < oursChanges.length   ? oursChanges[baseLen]   : null;
        BaseLineChange theirsTrailing = baseLen < theirsChanges.length ? theirsChanges[baseLen] : null;

        if (oursTrailing != null && oursTrailing.isPureInsertion()) {
            result.addAll(oursTrailing.replacementLines());
        }
        if (theirsTrailing != null && theirsTrailing.isPureInsertion()) {
            result.addAll(theirsTrailing.replacementLines());
        }
    }

    // =========================================================================
    // Internal types
    // =========================================================================

    /**
     * Describes what replaces a single base line in one side of the diff.
     *
     * @param replacementLines the lines that replace the base line (may be empty for deletion)
     * @param isPureInsertion  {@code true} if this is a pure insertion (no base line consumed)
     */
    private record BaseLineChange(List<String> replacementLines, boolean isPureInsertion) {

        /** Canonical constructor — defensively copies the list. */
        BaseLineChange {
            replacementLines = List.copyOf(replacementLines);
        }

        /** Convenience constructor for a replacement (not a pure insertion). */
        static BaseLineChange replacement(List<String> lines) {
            return new BaseLineChange(lines, false);
        }

        /** Convenience constructor for a pure insertion. */
        static BaseLineChange insertion(List<String> lines) {
            return new BaseLineChange(lines, true);
        }
    }
}
