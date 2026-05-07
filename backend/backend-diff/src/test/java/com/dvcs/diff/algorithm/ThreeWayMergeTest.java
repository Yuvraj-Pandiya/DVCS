package com.dvcs.diff.algorithm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link ThreeWayMerge}.
 *
 * <p>Requirement 9.10: Diff Engine — ThreeWayMergeTest.
 */
@DisplayName("ThreeWayMerge")
class ThreeWayMergeTest {

    private static final String MARKER_OURS   = "<<<<<<< OURS";
    private static final String MARKER_SEP    = "=======";
    private static final String MARKER_THEIRS = ">>>>>>> THEIRS";

    // =========================================================================
    // Null inputs
    // =========================================================================

    @Nested
    @DisplayName("null inputs")
    class NullInputs {

        @Test
        @DisplayName("null base throws NullPointerException")
        void nullBase() {
            assertThatNullPointerException()
                    .isThrownBy(() -> ThreeWayMerge.merge(null, new String[0], new String[0]))
                    .withMessageContaining("base");
        }

        @Test
        @DisplayName("null ours throws NullPointerException")
        void nullOurs() {
            assertThatNullPointerException()
                    .isThrownBy(() -> ThreeWayMerge.merge(new String[0], null, new String[0]))
                    .withMessageContaining("ours");
        }

        @Test
        @DisplayName("null theirs throws NullPointerException")
        void nullTheirs() {
            assertThatNullPointerException()
                    .isThrownBy(() -> ThreeWayMerge.merge(new String[0], new String[0], null))
                    .withMessageContaining("theirs");
        }
    }

    // =========================================================================
    // No-conflict auto-merge
    // =========================================================================

    @Nested
    @DisplayName("no-conflict auto-merge")
    class NoConflict {

        @Test
        @DisplayName("identical ours and theirs produces no conflict")
        void identicalSides() {
            String[] base   = {"a", "b", "c"};
            String[] ours   = {"a", "b", "c"};
            String[] theirs = {"a", "b", "c"};

            MergeResult result = ThreeWayMerge.merge(base, ours, theirs);

            assertThat(result.hasConflicts()).isFalse();
            assertThat(result.getMergedLines()).containsExactly("a", "b", "c");
        }

        @Test
        @DisplayName("ours changes one line, theirs unchanged — auto-merge applies ours change")
        void oursChangesOneLineTheirsUnchanged() {
            String[] base   = {"a", "b", "c"};
            String[] ours   = {"a", "OURS", "c"};
            String[] theirs = {"a", "b", "c"};

            MergeResult result = ThreeWayMerge.merge(base, ours, theirs);

            assertThat(result.hasConflicts()).isFalse();
            assertThat(result.getMergedLines()).containsExactly("a", "OURS", "c");
        }

        @Test
        @DisplayName("theirs changes one line, ours unchanged — auto-merge applies theirs change")
        void theirsChangesOneLineOursUnchanged() {
            String[] base   = {"a", "b", "c"};
            String[] ours   = {"a", "b", "c"};
            String[] theirs = {"a", "THEIRS", "c"};

            MergeResult result = ThreeWayMerge.merge(base, ours, theirs);

            assertThat(result.hasConflicts()).isFalse();
            assertThat(result.getMergedLines()).containsExactly("a", "THEIRS", "c");
        }

        @Test
        @DisplayName("ours and theirs change different lines — both changes applied")
        void differentLinesChanged() {
            String[] base   = {"a", "b", "c", "d"};
            String[] ours   = {"OURS", "b", "c", "d"};
            String[] theirs = {"a", "b", "c", "THEIRS"};

            MergeResult result = ThreeWayMerge.merge(base, ours, theirs);

            assertThat(result.hasConflicts()).isFalse();
            assertThat(result.getMergedLines()).containsExactly("OURS", "b", "c", "THEIRS");
        }

