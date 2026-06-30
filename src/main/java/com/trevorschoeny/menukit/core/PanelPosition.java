package com.trevorschoeny.menukit.core;

import org.jspecify.annotations.Nullable;

/**
 * Describes how a panel is positioned within a screen's layout.
 *
 * <p><b>Movement ③ — the main-panel + region model.</b> A custom screen names
 * ONE panel as its {@link Mode#MAIN main} = its frame (centred on the screen,
 * exactly like a vanilla container's menu frame). Every other panel anchors to
 * that frame with a {@link Mode#REGION region} — the SAME {@link MenuRegion}
 * vocabulary and the SAME {@link RegionMath} resolver vanilla-injected panels
 * use against the menu frame — so siblings inherit overlay-centring (①) and
 * vertical edge-awareness + auto-scroll (②) for free. This retired the old
 * relative verbs (rightOf / leftOf / above / below), which were a second,
 * edge-unaware placement system bolted onto this enum.
 *
 * <p>Modes:
 * <ul>
 *   <li>{@link Mode#BODY} — stacks vertically in a single centred column
 *       (default; the legacy regime for simple standalone screens with no
 *       designated main panel).</li>
 *   <li>{@link Mode#MAIN} — the screen's frame: one per screen, centred, the
 *       anchor every {@code REGION} sibling resolves against.</li>
 *   <li>{@link Mode#REGION} — anchored to the main panel via a {@link MenuRegion}
 *       (RIGHT_ALIGN_TOP, BOTTOM_CENTER, …), resolved by {@link RegionMath}
 *       against the main panel's bounds.</li>
 *   <li>{@link Mode#SCREEN_ANCHOR} — pinned to a fixed screen corner (chrome).</li>
 *   <li>{@link Mode#CENTER} — a screen-centred overlay, drawn on top.</li>
 * </ul>
 *
 * <p>This is declarative metadata. The screen reads it during layout
 * computation to determine where each panel goes.
 */
