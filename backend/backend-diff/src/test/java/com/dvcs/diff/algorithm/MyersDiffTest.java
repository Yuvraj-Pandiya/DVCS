package com.dvcs.diff.algorithm;

import com.dvcs.diff.model.DiffHunk;
import com.dvcs.diff.model.DiffLine;
import com.dvcs.diff.model.LineType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link MyersDiff} and the round-trip property with {@link PatchApplier}.
 *
 * <p>Requirement 9.9: Diff Engine — MyersDiffTest.
 */
@DisplayName("MyersDiff")
class MyersDiffTest {

    // =========================================================================
    // Null / empty inputs
    // =========================================================================

    @Nested
    @DisplayName("null inputs")
    class NullInputs {

        @Test
        @DisplayName("null base throws NullPointerException")
        void nullBaseThrows() {
            assertThatNullPointerException()
                    .isThrownBy(() -> MyersDiff.diff(null, new String[]{"a"}))
                    .withMessageContaining("base");
        }

        @Test
        @DisplayName("null head throws NullPointerException")
        void nullHeadThrows() {
            assertThatNullPointerException()
                    .isThrownBy(() -> MyersDiff.diff(new String[]{"a"}, null))
                    .withMessageContaining("head");
        }
    }

    @Nested
    @DisplayName("empty arrays")
    class EmptyArrays {

        @Test
        @DisplayName("diff of two empty arrays returns empty list")
        void bothEmpty() {
            List<DiffHunk> hunks = MyersDiff.diff(new String[0], new String[0]);
            assertThat(hunks).isEmpty();
        }

        @Test
        @DisplayName("diff of empty base and non-empty head returns ADD hunk")
        void emptyBaseNonEmptyHead() {
            String[] head = {"line1", "line2"};
            List<DiffHunk> hunks = MyersDiff.diff(new String[0], head);
            assertThat(hunks).hasSize(1);
            DiffHunk hunk = hunks.get(0);
            assertThat(hunk.getLines()).allMatch(l -> l.type() == LineType.ADD);
            assertThat(hunk.getLines()).extracting(DiffLine::content)
                    .containsExactly("line1", "line2");
        }

        @Test
        @DisplayName("diff of non-empty base and empty head returns REMOVE hunk")
        void nonEmptyBaseEmptyHead() {
            String[] base = {"line1", "line2"};
            List<DiffHunk> hunks = MyersDiff.diff(base, new String[0]);
            assertThat(hunks).hasSize(1);
            DiffHunk hunk = hunks.get(0);
            assertThat(hunk.getLines()).allMatch(l -> l.type() == LineType.REMOVE);
        }
    }

    // =========================================================================
    // Identical arrays
    // =========================================================================

    @Nested
    @DisplayName("identical arrays")
    class IdenticalArrays {

        @Test
        @DisplayName("diff of identical single-line arrays returns empty list")
        void identicalSingleLine() {
            String[] arr = {"hello"};
            assertThat(MyersDiff.diff(arr, arr)).isEmpty();
        }

        @Test
        @DisplayName("diff of identical multi-line arrays returns empty list")
        void identicalMultiLine() {
            String[] arr = {"a", "b", "c", "d", "e"};
            assertThat(MyersDiff.diff(arr, arr)).isEmpty();
        }

        @Test
        @DisplayName("diff of equal but distinct array instances returns empty list")
        void equalDistinctInstances() {
            String[] base = {"x", "y", "z"};
            String[] head = {"x", "y", "z"};
            assertThat(MyersDiff.diff(base, head)).isEmpty();
        }
    }

    // =========================================================================
    // Single-line change
    // =========================================================================

    @Nested
    @DisplayName("single-line change")
    class SingleLineChange {

        @Test
        @DisplayName("replacing the only line produces one hunk with one REMOVE and one ADD")
        void replaceOnlyLine() {
            String[] base = {"old line"};
            String[] head = {"new line"};

            List<DiffHunk> hunks = MyersDiff.diff(base, head);

            assertThat(hunks).hasSize(1);
            DiffHunk hunk = hunks.get(0);

            long removes = hunk.getLines().stream().filter(l -> l.type() == LineType.REMOVE).count();
            long adds    = hunk.getLines().stream().filter(l -> l.type() == LineType.ADD).count();

            assertThat(removes).isEqualTo(1);
            assertThat(adds).isEqualTo(1);
        }