        @Test
        @DisplayName("both sides make the identical change — auto-merge without conflict")
        void bothSidesSameChange() {
            String[] base   = {"a", "b", "c"};
            String[] ours   = {"a", "SAME", "c"};
            String[] theirs = {"a", "SAME", "c"};

            MergeResult result = ThreeWayMerge.merge(base, ours, theirs);

            assertThat(result.hasConflicts()).isFalse();
            assertThat(result.getMergedLines()).containsExactly("a", "SAME", "c");
        }

        @Test
        @DisplayName("empty base, ours adds lines, theirs unchanged — ours lines appear")
        void emptyBaseOursAddsLines() {
            String[] base   = new String[0];
            String[] ours   = {"line1", "line2"};
            String[] theirs = new String[0];

            MergeResult result = ThreeWayMerge.merge(base, ours, theirs);

            assertThat(result.hasConflicts()).isFalse();
            assertThat(result.getMergedLines()).containsExactly("line1", "line2");
        }

        @Test
        @DisplayName("all three identical — no conflict, output equals base")
        void allThreeIdentical() {
            String[] base   = {"x", "y", "z"};
            String[] ours   = {"x", "y", "z"};
            String[] theirs = {"x", "y", "z"};

            MergeResult result = ThreeWayMerge.merge(base, ours, theirs);

            assertThat(result.hasConflicts()).isFalse();
            assertThat(result.getMergedLines()).containsExactly("x", "y", "z");
        }
    }

    // =========================================================================
    // Conflict detection
    // =========================================================================

    @Nested
    @DisplayName("conflict detection")
    class ConflictDetection {

        @Test
        @DisplayName("same-line conflict produces conflict markers")
        void sameLineConflict() {
            String[] base   = {"a", "b", "c"};
            String[] ours   = {"a", "OURS",   "c"};
            String[] theirs = {"a", "THEIRS", "c"};

            MergeResult result = ThreeWayMerge.merge(base, ours, theirs);

            assertThat(result.hasConflicts()).isTrue();

            List<String> merged = result.getMergedLines();
            assertThat(merged).contains(MARKER_OURS, MARKER_SEP, MARKER_THEIRS);

            // Ours content appears between OURS and SEP markers
            int oursIdx   = merged.indexOf(MARKER_OURS);
            int sepIdx    = merged.indexOf(MARKER_SEP);
            int theirsIdx = merged.indexOf(MARKER_THEIRS);

            assertThat(oursIdx).isLessThan(sepIdx);
            assertThat(sepIdx).isLessThan(theirsIdx);

            assertThat(merged.subList(oursIdx + 1, sepIdx)).contains("OURS");
            assertThat(merged.subList(sepIdx + 1, theirsIdx)).contains("THEIRS");
        }

        @Test
        @DisplayName("conflict markers appear in correct order: OURS, SEP, THEIRS")
        void conflictMarkerOrder() {
            String[] base   = {"line1"};
            String[] ours   = {"ours-version"};
            String[] theirs = {"theirs-version"};

            MergeResult result = ThreeWayMerge.merge(base, ours, theirs);

            assertThat(result.hasConflicts()).isTrue();
            List<String> merged = result.getMergedLines();

            int oursIdx   = merged.indexOf(MARKER_OURS);
            int sepIdx    = merged.indexOf(MARKER_SEP);
            int theirsIdx = merged.indexOf(MARKER_THEIRS);

            assertThat(oursIdx).isGreaterThanOrEqualTo(0);
            assertThat(sepIdx).isGreaterThan(oursIdx);
            assertThat(theirsIdx).isGreaterThan(sepIdx);
        }

        @Test
        @DisplayName("non-conflicting lines are preserved around a conflict")
        void nonConflictingLinesPreserved() {
            String[] base   = {"before", "conflict-line", "after"};
            String[] ours   = {"before", "OURS",          "after"};
            String[] theirs = {"before", "THEIRS",        "after"};

            MergeResult result = ThreeWayMerge.merge(base, ours, theirs);

            assertThat(result.hasConflicts()).isTrue();
            List<String> merged = result.getMergedLines();

            assertThat(merged).contains("before");
            assertThat(merged).contains("after");
        }

