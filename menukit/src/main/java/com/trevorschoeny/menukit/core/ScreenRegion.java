package com.trevorschoeny.menukit.core;

/**
 * A spot on the SCREEN edge — the nine-spot screen-edge vocabulary: the four
 * corners, the four edge midpoints, and the centre. This is the ONE screen-edge
 * region type in MenuKit; it backs two distinct placement subsystems that share
 * this single vocabulary:
 *
 * <ul>
 *   <li><b>Single-panel chrome</b> ({@link RegionMath#resolveScreenRegion}) — pins
 *       ONE {@link Panel} to a fixed screen spot, inset by a caller-supplied margin,
 *       independent of the screen's content. Used by
 *       {@link PanelPosition#screenAnchor(ScreenRegion)} so chrome like a
 *       "&lt; Back" button (TOP_LEFT) or a screen title (TOP_CENTER) stays put
 *       regardless of how the content sizes, wraps, or scrolls. This placement never
 *       fails (a too-big panel just overhangs the far edge) and does not stack.
 *       Screen-anchored panels are excluded from the layout's extent, exactly as
 *       overlay (centred) panels are.</li>
 *   <li><b>Vanilla-screen injection stacking</b> ({@link RegionMath#resolveVanillaScreen},
 *       via {@link com.trevorschoeny.menukit.inject.VanillaScreenPanelAdapter}) —
 *       STACKS multiple injected panels against the bounds of a vanilla
 *       non-container {@link net.minecraft.client.gui.screens.Screen} (Options,
 *       Controls/KeyBinds, world-select, server-list). Anchors to the screen's
 *       GUI-scaled width × height (no inventory chrome — vanilla non-container
 *       screens have none), inset by {@link RegionConstants#EDGE_INSET}, with same-
 *       region panels stacking VERTICALLY at {@link RegionConstants#SCREEN_STACK_GAP}
 *       (down from top edges, up from bottom edges, down from vertical-center). This
 *       placement returns {@code OUT_OF_REGION} when a panel overflows, and orders
 *       siblings by the {@link RegionAnchor} priority below.</li>
 * </ul>
 *
 * <p>Where {@link PanelPosition#region(MenuRegion)} anchors a panel to the MAIN
 * panel's bounds (so it moves with the content frame), {@code screenAnchor} pins to
 * a fixed SCREEN spot. The two resolvers above are deliberately distinct geometries
 * over the same nine names — the chrome path is single-shot, the injection path is
 * prefix-stacked — which is why a single enum serves both.
 *
 * <p>This unified {@code ScreenRegion} replaces the former pair of identical
 * nine-spot enums ({@code ScreenRegion} for chrome + {@code VanillaScreenRegion} for
 * injection): they were the same alphabet backing two subsystems, so they collapse
 * into this one type, which carries the richer {@link #priority(int)} surface the
 * injection path needs while the chrome path simply ignores it.
 *
 * <p>Layout constants live in {@link RegionConstants} (the per-enum
 * {@code STACK_GAP}/{@code EDGE_INSET} faces were hoisted there so all four region
 * enums present an identical surface): read {@link RegionConstants#SCREEN_STACK_GAP}
 * / {@link RegionConstants#EDGE_INSET} / {@link RegionConstants#SCREEN_EDGE_MARGIN}
 * directly.
 */
public enum ScreenRegion {
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
    public RegionAnchor<ScreenRegion> priority(int priority) {
        return new RegionAnchor<>(this, priority);
    }
}