        @Test
        @DisplayName("replacing a middle line in a 5-line file produces one hunk")
        void replaceMiddleLine() {
            String[] base = {"a", "b", "c", "d", "e"};
            String[] head = {"a", "b", "CHANGED", "d", "e"};

            List<DiffHunk> hunks = MyersDiff.diff(base, head);

            assertThat(hunks).hasSize(1);
            DiffHunk hunk = hunks.get(0);

            assertThat(hunk.hasRemovals()).isTrue();
            assertThat(hunk.hasAdditions()).isTrue();

            // The changed line should be present
            assertThat(hunk.getLines())
                    .anyMatch(l -> l.type() == LineType.REMOVE && l.content().equals("c"))
                    .anyMatch(l -> l.type() == LineType.ADD    && l.content().equals("CHANGED"));
        }

        @Test
        @DisplayName("adding a line at the end produces one ADD hunk")
        void addLineAtEnd() {
            String[] base = {"a", "b"};
            String[] head = {"a", "b", "c"};

            List<DiffHunk> hunks = MyersDiff.diff(base, head);

            assertThat(hunks).hasSize(1);
            assertThat(hunks.get(0).getLines())
                    .anyMatch(l -> l.type() == LineType.ADD && l.content().equals("c"));
        }

        @Test
        @DisplayName("removing the first line produces one REMOVE hunk")
        void removeFirstLine() {
            String[] base = {"first", "second", "third"};
            String[] head = {"second", "third"};

            List<DiffHunk> hunks = MyersDiff.diff(base, head);

            assertThat(hunks).hasSize(1);
            assertThat(hunks.get(0).getLines())
                    .anyMatch(l -> l.type() == LineType.REMOVE && l.content().equals("first"));
        }
    }

    // =========================================================================
    // Multi-hunk diff on 50-line files
    // =========================================================================

    @Nested
    @DisplayName("multi-hunk diff")
    class MultiHunkDiff {

        @Test
        @DisplayName("changes at lines 5 and 45 of a 50-line file produce two separate hunks")
        void twoWellSeparatedChanges() {
            String[] base = new String[50];
            String[] head = new String[50];
            for (int i = 0; i < 50; i++) {
                base[i] = "line " + (i + 1);
                head[i] = "line " + (i + 1);
            }
            // Change line 5 (index 4) and line 45 (index 44)
            head[4]  = "CHANGED line 5";
            head[44] = "CHANGED line 45";

            List<DiffHunk> hunks = MyersDiff.diff(base, head);

            // The two changes are 40 lines apart — well beyond the 6-line merge window
            assertThat(hunks).hasSizeGreaterThanOrEqualTo(2);

            // Verify both changed lines appear in the hunks
            boolean foundChange5  = hunks.stream().flatMap(h -> h.getLines().stream())
                    .anyMatch(l -> l.type() == LineType.ADD && l.content().equals("CHANGED line 5"));
            boolean foundChange45 = hunks.stream().flatMap(h -> h.getLines().stream())
                    .anyMatch(l -> l.type() == LineType.ADD && l.content().equals("CHANGED line 45"));

            assertThat(foundChange5).isTrue();
            assertThat(foundChange45).isTrue();
        }

        @Test
        @DisplayName("changes at adjacent lines are merged into a single hunk")
        void adjacentChangesInOneHunk() {
            String[] base = {"a", "b", "c", "d", "e"};
            String[] head = {"a", "B", "C", "d", "e"};

            List<DiffHunk> hunks = MyersDiff.diff(base, head);

            // Both changes are adjacent — should be in one hunk
            assertThat(hunks).hasSize(1);
        }

        @Test
        @DisplayName("context lines are included around each changed region")
        void contextLinesPresent() {
            String[] base = new String[20];
            String[] head = new String[20];
            for (int i = 0; i < 20; i++) {
                base[i] = "line " + (i + 1);
                head[i] = "line " + (i + 1);
            }
            head[9] = "CHANGED line 10"; // change at index 9

            List<DiffHunk> hunks = MyersDiff.diff(base, head);
            assertThat(hunks).hasSize(1);

            DiffHunk hunk = hunks.get(0);
            long contextCount = hunk.getLines().stream()
                    .filter(l -> l.type() == LineType.CONTEXT)
                    .count();

            // Should have up to 3 context lines on each side
            assertThat(contextCount).isGreaterThanOrEqualTo(1);
        }
    }

    // =========================================================================
    // Line number correctness
    // =========================================================================

