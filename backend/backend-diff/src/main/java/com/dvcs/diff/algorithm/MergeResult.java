package com.dvcs.diff.algorithm;

import java.util.List;
import java.util.Objects;

/**
 * The result of a three-way merge operation.
 *
 * <p>Contains the merged lines (which may include conflict markers if the merge
 * could not be resolved automatically) and a flag indicating whether any conflicts
 * were detected.
 *
 * <p>Requirement 9.6: Diff Engine — ThreeWayMerge result model.
 */
public class MergeResult {

    /** The merged lines, potentially containing conflict markers. */
    private final List<String> mergedLines;

    /**
     * {@code true} if the merge contains unresolved conflicts (i.e., conflict
     * markers were emitted); {@code false} if the merge was fully automatic.
     */
    private final boolean hasConflicts;

    /**
     * Constructs a {@code MergeResult}.
     *
     * @param mergedLines  the merged output lines; must not be {@code null}
     * @param hasConflicts {@code true} if conflict markers are present
     * @throws NullPointerException if {@code mergedLines} is {@code null}
     */
    public MergeResult(List<String> mergedLines, boolean hasConflicts) {
        this.mergedLines  = List.copyOf(Objects.requireNonNull(mergedLines, "mergedLines must not be null"));
        this.hasConflicts = hasConflicts;
    }

    /**
     * Returns the merged output lines.
     *
     * <p>If {@link #hasConflicts()} is {@code true}, some lines will be conflict
     * markers ({@code <<<<<<< OURS}, {@code =======}, {@code >>>>>>> THEIRS}).
     *
     * @return an unmodifiable list of merged lines; never {@code null}
     */
    public List<String> getMergedLines() {
        return mergedLines;
    }

    /**
     * Returns {@code true} if the merge contains unresolved conflicts.
     *
     * @return {@code true} if conflict markers are present in {@link #getMergedLines()}
     */
    public boolean hasConflicts() {
        return hasConflicts;
    }

    /**
     * Returns the merged lines as a {@code String[]} array.
     *
     * @return array of merged lines; never {@code null}
     */
    public String[] toArray() {
        return mergedLines.toArray(new String[0]);
    }
}
