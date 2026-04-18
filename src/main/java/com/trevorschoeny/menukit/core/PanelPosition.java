package com.trevorschoeny.menukit.core;

import org.jspecify.annotations.Nullable;

/**
 * Describes how a panel is positioned relative to the screen layout.
 *
 * <p>Modes:
 * <ul>
 *   <li>{@link Mode#BODY} — stacks vertically in the main column (default)</li>
 *   <li>{@code rightOf("panelId")} etc. — offsets from a named anchor panel</li>
 *   <li>{@link Mode#IN_REGION} — positions via a named {@link StandaloneRegion}
 *       (reserved API; solver deferred — see M5 design doc §3.6)</li>
 * </ul>
 *
 * <p>This is declarative metadata. The screen reads it during layout
 * computation to determine where each panel goes.
 */
public record PanelPosition(Mode mode,
                             @Nullable String anchorPanelId,
                             @Nullable StandaloneRegion region) {

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
         * Positions via a named {@link StandaloneRegion} anchored to the
         * screen's main panel. Reserved API — the layout solver for this mode
         * is deferred until a concrete consumer surfaces. See M5 design doc §3.6.
         */
        IN_REGION
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
     * Positions the panel via a named standalone region. Reserved API —
     * the layout solver for {@link Mode#IN_REGION} is deferred (see M5
     * design doc §3.6). Panels declared with this mode are valid API inputs
     * but will not resolve to concrete coordinates until the solver ships.
     */
    public static PanelPosition inRegion(StandaloneRegion region) {
        return new PanelPosition(Mode.IN_REGION, null, region);
    }
}