    @Nested
    @DisplayName("line number correctness")
    class LineNumbers {

        @Test
        @DisplayName("CONTEXT lines have correct 1-based line numbers in both files")
        void contextLineNumbers() {
            String[] base = {"a", "b", "c"};
            String[] head = {"a", "B", "c"};

            List<DiffHunk> hunks = MyersDiff.diff(base, head);
            assertThat(hunks).hasSize(1);

            // Line "a" is context at base=1, head=1
            DiffLine contextA = hunks.get(0).getLines().stream()
                    .filter(l -> l.type() == LineType.CONTEXT && l.content().equals("a"))
                    .findFirst().orElse(null);
            assertThat(contextA).isNotNull();
            assertThat(contextA.baseLineNo()).isEqualTo(1);
            assertThat(contextA.headLineNo()).isEqualTo(1);
        }

        @Test
        @DisplayName("REMOVE lines have valid baseLineNo and headLineNo == -1")
        void removeLineNumbers() {
            String[] base = {"x", "y"};
            String[] head = {"x"};

            List<DiffHunk> hunks = MyersDiff.diff(base, head);
            DiffLine removed = hunks.stream().flatMap(h -> h.getLines().stream())
                    .filter(l -> l.type() == LineType.REMOVE)
                    .findFirst().orElseThrow();

            assertThat(removed.baseLineNo()).isGreaterThan(0);
            assertThat(removed.headLineNo()).isEqualTo(-1);
        }

        @Test
        @DisplayName("ADD lines have valid headLineNo and baseLineNo == -1")
        void addLineNumbers() {
            String[] base = {"x"};
            String[] head = {"x", "y"};

            List<DiffHunk> hunks = MyersDiff.diff(base, head);
            DiffLine added = hunks.stream().flatMap(h -> h.getLines().stream())
                    .filter(l -> l.type() == LineType.ADD)
                    .findFirst().orElseThrow();

            assertThat(added.headLineNo()).isGreaterThan(0);
            assertThat(added.baseLineNo()).isEqualTo(-1);
        }
    }

    // =========================================================================
    // Round-trip property: apply(A, diff(A, B)) == B
    // =========================================================================

    @Nested
    @DisplayName("round-trip property")
    class RoundTrip {

        @Test
        @DisplayName("apply(A, diff(A, B)) == B for 20 random string pairs")
        void roundTripRandomPairs() {
            Random rng = new Random(42L); // fixed seed for reproducibility

            for (int trial = 0; trial < 20; trial++) {
                String[] a = randomLines(rng, 5, 15);
                String[] b = randomLines(rng, 5, 15);

                List<DiffHunk> hunks = MyersDiff.diff(a, b);
                String[] reconstructed = PatchApplier.apply(a, hunks);

                assertThat(reconstructed)
                        .as("Round-trip failed on trial %d: a=%s, b=%s",
                                trial, Arrays.toString(a), Arrays.toString(b))
                        .isEqualTo(b);
            }
        }

        @Test
        @DisplayName("apply(A, diff(A, A)) == A (identity)")
        void roundTripIdentity() {
            String[] a = {"foo", "bar", "baz"};
            List<DiffHunk> hunks = MyersDiff.diff(a, a);
            String[] result = PatchApplier.apply(a, hunks);
            assertThat(result).isEqualTo(a);
        }

        @Test
        @DisplayName("apply(empty, diff(empty, B)) == B")
        void roundTripFromEmpty() {
            String[] a = new String[0];
            String[] b = {"hello", "world"};
            List<DiffHunk> hunks = MyersDiff.diff(a, b);
            String[] result = PatchApplier.apply(a, hunks);
            assertThat(result).isEqualTo(b);
        }

        @Test
        @DisplayName("apply(A, diff(A, empty)) == empty")
        void roundTripToEmpty() {
            String[] a = {"hello", "world"};
            String[] b = new String[0];
            List<DiffHunk> hunks = MyersDiff.diff(a, b);
            String[] result = PatchApplier.apply(a, hunks);
            assertThat(result).isEqualTo(b);
        }

        @Test
        @DisplayName("round-trip with single-character lines")
        void roundTripSingleCharLines() {
            String[] a = {"a", "b", "c", "d", "e"};
            String[] b = {"a", "x", "c", "y", "e"};
            List<DiffHunk> hunks = MyersDiff.diff(a, b);
            String[] result = PatchApplier.apply(a, hunks);
            assertThat(result).isEqualTo(b);
        }

