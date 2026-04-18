package com.trevorschoeny.menukit.inject;

/**
 * Screen-space top-left coordinates of an injected panel. Produced by a
 * {@link ScreenOriginFn} from a {@link ScreenBounds}. The adapter uses this
 * as the origin for its {@link com.trevorschoeny.menukit.core.RenderContext}
 * and for translating mouse coordinates when dispatching input.
 *
 * <p><b>{@link #OUT_OF_REGION} sentinel.</b> Returned by region-aware
 * {@code ScreenOriginFn}s when the panel's stacking position exceeds the
 * region's available space. {@link ScreenPanelAdapter} checks for this
 * sentinel and short-circuits rendering + input dispatch when it's seen.
 * Consumer-written lambdas should never return this — they express explicit
 * coordinates that are always in-range.
 *
 * @param x screen-space X
 * @param y screen-space Y
 */
public record ScreenOrigin(int x, int y) {

    /**
     * Sentinel signaling "this panel does not fit in its region — skip render."
     * Uses {@link Integer#MIN_VALUE} coordinates that cannot collide with any
     * real on-screen position. Compare via identity ({@code origin == OUT_OF_REGION}).
     */
    public static final ScreenOrigin OUT_OF_REGION =
            new ScreenOrigin(Integer.MIN_VALUE, Integer.MIN_VALUE);
}
