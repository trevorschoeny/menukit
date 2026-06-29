package com.trevorschoeny.menukit.core;

/**
 * A corner of the screen, for anchoring a {@link Panel} to the screen edge
 * on a standalone {@link com.trevorschoeny.menukit.screen.MKScreen} (Pass 3).
 *
 * <p>Where {@link PanelPosition#BODY} centers a panel in the stacked content
 * column, {@link PanelPosition#screenAnchor(ScreenCorner)} pins it to a fixed
 * screen corner — inset by {@link RegionConstants#SCREEN_EDGE_MARGIN} — so
 * chrome like a "Back" button stays put in the same place regardless of the
 * centered content's size. Corner-anchored panels are excluded from the body
 * stack's layout + extent, exactly as overlay (dim-behind) panels are.
 */
public enum ScreenCorner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}
