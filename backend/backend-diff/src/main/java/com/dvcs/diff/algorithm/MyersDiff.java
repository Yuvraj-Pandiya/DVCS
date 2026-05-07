package com.dvcs.diff.algorithm;

import com.dvcs.diff.model.DiffHunk;
import com.dvcs.diff.model.DiffLine;
import com.dvcs.diff.model.LineType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Implements the Myers O(ND) shortest-edit-script diff algorithm.
 *
 * <p>Given two sequences of lines ({@code base} and {@code head}), this class
 * computes the minimal set of insertions and deletions needed to transform
 * {@code base} into {@code head}, then groups the resulting edit operations
 * into {@link DiffHunk}s with ±3 context lines on each side.
 *
 * <h2>Algorithm overview</h2>
 * <ol>
 *   <li>Run the Myers forward search to find the shortest edit script (SES).</li>
 *   <li>Backtrack through the saved "frontier" snapshots to reconstruct the
 *       sequence of EQUAL / INSERT / DELETE operations.</li>
 *   <li>Convert the flat operation list into {@link DiffLine}s.</li>
 *   <li>Group consecutive changed lines (with ±3 context lines) into
 *       {@link DiffHunk}s.</li>
 * </ol>
 *
 * <p>No external diff library is used; all logic is implemented from scratch.
 *
 * <p>Requirement 9.3: Diff Engine — Myers diff algorithm.
 */
public class MyersDiff {

    /** Number of context lines to include on each side of a changed region. */
    private static final int CONTEXT = 3;

    // Prevent instantiation — all methods are static.
    private MyersDiff() {}

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Computes the unified diff between {@code base} and {@code head}.
     *
     * @param base the original lines; must not be {@code null}
     * @param head the new lines; must not be {@code null}
     * @return an ordered list of {@link DiffHunk}s; empty if the inputs are identical
     * @throws NullPointerException if {@code base} or {@code head} is {@code null}
     */
    public static List<DiffHunk> diff(String[] base, String[] head) {
        if (base == null) throw new NullPointerException("base must not be null");
        if (head == null) throw new NullPointerException("head must not be null");

        // Both empty → no diff
        if (base.length == 0 && head.length == 0) {
            return Collections.emptyList();
        }

        // Fast path: identical arrays
        if (Arrays.equals(base, head)) {
            return Collections.emptyList();
        }

        // Handle the case where one array is empty
        if (base.length == 0) {
            List<DiffLine> lines = new ArrayList<>(head.length);
            for (int i = 0; i < head.length; i++) {
                lines.add(DiffLine.add(head[i], i + 1));
            }
            return groupIntoHunks(lines);
        }
        if (head.length == 0) {
            List<DiffLine> lines = new ArrayList<>(base.length);
            for (int i = 0; i < base.length; i++) {
                lines.add(DiffLine.remove(base[i], i + 1));
            }
            return groupIntoHunks(lines);
        }

        List<DiffLine> lines = computeDiffLines(base, head);
        return groupIntoHunks(lines);
    }

    // =========================================================================
    // Myers algorithm — forward search + backtrack
    // =========================================================================

