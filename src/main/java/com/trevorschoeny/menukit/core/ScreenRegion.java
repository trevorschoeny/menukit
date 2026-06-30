package com.trevorschoeny.menukit.core;

/**
 * A spot on the SCREEN edge for anchoring a {@link Panel} as chrome on a
 * standalone {@link com.trevorschoeny.menukit.screen.MKScreen} or a custom
 * container screen — independent of the screen's content. The nine spots are the
 * four corners, the four edge midpoints, and the centre, each inset by
 * {@link RegionConstants#SCREEN_EDGE_MARGIN} from the edges it touches.
 *
 * <p>Where {@link PanelPosition#region(MenuRegion)} anchors a panel to the MAIN
 * panel's bounds (so it moves with the content frame), {@link
 * PanelPosition#screenAnchor(ScreenRegion)} pins a panel to a fixed SCREEN spot —
 * so chrome like a "&lt; Back" button (TOP_LEFT) or a screen title (TOP_CENTER)
 * stays put regardless of how the content sizes, wraps, or scrolls. Screen-
 * anchored panels are excluded from the layout's extent, exactly as overlay
 * (centred) panels are.
 *
 * <p>This replaces the earlier 4-corner {@code ScreenCorner}: a title wants the
 * TOP_CENTER spot, a status note the BOTTOM_CENTER spot, etc., and four corners
 * couldn't express those.
 *
 * <p><b>Note — one screen-edge vocabulary, two names (for now).</b> {@link
 * VanillaScreenRegion} is the identical nine-spot screen-edge vocabulary used by
 * the vanilla-screen INJECTION path (panels MenuKit injects onto vanilla Options
 * / Controls screens). The two should collapse into a single {@code ScreenRegion}
 * type, but {@code VanillaScreenRegion} is consumed by sibling mods (Keybindery)
 * outside this library's change scope, so the unification is deferred to a
 * cross-unit pass. Until then they intentionally mirror each other (same nine
 * values, same {@link RegionMath} placement geometry).
 */
public enum ScreenRegion {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    LEFT_CENTER,
    CENTER,
    RIGHT_CENTER,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT
}
