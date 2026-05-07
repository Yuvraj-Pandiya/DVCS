package com.dvcs.diff.algorithm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ThreeWayMerge}.
 *
 * <p>Covers:
 * <ul>
 *   <li>Auto-merge with no conflict when ours and theirs touch different lines.</li>
 *   <li>Conflict detection with standard conflict markers for a single-line conflict.</li>
 *   <li>Adjacent edits (ours edits line 2, theirs edits line 4) auto-merge without conflict.</li>
 * </ul>
 */
class ThreeWayMergeTest {

    // -------------------------------------------------------------------------
    // Test 1: auto-merge, no conflict — all changes applied
    // -------------------------------------------------------------------------

    /**
     * base = ["line1", "line2", "line3"]
     * ours = ["line1", "line2", "line3", "line4"]  (appended "line4")
     * theirs = ["LINE1", "line2", "line3"]          (changed "line1" → "LINE1")
     *
     * Expected: hasConflicts == false, merged output contains all four lines.
     */
    @Test
    void autoMerge_noConflict_allChangesApplied() {
        String[] base   = {"line1", "line2", "line3"};
        String[] ours   = {"line1", "line2", "line3", "line4"};
        String[] theirs = {"LINE1", "line2", "line3"};

        MergeResult result = ThreeWayMerge.merge(base, ours, theirs);

        assertFalse(result.hasConflicts(), "Expected no conflicts");

        List<String> merged = result.getMergedLines();
        assertTrue(merged.contains("LINE1"), "Merged output should contain theirs' change 'LINE1'");
        assertTrue(merged.contains("line2"), "Merged output should contain unchanged 'line2'");
        assertTrue(merged.contains("line3"), "Merged output should contain unchanged 'line3'");
        assertTrue(merged.contains("line4"), "Merged output should contain ours' addition 'line4'");
    }

    // -------------------------------------------------------------------------
    // Test 2: single-line conflict — conflict markers present
    // -------------------------------------------------------------------------

    /**
     * base = ["x"]
     * ours = ["y"]
     * theirs = ["z"]
     *
     * Both sides replace the only base line with different content → conflict.
     * Expected: hasConflicts == true, merged lines contain all three conflict markers.
     */
    @Test
    void singleLineConflict_conflictMarkersPresent() {
        String[] base   = {"x"};
        String[] ours   = {"y"};
        String[] theirs = {"z"};

        MergeResult result = ThreeWayMerge.merge(base, ours, theirs);

        assertTrue(result.hasConflicts(), "Expected conflicts to be detected");

        List<String> merged = result.getMergedLines();
        assertTrue(merged.contains("<<<<<<< OURS"),   "Merged output should contain '<<<<<<< OURS' marker");
        assertTrue(merged.contains("======="),         "Merged output should contain '=======' separator");
        assertTrue(merged.contains(">>>>>>> THEIRS"), "Merged output should contain '>>>>>>> THEIRS' marker");
    }

    // -------------------------------------------------------------------------
    // Test 3: adjacent edits — auto-merge without conflict
    // -------------------------------------------------------------------------

    /**
     * base = ["a", "b", "c", "d", "e"]
     * ours edits line 2 ("b" → "B"), theirs edits line 4 ("d" → "D").
     * The edits are on different base lines → no conflict.
     *
     * Expected: hasConflicts == false, both "B" and "D" present in merged output.
     */
    @Test
    void adjacentEdits_autoMergeWithoutConflict() {
        String[] base   = {"a", "b", "c", "d", "e"};
        String[] ours   = {"a", "B", "c", "d", "e"};  // line 2: b → B
        String[] theirs = {"a", "b", "c", "D", "e"};  // line 4: d → D

        MergeResult result = ThreeWayMerge.merge(base, ours, theirs);

        assertFalse(result.hasConflicts(), "Adjacent edits on different lines should auto-merge without conflict");

        List<String> merged = result.getMergedLines();
        assertTrue(merged.contains("B"), "Merged output should contain ours' edit 'B'");
        assertTrue(merged.contains("D"), "Merged output should contain theirs' edit 'D'");
        assertTrue(merged.contains("a"), "Merged output should contain unchanged 'a'");
        assertTrue(merged.contains("c"), "Merged output should contain unchanged 'c'");
        assertTrue(merged.contains("e"), "Merged output should contain unchanged 'e'");
    }
}