    /**
     * Runs the Myers O(ND) algorithm and returns the flat list of diff lines.
     *
     * <p>The algorithm maintains a "frontier" array {@code v} where
     * {@code v[k + offset]} is the furthest-reaching x-coordinate on diagonal
     * {@code k = x - y}. At each step d (edit distance), it extends the frontier
     * by one edit (insert or delete) and then follows any diagonal (equal) moves
     * as far as possible.
     *
     * <p>The trace stores a snapshot of {@code v} AFTER each d-step completes.
     * {@code trace.get(d)} = state of v after step d ran.
     *
     * @param base the original lines (non-empty)
     * @param head the new lines (non-empty)
     * @return ordered list of {@link DiffLine}s
     */
    private static List<DiffLine> computeDiffLines(String[] base, String[] head) {
        int n = base.length;
        int m = head.length;
        int max = n + m;
        int offset = max; // v[k + offset] = furthest x on diagonal k

        int[] v = new int[2 * max + 2];
        // trace.get(d) = snapshot of v AFTER step d completed
        List<int[]> trace = new ArrayList<>();

        for (int d = 0; d <= max; d++) {
            for (int k = -d; k <= d; k += 2) {
                int kIdx = k + offset;

                int x;
                if (k == -d) {
                    x = v[kIdx + 1]; // move down (insert from head)
                } else if (k == d) {
                    x = v[kIdx - 1] + 1; // move right (delete from base)
                } else if (v[kIdx - 1] < v[kIdx + 1]) {
                    x = v[kIdx + 1]; // move down (insert from head)
                } else {
                    x = v[kIdx - 1] + 1; // move right (delete from base)
                }

                int y = x - k;

                // Follow the diagonal (equal lines)
                while (x < n && y < m && base[x].equals(head[y])) {
                    x++;
                    y++;
                }

                v[kIdx] = x;

                if (x >= n && y >= m) {
                    // Found the end — save snapshot and backtrack
                    trace.add(Arrays.copyOf(v, v.length));
                    return backtrack(trace, base, head, offset, n, m);
                }
            }
            // Save snapshot AFTER this d-step
            trace.add(Arrays.copyOf(v, v.length));
        }

        // Should never reach here for finite inputs
        return backtrack(trace, base, head, offset, n, m);
    }

    /**
     * Backtracks through the saved frontier snapshots to reconstruct the diff lines.
     *
     * <p>{@code trace.get(d)} is the state of v AFTER step d completed.
     * To find what move was made at step d, we compare the state at step d
     * ({@code trace.get(d)}) with the state at step d-1 ({@code trace.get(d-1)}).
     *
     * <p>Starting from (n, m), we walk backwards:
     * <ul>
     *   <li>Find the diagonal k = x - y.</li>
     *   <li>Determine prevK: the diagonal we came from at step d.</li>
     *   <li>prevX = trace[d-1][prevK]: the x-position at the start of step d on prevK.</li>
     *   <li>Walk the diagonal from (prevX, prevY) to (x, y) emitting EQUAL lines.</li>
     *   <li>Emit the single INSERT or DELETE that moved us from prevK to k.</li>
     * </ul>
     *
     * @param trace  the list of frontier snapshots (trace[d] = state after step d)
     * @param base   the original lines
     * @param head   the new lines
     * @param offset the offset used to index the frontier array
     * @param n      length of base
     * @param m      length of head
     * @return ordered list of {@link DiffLine}s
     */
    private static List<DiffLine> backtrack(List<int[]> trace, String[] base, String[] head,
                                             int offset, int n, int m) {
        List<DiffLine> result = new ArrayList<>();

        int x = n;
        int y = m;

        // d goes from the last step down to 1 (step 0 has no edit)
        for (int d = trace.size() - 1; d >= 1; d--) {
            int[] vAfterD    = trace.get(d);     // state after step d
            int[] vAfterDm1  = trace.get(d - 1); // state after step d-1 (= before step d)
            int k = x - y;

            // Determine prevK: the diagonal we came from at step d.
            // At step d, we either moved down (insert, k = prevK - 1) or
            // right (delete, k = prevK + 1).
            int prevK;
            if (k == -d) {
                // Must have moved down (insert): prevK = k + 1
                prevK = k + 1;
            } else if (k == d) {
                // Must have moved right (delete): prevK = k - 1
                prevK = k - 1;
            } else if (vAfterDm1[k - 1 + offset] < vAfterDm1[k + 1 + offset]) {
                // Moved down (insert): prevK = k + 1
                prevK = k + 1;
            } else {
                // Moved right (delete): prevK = k - 1
                prevK = k - 1;
            }

            // prevX is where we were on diagonal prevK before the edit at step d
            int prevX = vAfterDm1[prevK + offset];
            int prevY = prevX - prevK;

            // The single edit at step d (with diagonal walk before it)
            if (prevK == k + 1) {
                // Moved down (insert): y decreased by 1
                // Diagonal walk: from (x,y) back to (prevX, prevY+1)
                while (x > prevX && y > prevY + 1) {
                    x--;
                    y--;
                    result.add(DiffLine.context(base[x], x + 1, y + 1));
                }
                y--;
                result.add(DiffLine.add(head[y], y + 1));
            } else {
                // Moved right (delete): x decreased by 1
                // Diagonal walk: from (x,y) back to (prevX+1, prevY)
                while (x > prevX + 1 && y > prevY) {
                    x--;
                    y--;
                    result.add(DiffLine.context(base[x], x + 1, y + 1));
                }
                x--;
                result.add(DiffLine.remove(base[x], x + 1));
            }
        }

        // Any remaining equal lines at the start (from the d=0 diagonal)
        while (x > 0 && y > 0) {
            x--;
            y--;
            result.add(DiffLine.context(base[x], x + 1, y + 1));
        }

        Collections.reverse(result);
        return result;
    }

