package com.trevorschoeny.menukit.core;

import org.jspecify.annotations.Nullable;

/**
 * Describes how a panel is positioned relative to the screen layout.
 *
 * <p>Modes:
 * <ul>
 *   <li>{@link Mode#BODY} — stacks vertically in the main column (default)</li>
 *   <li>{@code rightOf("panelId")} etc. — offsets from a named anchor panel</li>
 * </ul>
 *
 * <p>This is declarative metadata. The screen reads it during layout
 * computation to determine where each panel goes.
 */
public record PanelPosition(Mode mode,
                            @Nullable String anchorPanelId,
                            @Nullable ScreenCorner screenCorner) {

    /** How a panel is positioned. */
    public enum Mode {
        /** Stacks vertically in the main column. Default. */
        BODY,
        /** Offset to the right of the anchor panel. */
        RIGHT_OF,
        /** Offset to the left of the anchor panel. */
        LEFT_OF,
        /** Offset above the anchor panel. */
        ABOVE,
        /** Offset below the anchor panel. */
        BELOW,
        /**
         * Pinned to a fixed screen corner (Pass 3), inset by
         * {@link RegionConstants#SCREEN_EDGE_MARGIN}. Excluded from the
         * centered body stack's layout + extent — chrome like a "Back" button
         * stays put regardless of content size. See {@link #screenAnchor}.
         */
        SCREEN_ANCHOR,
        /**
         * Centered on the screen as an overlay — excluded from the body stack's
         * layout + extent, auto-centered on the screen window, and drawn on top
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
    public static final PanelPosition BODY = new PanelPosition(Mode.BODY, null, null);

    /** Position to the right of the named panel. */
    public static PanelPosition rightOf(String panelId) {
        return new PanelPosition(Mode.RIGHT_OF, panelId, null);
    }

    /** Position to the left of the named panel. */
    public static PanelPosition leftOf(String panelId) {
        return new PanelPosition(Mode.LEFT_OF, panelId, null);
    }

    /** Position above the named panel. */
    public static PanelPosition above(String panelId) {
        return new PanelPosition(Mode.ABOVE, panelId, null);
    }

    /** Position below the named panel. */
    public static PanelPosition below(String panelId) {
        return new PanelPosition(Mode.BELOW, panelId, null);
    }

    /**
     * Pins the panel to a fixed screen corner (Pass 3) — inset by
     * {@link RegionConstants#SCREEN_EDGE_MARGIN} from both edges of that
     * corner. The canonical "Back button" / screen-chrome placement: the panel
     * is positioned independently of the centered body stack (and contributes
     * nothing to it), so it stays put in the same screen corner no matter how
     * the content sizes or scrolls.
     *
     * <p>Honored by {@link com.trevorschoeny.menukit.screen.MKScreen}. (A
     * container screen's chrome anchors to its menu frame via the region
     * system instead.)
     */
    public static PanelPosition screenAnchor(ScreenCorner corner) {
        return new PanelPosition(Mode.SCREEN_ANCHOR, null, corner);
    }

    /**
     * Centers the panel on the screen as an overlay — excluded from the body
     * stack, auto-centered on the screen window, and drawn on top of the body.
     * The canonical placement for dialogs, popovers, and any panel that floats
     * <em>over</em> the screen instead of flowing in its vertical column.
     *
     * <p>Position only: it composes freely with the {@code dimsBehind} (visual
     * dim), {@code opaque} (click-eat), and {@code tracksAsModal} (input-block)
     * flags per the M9 doctrine — a panel can be a centered overlay with any,
     * all, or none of them. Honored by
     * {@link com.trevorschoeny.menukit.screen.MKScreen}.
     */
    public static PanelPosition center() {
        return new PanelPosition(Mode.CENTER, null, null);
    }
}
