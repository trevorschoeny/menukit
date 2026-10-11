package com.trevlar.menukit.api.panel;

/**
 * Visual style for a panel's background. It describes what a panel looks like,
 * not how to draw it; the screen reads it and hands off to the rendering code
 * (for example {@code MKPanel.renderPanel()}).
 */
public enum PanelStyle {
    /** Raised, vanilla inventory panel look (9-slice sprite with rounded corners). */
    RAISED,
    /** Dark translucent panel, like the status effects background. */
    DARK,
    /** Inset: dark top and left edges, light bottom and right, like a pressed button. */
    INSET,
    /** No background. The panel only positions and groups its contents. */
    NONE
}
