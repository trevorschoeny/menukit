package com.trevlar.menukit.core;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * How a wrapping {@link Tabs} strip splits its tabs into rows. A pure function of
 * the tab widths and the strip width, kept apart from {@link Tabs} so it can be
 * checked without a game (the validator's tabs probe runs it at init).
 */
@ApiStatus.Internal
public final class TabRows {

    private TabRows() {}

    /**
     * Splits the tabs, in order, into the fewest rows that fit {@code width},
     * choosing among splits with that row count the one whose rows are most
     * evenly full: least total squared leftover space. With 13 equal tabs and
     * room for 6 a row, that is 5-4-4 rather than 6-6-1. Depends only on the
     * widths and the strip width, never on the selection, so rows are stable.
     */
    public static List<int[]> balanced(int[] widths, int width) {
        int n = widths.length;
        // Fewest rows: greedy packing is optimal for the count.
        int rowCount = 1;
        for (int i = 0, used = 0; i < n; i++) {
            if (used > 0 && used + widths[i] > width) {
                rowCount++;
                used = 0;
            }
            used += widths[i];
        }
        // DP over (rows used, tabs placed): cost = sum of squared leftover.
        long inf = Long.MAX_VALUE / 4;
        long[][] best = new long[rowCount + 1][n + 1];
        int[][] cut = new int[rowCount + 1][n + 1];
        for (long[] r : best) java.util.Arrays.fill(r, inf);
        best[0][0] = 0;
        for (int k = 1; k <= rowCount; k++) {
            for (int j = 1; j <= n; j++) {
                int rowWidth = 0;
                for (int i = j - 1; i >= 0; i--) {            // row holds tabs [i, j)
                    rowWidth += widths[i];
                    if (rowWidth > width && i < j - 1) break; // a lone over-wide tab still gets a row
                    if (best[k - 1][i] >= inf) continue;
                    long slack = Math.max(0, width - rowWidth);
                    long cost = best[k - 1][i] + slack * slack;
                    if (cost < best[k][j]) {                  // strict: ties keep the earlier, fuller split
                        best[k][j] = cost;
                        cut[k][j] = i;
                    }
                }
            }
        }
        int[][] rows = new int[rowCount][];
        for (int k = rowCount, j = n; k >= 1; k--) {
            int i = cut[k][j];
            rows[k - 1] = new int[]{i, j};
            j = i;
        }
        return List.of(rows);
    }
}
