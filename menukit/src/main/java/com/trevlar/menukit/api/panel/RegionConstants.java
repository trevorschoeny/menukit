package com.trevlar.menukit.api.panel;

import com.trevlar.menukit.core.RegionMath;

/**
 * The region layout constants, stacking gaps and edge insets, in one place. Both
 * region vocabularies ({@link OutsideRegion}, {@link InsideRegion}) and the
 * resolver, {@link RegionMath}, read them; {@link RegionMath.Insets} picks which
 * values each placement context uses. The enums carry no constants of their own.
 *
 * <h2>Two stacking gaps</h2>
 *
 * <ul>
 *   <li>{@link #MENU_STACK_GAP} (2) for the menu, slot-group and HUD contexts.</li>
 *   <li>{@link #SCREEN_STACK_GAP} (4) for vanilla screens that are not
 *       containers.</li>
 * </ul>
 *
 * <p>They stay separate because merging them would move either the menu,
 * slot-group and HUD layouts (to 4) or the vanilla-screen layout (to 2).
 *
 * <h2>Edge inset</h2>
 *
 * {@link #EDGE_INSET} (4) is the screen-edge inset for the HUD and vanilla-screen
 * contexts, matching vanilla's F3 debug overlay. The menu and slot-group contexts
 * anchor to a frame rather than a screen edge, so they have no edge inset.
 */
public final class RegionConstants {

    private RegionConstants() {}

    /**
     * Stacking gap (GUI-scaled pixels) for the menu, slot-group and HUD
     * contexts.
     */
    public static final int MENU_STACK_GAP = 2;

    /**
     * Stacking gap (GUI-scaled pixels) for vanilla screens that are not
     * containers.
     */
    public static final int SCREEN_STACK_GAP = 4;

    /**
     * Screen-edge inset (GUI-scaled pixels) for the HUD and vanilla-screen
     * contexts, matching vanilla's F3 debug overlay.
     */
    public static final int EDGE_INSET = 4;

    /**
     * Extra downward offset for {@link InsideRegion#CENTER} on the HUD, so a
     * centered HUD panel clears the crosshair.
     *
     * <p>HUD only: on a screen, {@code CENTER} resolves to the true centre. Since {@link InsideRegion} serves both contexts, a value for one of
     * them lives here rather than on the enum.
     */
    public static final int CENTER_CROSSHAIR_CLEARANCE = 16;

    /**
     * Safe margin (GUI-scaled pixels) from each screen edge. Every panel keeps at
     * least this far from the window's edges; when a panel's natural content
     * would cross it, the panel wraps instead of overflowing
     * ({@link com.trevlar.menukit.api.panel.Panel#setAvailableContentWidth}).
     *
     * <p>A little more than {@link #EDGE_INSET}, so an adaptive panel doesn't sit
     * flush against the window edge. The two differ on purpose: {@code EDGE_INSET}
     * positions edge-anchored panels, while {@code SCREEN_EDGE_MARGIN} is the
     * boundary every panel's width budget is measured against.
     */
    public static final int SCREEN_EDGE_MARGIN = 6;
}