        @Test
        @DisplayName("multiple conflicts in one file — all produce markers")
        void multipleConflicts() {
            String[] base   = {"a", "b", "c", "d", "e"};
            String[] ours   = {"A", "b", "C", "d", "e"};
            String[] theirs = {"X", "b", "Y", "d", "e"};

            MergeResult result = ThreeWayMerge.merge(base, ours, theirs);

            assertThat(result.hasConflicts()).isTrue();

            // Count conflict marker occurrences
            long oursMarkers = result.getMergedLines().stream()
                    .filter(MARKER_OURS::equals).count();
            assertThat(oursMarkers).isGreaterThanOrEqualTo(2);
        }
    }

    // =========================================================================
    // Adjacent-line edits
    // =========================================================================

    @Nested
    @DisplayName("adjacent-line edits")
    class AdjacentLineEdits {

        @Test
        @DisplayName("ours edits line 1, theirs edits line 2 — both applied without conflict")
        void adjacentEditsNoConflict() {
            String[] base   = {"line1", "line2", "line3"};
            String[] ours   = {"OURS1", "line2", "line3"};
            String[] theirs = {"line1", "THEIRS2", "line3"};

            MergeResult result = ThreeWayMerge.merge(base, ours, theirs);

            assertThat(result.hasConflicts()).isFalse();
            assertThat(result.getMergedLines()).containsExactly("OURS1", "THEIRS2", "line3");
        }

        @Test
        @DisplayName("ours edits first line, theirs edits last line — both applied")
        void firstAndLastEdits() {
            String[] base   = {"first", "middle", "last"};
            String[] ours   = {"OURS-FIRST", "middle", "last"};
            String[] theirs = {"first", "middle", "THEIRS-LAST"};

            MergeResult result = ThreeWayMerge.merge(base, ours, theirs);

            assertThat(result.hasConflicts()).isFalse();
            assertThat(result.getMergedLines()).containsExactly("OURS-FIRST", "middle", "THEIRS-LAST");
        }

        @Test
        @DisplayName("ours deletes a line, theirs edits a different line — both applied")
        void deleteAndEdit() {
            String[] base   = {"a", "b", "c", "d"};
            String[] ours   = {"a", "c", "d"};       // deleted "b"
            String[] theirs = {"a", "b", "c", "D"};  // changed "d" to "D"

            MergeResult result = ThreeWayMerge.merge(base, ours, theirs);

            assertThat(result.hasConflicts()).isFalse();
            // "b" deleted by ours, "d" changed to "D" by theirs
            assertThat(result.getMergedLines()).doesNotContain("b");
            assertThat(result.getMergedLines()).contains("D");
        }
    }

    // =========================================================================
    // MergeResult API
    // =========================================================================

    @Nested
    @DisplayName("MergeResult API")
    class MergeResultApi {

        @Test
        @DisplayName("toArray() returns the same content as getMergedLines()")
        void toArrayMatchesList() {
            String[] base   = {"a", "b"};
            String[] ours   = {"a", "OURS"};
            String[] theirs = {"a", "b"};

            MergeResult result = ThreeWayMerge.merge(base, ours, theirs);

            assertThat(result.toArray())
                    .isEqualTo(result.getMergedLines().toArray(new String[0]));
        }

        @Test
        @DisplayName("getMergedLines() returns an unmodifiable list")
        void mergedLinesIsUnmodifiable() {
            MergeResult result = ThreeWayMerge.merge(
                    new String[]{"a"}, new String[]{"a"}, new String[]{"a"});

            assertThat(result.getMergedLines())
                    .isUnmodifiable();
        }
    }
}
