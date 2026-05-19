package com.trevorschoeny.menukit.core;

/**
 * Named regions for positioning MK panels against the bounds of a vanilla
 * non-container {@link net.minecraft.client.gui.screens.Screen} — the
 * Options menu, Controls/KeyBinds, world-select, server-list, and the
 * like. Anchors to the screen's GUI-scaled width × height (the panel is
 * relative to the screen overlay, not to any inventory chrome — vanilla
 * non-container screens HAVE no inventory chrome).
 *
 * <h2>Parallel to {@link HudRegion}, not the same type</h2>
 *
 * The nine anchor values + the positioning math are IDENTICAL to
 * {@link HudRegion}. Why a separate enum then? Different semantic
 * context — HUD panels persist across screen opens and render against
 * the in-world overlay layer; vanilla-screen panels are per-screen-open
 * and render inside the screen's render loop. Keeping the types separate
 * future-proofs against divergence (different stacking rules, different
 * edge-inset conventions, different priority defaults). If both stay
 * identical forever, the positioning math can be hoisted to a shared
 * helper (fold-on-evidence per §0029).
 *
 * <h2>Anchors</h2>
 *
 * Nine values — three each along the top and bottom edges
 * (left / center / right), two on the left and right edges
 * (vertical-center), and one {@link #CENTER} in the middle of the screen.
 * Stacking flows VERTICALLY for every region (down from top edges,
 * up from bottom edges, down from vertical-center for the middle row).
 *
 * <p>Edge insets default to {@link #EDGE_INSET} (4px from each edge,
 * matching vanilla's F3 debug-overlay convention + the HudRegion
 * convention).
 */
public enum VanillaScreenRegion {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    LEFT_CENTER,
    RIGHT_CENTER,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT,
    /** Centered horizontally and vertically. Flows down. */
    CENTER;

    /** Screen-edge inset for corner/edge regions. */
    public static final int EDGE_INSET = 4;

    /** Vertical gap between stacked siblings sharing the same region. */
    public static final int STACK_GAP = 4;

    /**
     * Returns a {@link RegionAnchor} pairing this region with an explicit
     * stacking priority. Lower priority renders first (closer to the
     * region's anchor edge); default is {@link RegionAnchor#DEFAULT_PRIORITY}.
     *
     * <p>Mirrors {@link HudRegion#priority(int)} / {@link MenuRegion#priority(int)}.
     */
    public RegionAnchor<VanillaScreenRegion> priority(int priority) {
        return new RegionAnchor<>(this, priority);
    }
}
