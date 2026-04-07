package com.trevorschoeny.menukit.panel;

import com.trevorschoeny.menukit.MenuKit;

import java.util.List;

/**
 * Defines a tabbed container with a tab bar and switchable content groups.
 * Only the active tab's content is visible and interactive at any time.
 *
 * <p>Part of the <b>MenuKit</b> layout system.
 */
public record MKTabsDef(
        List<MKTabDef> tabs,
        TabBarPosition barPosition,
        int barThickness,           // height (TOP/BOTTOM) or width (LEFT/RIGHT)
        int tabGap,                 // gap between tab buttons
        int defaultTab,             // initially active tab index
        boolean allowKeyboardSwitch // tab/arrow key navigation
) {
    /** Where the tab bar is rendered relative to the content area. */
    public enum TabBarPosition { TOP, BOTTOM, LEFT, RIGHT }
}
