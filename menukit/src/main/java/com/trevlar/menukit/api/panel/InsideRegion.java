package com.trevlar.menukit.api.panel;

import com.trevlar.menukit.core.RegionMath;

/**
 * A region <b>on</b> a reference rectangle: one of nine spots, the four corners,
 * the four edge midpoints, and the centre, inset from the edge.
 *
 * <p>This names the <em>vocabulary</em>, not the reference. The reference is
 * decided by the call site: a HUD panel is placed against the game window, and so
 * is a standalone screen's chrome. One enum serves both. (Before 5.0.0 this was
 * two identical enums, {@code HudRegion} and {@code ScreenRegion}.)
 *
 * <p><b>One resolver, the context's insets.</b> Every host resolves these spots
 * through {@link RegionMath#resolveInside}; what differs per context is only its
 * {@link RegionMath.Insets}: the HUD sits {@link RegionConstants#EDGE_INSET} in and
 * drops {@link #CENTER} below the crosshair by
 * {@link RegionConstants#CENTER_CROSSHAIR_CLEARANCE}, a vanilla screen uses the same
 * inset without the crosshair, and a standalone screen's chrome sits
 * {@link RegionConstants#SCREEN_EDGE_MARGIN} in and never hides for overflow.
 *
 * <p>Stacking order among siblings on one spot is the panel's
 * {@link PanelPosition#priority(int)}.
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
    BOTTOM_RIGHT
}