    // =========================================================================
    // Group DiffLines into hunks with ±3 context lines
    // =========================================================================

    /**
     * Groups the flat list of {@link DiffLine}s into {@link DiffHunk}s.
     *
     * <p>Changed lines (ADD or REMOVE) are grouped together with up to
     * {@value #CONTEXT} context lines on each side. Adjacent groups that would
     * overlap in their context windows are merged into a single hunk.
     *
     * @param lines the flat list of diff lines
     * @return the list of hunks; empty if there are no changes
     */
    static List<DiffHunk> groupIntoHunks(List<DiffLine> lines) {
        if (lines.isEmpty()) return Collections.emptyList();

        // Find indices of all changed lines
        List<Integer> changeIndices = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).type() != LineType.CONTEXT) {
                changeIndices.add(i);
            }
        }

        if (changeIndices.isEmpty()) return Collections.emptyList();

        // Build hunk windows: [start, end] index ranges in the lines list
        List<int[]> windows = new ArrayList<>();
        int winStart = Math.max(0, changeIndices.get(0) - CONTEXT);
        int winEnd   = Math.min(lines.size() - 1, changeIndices.get(0) + CONTEXT);

        for (int i = 1; i < changeIndices.size(); i++) {
            int idx = changeIndices.get(i);
            int newStart = Math.max(0, idx - CONTEXT);
            int newEnd   = Math.min(lines.size() - 1, idx + CONTEXT);

            if (newStart <= winEnd + 1) {
                // Overlapping or adjacent — extend current window
                winEnd = Math.max(winEnd, newEnd);
            } else {
                windows.add(new int[]{winStart, winEnd});
                winStart = newStart;
                winEnd   = newEnd;
            }
        }
        windows.add(new int[]{winStart, winEnd});

        // Build a DiffHunk for each window
        List<DiffHunk> hunks = new ArrayList<>(windows.size());
        for (int[] window : windows) {
            List<DiffLine> hunkLines = lines.subList(window[0], window[1] + 1);
            hunks.add(buildHunk(hunkLines));
        }
        return hunks;
    }

    /**
     * Builds a single {@link DiffHunk} from a contiguous slice of diff lines.
     *
     * @param hunkLines the lines belonging to this hunk
     * @return the constructed hunk
     */
    private static DiffHunk buildHunk(List<DiffLine> hunkLines) {
        int baseStart = Integer.MAX_VALUE, baseEnd = 0;
        int headStart = Integer.MAX_VALUE, headEnd = 0;
        boolean hasAdd = false, hasRemove = false;

        for (DiffLine line : hunkLines) {
            if (line.baseLineNo() > 0) {
                baseStart = Math.min(baseStart, line.baseLineNo());
                baseEnd   = Math.max(baseEnd,   line.baseLineNo());
            }
            if (line.headLineNo() > 0) {
                headStart = Math.min(headStart, line.headLineNo());
                headEnd   = Math.max(headEnd,   line.headLineNo());
            }
            if (line.type() == LineType.ADD)    hasAdd    = true;
            if (line.type() == LineType.REMOVE) hasRemove = true;
        }

        if (baseStart == Integer.MAX_VALUE) baseStart = 0;
        if (headStart == Integer.MAX_VALUE) headStart = 0;

        LineType dominantType = hasAdd ? LineType.ADD
                              : hasRemove ? LineType.REMOVE
                              : LineType.CONTEXT;

        return new DiffHunk(dominantType, baseStart, baseEnd, headStart, headEnd,
                            new ArrayList<>(hunkLines));
    }
}
