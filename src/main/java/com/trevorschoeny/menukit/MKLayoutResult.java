package com.trevorschoeny.menukit;

/**
 * Wraps the output of a panel's flow layout computation.
 *
 * <p>Replaces the old raw {@code int[][]} return type from
 * {@link MKPanelDef#computeFlowPositions()}. Positions are always real
 * coordinates (no sentinel values). The {@code active} array tracks
 * which elements participate in layout — inactive elements (disabled
 * by {@code disabledWhen} predicates or ancestor groups) have
 * {@code active[i] = false} and their position is {@code {0, 0}}.
 *
 * <p>Part of the <b>MenuKit</b> framework internals.
 *
 * @param positions     real coordinates for each element — slots at 0..S-1,
 *                      buttons at S..S+B-1, texts at S+B..S+B+T-1
 * @param active        true = element participates in layout and rendering
 * @param contentWidth  width of the laid-out content area (excluding padding)
 * @param contentHeight height of the laid-out content area (excluding padding)
 */
public record MKLayoutResult(
        int[][] positions,
        boolean[] active,
        int contentWidth,
        int contentHeight
) {
    /** Returns true if the element at {@code index} is active in this layout. */
    public boolean isActive(int index) {
        return index >= 0 && index < active.length && active[index];
    }

    /** Returns the X position of the element at {@code index}. */
    public int x(int index) { return positions[index][0]; }

    /** Returns the Y position of the element at {@code index}. */
    public int y(int index) { return positions[index][1]; }

    /**
     * Converts back to the legacy {@code int[][]} format where inactive
     * elements have {@code {-9999, -9999}} positions.
     *
     * @return positions array with -9999 sentinels for inactive elements
     * @deprecated Use {@link #isActive(int)} instead of checking for -9999.
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    public int[][] toRawPositions() {
        int[][] raw = new int[positions.length][2];
        for (int i = 0; i < positions.length; i++) {
            if (isActive(i)) {
                raw[i] = new int[]{ positions[i][0], positions[i][1] };
            } else {
                raw[i] = new int[]{ -9999, -9999 };
            }
        }
        return raw;
    }
}