public record PanelPosition(Mode mode,
                            @Nullable String anchorPanelId,
                            @Nullable ScreenCorner screenCorner,
                            @Nullable MenuRegion menuRegion) {

    /** How a panel is positioned. */
    public enum Mode {
        /** Stacks vertically in a single centred column. Default. */
        BODY,
        /**
         * The screen's frame (Movement ③) — centred on the screen window, the
         * anchor every {@link #REGION} sibling resolves against. Exactly one
         * panel per screen should be {@code MAIN}; it is the custom-screen
         * analogue of a vanilla container's menu frame.
         */
        MAIN,
        /**
         * Anchored to the {@link #MAIN} panel via a {@link MenuRegion} (Movement
         * ③). Resolved by {@link RegionMath} against the main panel's bounds —
         * the same path vanilla-injected panels take against the menu frame, so
         * the panel is edge-aware on both axes and auto-scrolls on overflow.
         */
        REGION,
        /** Offset to the right of the anchor panel. <b>Standalone-screen
         *  ({@link com.trevorschoeny.menukit.screen.MKScreen}) only</b> — custom
         *  container screens use {@link #MAIN} + {@link #REGION} (Movement ③). */
        RIGHT_OF,
        /** Offset to the left of the anchor panel. Standalone-screen only — see {@link #RIGHT_OF}. */
        LEFT_OF,
        /** Offset above the anchor panel. Standalone-screen only — see {@link #RIGHT_OF}. */
        ABOVE,
        /** Offset below the anchor panel. Standalone-screen only — see {@link #RIGHT_OF}. */
        BELOW,
        /**
         * Pinned to a fixed screen corner (Pass 3), inset by
         * {@link RegionConstants#SCREEN_EDGE_MARGIN}. Excluded from the layout's
         * extent — chrome like a "Back" button stays put regardless of content
         * size. See {@link #screenAnchor}.
         */
        SCREEN_ANCHOR,
        /**
         * Centred on the screen as an overlay — excluded from the body stack's
         * layout + extent, auto-centred on the screen window, and drawn on top
         * of the body in the overlay pass. The placement for dialogs, popovers,
         * and any panel that floats <em>over</em> the screen rather than flowing
         * in its column. This is purely a POSITION; it is independent of the
         * {@code dimsBehind}/{@code opaque}/{@code tracksAsModal} visual+input
         * flags (M9 doctrine — those compose freely with this). See
         * {@link #center}.
         */
        CENTER
    }

    /** Default position: body panel, stacks vertically. */
    public static final PanelPosition BODY =
            new PanelPosition(Mode.BODY, null, null, null);

    /**
     * The screen's main panel = its frame (Movement ③). Centred on the screen
     * window; every {@link #region} sibling anchors to its bounds. Exactly one
     * panel per screen should be {@code main()}.
     */
    public static PanelPosition main() {
        return new PanelPosition(Mode.MAIN, null, null, null);
    }

    /**
     * Anchors the panel to the main panel via {@code region} (Movement ③) — the
     * same {@link MenuRegion} vocabulary vanilla-injected panels use against the
     * menu frame. RIGHT_ALIGN_TOP sits it to the right of the main panel, top-
     * aligned; BOTTOM_CENTER below it, centred; and so on. Resolved by
     * {@link RegionMath} against the main panel's bounds, so it is edge-aware on
     * both axes and auto-scrolls when it would overflow the screen.
     */
    public static PanelPosition region(MenuRegion region) {
        return new PanelPosition(Mode.REGION, null, null, region);
    }

    // ── Standalone-screen relative offsets (MKScreen only) ──────────────────
    // Custom container screens unified onto MAIN + REGION in Movement ③; these
    // remain for pure-UI standalone screens (no menu frame), which place demo
    // panels relative to a named anchor.

    /** Position to the right of the named panel. Standalone-screen only. */
    public static PanelPosition rightOf(String panelId) {
        return new PanelPosition(Mode.RIGHT_OF, panelId, null, null);
    }

    /** Position to the left of the named panel. Standalone-screen only. */
    public static PanelPosition leftOf(String panelId) {
        return new PanelPosition(Mode.LEFT_OF, panelId, null, null);
    }

    /** Position above the named panel. Standalone-screen only. */
    public static PanelPosition above(String panelId) {
        return new PanelPosition(Mode.ABOVE, panelId, null, null);
    }

    /** Position below the named panel. Standalone-screen only. */
    public static PanelPosition below(String panelId) {
        return new PanelPosition(Mode.BELOW, panelId, null, null);
    }

    /**
     * Pins the panel to a fixed screen corner (Pass 3) — inset by
     * {@link RegionConstants#SCREEN_EDGE_MARGIN} from both edges of that corner.
     * The canonical "Back button" / screen-chrome placement: positioned
     * independently of the layout (contributes nothing to its extent), so it
     * stays put in the same screen corner no matter how content sizes or scrolls.
     *
     * <p>Honored by {@link com.trevorschoeny.menukit.screen.MKScreen}. (A
     * container screen's chrome anchors to its menu frame via the region system
     * instead.)
     */
    public static PanelPosition screenAnchor(ScreenCorner corner) {
        return new PanelPosition(Mode.SCREEN_ANCHOR, null, corner, null);
    }

    /**
     * Centres the panel on the screen as an overlay — excluded from the layout,
     * auto-centred on the screen window, and drawn on top. The canonical
     * placement for dialogs, popovers, and any panel that floats <em>over</em>
     * the screen instead of flowing in its column.
     *
     * <p>Position only: it composes freely with the {@code dimsBehind} (visual
     * dim), {@code opaque} (click-eat), and {@code tracksAsModal} (input-block)
     * flags per the M9 doctrine — a panel can be a centred overlay with any,
     * all, or none of them. Honored by
     * {@link com.trevorschoeny.menukit.screen.MKScreen}.
     */
    public static PanelPosition center() {
        return new PanelPosition(Mode.CENTER, null, null, null);
    }
}
