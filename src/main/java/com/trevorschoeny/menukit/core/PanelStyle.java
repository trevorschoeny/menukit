package com.trevorschoeny.menukit.core;

/**
 * Visual style for a panel's background rendering.
 *
 * <p>This is declarative metadata — it describes what a panel looks like,
 * not how to draw it. The screen reads this and delegates to rendering
 * utilities (e.g., {@code MKPanel.renderPanel()}).
 *
 * <p>Part of the canonical MenuKit hierarchy:
 * Screen → Panel → SlotGroup → MenuKitSlot
 */
public enum PanelStyle {
    /** Raised — vanilla inventory panel look (9-slice sprite with rounded corners). */
    RAISED,
    /** Dark — dark translucent panel like the effects/status background. */
    DARK,
    /** Inset — dark top/left, light bottom/right. Like a pressed button. */
    INSET,
    /** None — invisible background. Panel is a pure positioning/grouping tool. */
    NONE
}
