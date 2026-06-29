package com.trevorschoeny.menukit.core;

/**
 * Single source of truth for the region-positioning layout constants —
 * stacking gaps and edge insets — shared across all four region contexts
 * ({@link MenuRegion}, {@link SlotGroupRegion}, {@link HudRegion},
 * {@link VanillaScreenRegion}) and both pure math helpers
 * ({@link RegionMath}, {@link SlotGroupRegionMath}).
 *
 * <h2>Why a shared home (Phase 3b — Item 4c centralize)</h2>
 *
 * These constants previously lived inconsistently: {@link RegionMath} held
 * {@code STACK_GAP = 2} (consumed by the menu, slot-group, AND HUD prefix
 * paths), {@link VanillaScreenRegion} held its own {@code STACK_GAP = 4} +
 * {@code EDGE_INSET = 4}, and {@link HudRegion} held {@code EDGE_INSET = 4}
 * but no stack gap of its own (the HUD prefix path borrowed
 * {@code RegionMath.STACK_GAP}). Four declarations, three of which had to
 * stay in lock-step by hand. This class hoists them into one place so a
 * value lives in exactly one spot.
 *
 * <h2>Per-context values are PRESERVED, not collapsed</h2>
 *
 * Centralizing must not move a single pixel — the gap values Trev has seen
 * in-game stay exactly as they were. So the two distinct gaps are kept as
 * two NAMED constants rather than unified:
 *
 * <ul>
 *   <li>{@link #MENU_STACK_GAP} (= 2) — the menu / slot-group / HUD stacking
 *       gap. These three contexts all stacked at a 2px gap before the hoist
 *       (the menu + slot-group math used {@code RegionMath.STACK_GAP}; the
 *       HUD prefix path likewise used {@code RegionMath.STACK_GAP}), so they
 *       all point here.</li>
 *   <li>{@link #SCREEN_STACK_GAP} (= 4) — the vanilla non-container screen
 *       stacking gap. Only the vanilla-screen prefix path used the wider 4px
 *       gap before the hoist, so it alone points here.</li>
 * </ul>
 *
 * <p>The two values are deliberately NOT collapsed into one — doing so would
 * shift either the menu/slot-group/HUD layouts (to 4) or the vanilla-screen
 * layout (to 2), a visual change Trev hasn't approved.
 *
 * <h2>Edge inset</h2>
 *
 * {@link #EDGE_INSET} (= 4) is the screen-edge inset shared by the HUD and
 * vanilla-screen contexts (both previously declared their own identical
 * {@code EDGE_INSET = 4}, matching vanilla's F3 debug-overlay convention).
 * The menu + slot-group contexts anchor to a frame rather than a screen
 * edge, so they have no edge inset.
 */
public final class RegionConstants {

    private RegionConstants() {}

    /**
     * Stacking gap (pixels, GUI-scaled) for the menu, slot-group, and HUD
     * contexts. {@code = 2}. Preserves the pre-hoist
     * {@code RegionMath.STACK_GAP} value that all three contexts used.
     */
    public static final int MENU_STACK_GAP = 2;

    /**
     * Stacking gap (pixels, GUI-scaled) for the vanilla non-container screen
     * context. {@code = 4}. Preserves the pre-hoist
     * {@code VanillaScreenRegion.STACK_GAP} value.
     */
    public static final int SCREEN_STACK_GAP = 4;

    /**
     * Screen-edge inset (pixels, GUI-scaled) for the HUD + vanilla-screen
     * contexts. {@code = 4}, matching vanilla's F3 debug-overlay convention.
     */
    public static final int EDGE_INSET = 4;
}