        @Test
        @DisplayName("round-trip with large files (100 lines, 20 changes)")
        void roundTripLargeFile() {
            String[] a = new String[100];
            String[] b = new String[100];
            for (int i = 0; i < 100; i++) {
                a[i] = "line " + i;
                b[i] = "line " + i;
            }
            // Make 20 changes spread across the file
            for (int i = 0; i < 20; i++) {
                b[i * 5] = "changed " + i;
            }

            List<DiffHunk> hunks = MyersDiff.diff(a, b);
            String[] result = PatchApplier.apply(a, hunks);
            assertThat(result).isEqualTo(b);
        }

        // -------------------------------------------------------------------------
        // Helper
        // -------------------------------------------------------------------------

        /**
         * Generates an array of random lines with lengths between {@code minLen}
         * and {@code maxLen}.
         */
        private static String[] randomLines(Random rng, int minLen, int maxLen) {
            int len = minLen + rng.nextInt(maxLen - minLen + 1);
            String[] lines = new String[len];
            // Use a small alphabet to increase the chance of matches (more interesting diffs)
            String[] words = {"alpha", "beta", "gamma", "delta", "epsilon",
                              "zeta", "eta", "theta", "iota", "kappa"};
            for (int i = 0; i < len; i++) {
                lines[i] = words[rng.nextInt(words.length)];
            }
            return lines;
        }
    }

    // =========================================================================
    // Task 22.1 — explicitly named test cases
    // =========================================================================

    /**
     * Task 22.1 — test 1: diff of two empty String[] returns empty list.
     */
    @Test
    @DisplayName("diffTwoEmptyArrays_returnsEmptyList")
    void diffTwoEmptyArrays_returnsEmptyList() {
        List<DiffHunk> hunks = MyersDiff.diff(new String[0], new String[0]);
        assertThat(hunks).isEmpty();
    }

    /**
     * Task 22.1 — test 2: diff of identical 10-line arrays.
     *
     * <p>The Myers implementation uses a fast-path that returns an empty list when
     * {@code Arrays.equals(base, head)} is true, so no hunks (and therefore no
     * DiffLines) are produced. The assertion verifies that no REMOVE or ADD lines
     * are present — i.e. every line that would appear is CONTEXT-equivalent.
     */
    @Test
    @DisplayName("diffIdenticalArrays_returnsOnlyContextLines")
    void diffIdenticalArrays_returnsOnlyContextLines() {
        String[] arr = new String[10];
        for (int i = 0; i < 10; i++) {
            arr[i] = "line " + (i + 1);
        }

        List<DiffHunk> hunks = MyersDiff.diff(arr, arr);

        // Fast-path: identical arrays → no hunks at all (no changes, no context needed)
        assertThat(hunks).isEmpty();

        // Confirm: if any hunks were produced, none would contain ADD or REMOVE lines
        long nonContextCount = hunks.stream()
                .flatMap(h -> h.getLines().stream())
                .filter(l -> l.type() != LineType.CONTEXT)
                .count();
        assertThat(nonContextCount).isZero();
    }

    /**
     * Task 22.1 — test 3: single-line change at line 5 (index 4) of a 10-line array
     * produces exactly one hunk containing one REMOVE and one ADD line.
     */
    @Test
    @DisplayName("singleLineChange_producesOneHunkWithRemoveAndAdd")
    void singleLineChange_producesOneHunkWithRemoveAndAdd() {
        String[] base = new String[10];
        String[] head = new String[10];
        for (int i = 0; i < 10; i++) {
            base[i] = "line " + (i + 1);
            head[i] = "line " + (i + 1);
        }
        // Change line 5 (index 4)
        head[4] = "CHANGED line 5";

        List<DiffHunk> hunks = MyersDiff.diff(base, head);

        assertThat(hunks).hasSize(1);

        DiffHunk hunk = hunks.get(0);
        long removes = hunk.getLines().stream().filter(l -> l.type() == LineType.REMOVE).count();
        long adds    = hunk.getLines().stream().filter(l -> l.type() == LineType.ADD).count();

        assertThat(removes).isEqualTo(1);
        assertThat(adds).isEqualTo(1);

        // Verify the correct lines are flagged
        assertThat(hunk.getLines())
                .anyMatch(l -> l.type() == LineType.REMOVE && l.content().equals("line 5"))
                .anyMatch(l -> l.type() == LineType.ADD    && l.content().equals("CHANGED line 5"));
    }

