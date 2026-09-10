package com.trevlar.menukit.core;

/**
 * A region <b>on</b> a reference rectangle: one of nine spots — the four corners,
 * the four edge midpoints, and the centre — inset from the edge.
 *
 * <p>This names the <em>vocabulary</em>, not the reference. The reference is
 * decided by the call site: a HUD panel is placed against the game window, and so
 * is a standalone screen's chrome. One enum serves both. (Before 5.0.0 this was
 * two identical enums, {@code HudRegion} and {@code ScreenRegion}.)
 *
 * <p><b>Same vocabulary, different resolution.</b> Collapsing the enum does not
 * collapse the resolvers, and that is the point. The HUD path offsets
 * {@link #CENTER} below the crosshair by
 * {@link RegionConstants#CENTER_CROSSHAIR_CLEARANCE} and returns empty when a
 * panel overflows; the screen-chrome path always resolves and lets a too-big panel
 * overhang. Reference and vocabulary together still do not determine the math —
 * the context does.
 *
 * <p>Its counterpart is {@link OutsideRegion}, the eleven placements
 * <em>outside</em> a rectangle. The two are shaped differently and do not merge.
 */
public enum InsideRegion {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    LEFT_CENTER,
    /** Centered horizontally and vertically. Flows down (injection path). */
    CENTER,
    RIGHT_CENTER,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT;

    /**
     * Returns a {@link RegionAnchor} pairing this region with an explicit stacking
     * priority. Lower priority renders first (closer to the region's anchor edge);
     * default is {@link RegionAnchor#DEFAULT_PRIORITY}.
     *
     * <p>Consumed only by the vanilla-screen INJECTION path (which stacks several
     * panels in one region); the single-panel chrome path
     * ({@code screenAnchor}/{@code resolveScreenRegion}) ignores priority. Mirrors
     * {@link HudRegion#priority(int)} / {@link MenuRegion#priority(int)}, so all
     * stacking-family region enums present an identical surface.
     */
    public RegionAnchor<InsideRegion> priority(int priority) {
        return new RegionAnchor<>(this, priority);
    }
}
