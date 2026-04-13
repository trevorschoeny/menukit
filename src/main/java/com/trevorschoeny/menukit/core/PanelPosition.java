package com.trevorschoeny.menukit.core;

/**
 * Describes how a panel is positioned relative to the screen layout.
 *
 * <p>Two modes:
 * <ul>
 *   <li>{@link #BODY} — stacks vertically in the main column (default)</li>
 *   <li>{@code rightOf("panelId")} etc. — offsets from a named anchor panel</li>
 * </ul>
 *
 * <p>This is declarative metadata. The screen reads it during layout
 * computation to determine where each panel goes.
 */
public record PanelPosition(Mode mode, String anchorPanelId) {

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
        BELOW
    }

    /** Default position: body panel, stacks vertically. */
    public static final PanelPosition BODY = new PanelPosition(Mode.BODY, null);

    /** Position to the right of the named panel. */
    public static PanelPosition rightOf(String panelId) {
        return new PanelPosition(Mode.RIGHT_OF, panelId);
    }

    /** Position to the left of the named panel. */
    public static PanelPosition leftOf(String panelId) {
        return new PanelPosition(Mode.LEFT_OF, panelId);
    }

    /** Position above the named panel. */
    public static PanelPosition above(String panelId) {
        return new PanelPosition(Mode.ABOVE, panelId);
    }

    /** Position below the named panel. */
    public static PanelPosition below(String panelId) {
        return new PanelPosition(Mode.BELOW, panelId);
    }
}
