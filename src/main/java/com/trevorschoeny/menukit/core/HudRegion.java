package com.trevorschoeny.menukit.core;

/**
 * Named regions for positioning HUD panels against the game window.
 * Anchors to the GUI-scaled screen dimensions; stacking flows vertically
 * for every region (up or down depending on which edge the region anchors to).
 *
 * <p><b>Coverage.</b> Nine regions — three each along the top and bottom
 * edges (left/center/right), two on the left and right edges (center-aligned),
 * and one {@link #CENTER} below the crosshair.
 *
 * <p><b>Flow direction</b> — all HUD regions stack vertically:
 * <ul>
 *   <li>{@link #TOP_LEFT} / {@link #TOP_CENTER} / {@link #TOP_RIGHT} — flow down from top inset
 *   <li>{@link #LEFT_CENTER} / {@link #RIGHT_CENTER} — flow down from vertical center
 *   <li>{@link #BOTTOM_LEFT} / {@link #BOTTOM_CENTER} / {@link #BOTTOM_RIGHT} — flow up from bottom inset
 *   <li>{@link #CENTER} — flow down from {@code sh/2 + CENTER_CROSSHAIR_CLEARANCE}
 * </ul>
 *
 * <p>Edge inset constants ({@link #EDGE_INSET}, {@link #CENTER_CROSSHAIR_CLEARANCE})
 * are derived from vanilla UI conventions — see the design doc §3.5.
 */
public enum HudRegion {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    LEFT_CENTER,
    RIGHT_CENTER,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT,
    /** Below the crosshair, horizontally centered, flows down. */
    CENTER;

    /**
     * Screen-edge inset for corner/edge regions. Matches vanilla's F3
     * debug-overlay convention (4px from each edge).
     */
    public static final int EDGE_INSET = 4;

    /**
     * Vertical clearance below screen-center for the {@link #CENTER} region,
     * derived as {@code half-crosshair (8) + breathing gap (8) = 16px} in
     * GUI-scaled pixel units. Keeps CENTER-region panels clear of vanilla's
     * 15px crosshair sprite at any GUI scale.
     */
    public static final int CENTER_CROSSHAIR_CLEARANCE = 16;
}