    /**
     * Task 22.1 — test 4: 50-line file with 3 scattered changes (lines 5, 25, 45)
     * produces exactly 3 hunks.
     *
     * <p>The changes are spaced 20 lines apart, which is well beyond the ±3 context
     * window, so they cannot be merged into fewer hunks.
     */
    @Test
    @DisplayName("scatteredChanges_producesMultipleHunks")
    void scatteredChanges_producesMultipleHunks() {
        String[] base = new String[50];
        String[] head = new String[50];
        for (int i = 0; i < 50; i++) {
            base[i] = "line " + (i + 1);
            head[i] = "line " + (i + 1);
        }
        // Change lines 5, 25, 45 (indices 4, 24, 44)
        head[4]  = "CHANGED line 5";
        head[24] = "CHANGED line 25";
        head[44] = "CHANGED line 45";

        List<DiffHunk> hunks = MyersDiff.diff(base, head);

        assertThat(hunks).hasSize(3);

        // Verify each changed line appears in the hunks
        assertThat(hunks.stream().flatMap(h -> h.getLines().stream())
                .anyMatch(l -> l.type() == LineType.ADD && l.content().equals("CHANGED line 5"))).isTrue();
        assertThat(hunks.stream().flatMap(h -> h.getLines().stream())
                .anyMatch(l -> l.type() == LineType.ADD && l.content().equals("CHANGED line 25"))).isTrue();
        assertThat(hunks.stream().flatMap(h -> h.getLines().stream())
                .anyMatch(l -> l.type() == LineType.ADD && l.content().equals("CHANGED line 45"))).isTrue();
    }

    /**
     * Task 22.1 — test 5: round-trip property {@code apply(A, diff(A, B)) == B}
     * verified for 20 randomly generated String[] pairs (5–15 lines each).
     */
    @Test
    @DisplayName("roundTripProperty_applyDiffReproducesHead")
    void roundTripProperty_applyDiffReproducesHead() {
        Random rng = new Random(12345L); // fixed seed for reproducibility
        String[] words = {"alpha", "beta", "gamma", "delta", "epsilon",
                          "zeta", "eta", "theta", "iota", "kappa"};

        for (int trial = 0; trial < 20; trial++) {
            // Generate random arrays A and B with 5–15 lines each
            int lenA = 5 + rng.nextInt(11); // [5, 15]
            int lenB = 5 + rng.nextInt(11);
            String[] a = new String[lenA];
            String[] b = new String[lenB];
            for (int i = 0; i < lenA; i++) a[i] = words[rng.nextInt(words.length)];
            for (int i = 0; i < lenB; i++) b[i] = words[rng.nextInt(words.length)];

            List<DiffHunk> hunks = MyersDiff.diff(a, b);
            String[] reconstructed = PatchApplier.apply(a, hunks);

            assertThat(Arrays.equals(b, reconstructed))
                    .as("Round-trip failed on trial %d:%n  a=%s%n  b=%s%n  reconstructed=%s",
                            trial, Arrays.toString(a), Arrays.toString(b), Arrays.toString(reconstructed))
                    .isTrue();
        }
    }

    // =========================================================================
    // Hunk structure invariants
    // =========================================================================

    @Nested
    @DisplayName("hunk structure invariants")
    class HunkInvariants {

        @Test
        @DisplayName("all hunks have at least one non-CONTEXT line")
        void hunksHaveChanges() {
            String[] base = {"a", "b", "c", "d", "e"};
            String[] head = {"a", "B", "c", "D", "e"};

            List<DiffHunk> hunks = MyersDiff.diff(base, head);
            for (DiffHunk hunk : hunks) {
                boolean hasChange = hunk.getLines().stream()
                        .anyMatch(l -> l.type() != LineType.CONTEXT);
                assertThat(hasChange)
                        .as("Hunk %s should contain at least one non-CONTEXT line", hunk)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("hunk baseStart <= baseEnd and headStart <= headEnd")
        void hunkRangesAreOrdered() {
            String[] base = {"a", "b", "c"};
            String[] head = {"a", "X", "c"};

            List<DiffHunk> hunks = MyersDiff.diff(base, head);
            for (DiffHunk hunk : hunks) {
                assertThat(hunk.getBaseStart()).isLessThanOrEqualTo(hunk.getBaseEnd());
                assertThat(hunk.getHeadStart()).isLessThanOrEqualTo(hunk.getHeadEnd());
            }
        }
    }
}
