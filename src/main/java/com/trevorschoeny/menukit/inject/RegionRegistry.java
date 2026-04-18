package com.trevorschoeny.menukit.inject;

import com.trevorschoeny.menukit.core.HudRegion;
import com.trevorschoeny.menukit.core.InventoryRegion;
import com.trevorschoeny.menukit.core.Panel;
import com.trevorschoeny.menukit.core.RegionMath;
import com.trevorschoeny.menukit.hud.MKHudPanelDef;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Internal registry mapping M5 regions to their registered panels. Holds
 * process-lifetime state; panels register once at mod init (typically during
 * adapter/builder construction) and remain registered until process exit.
 * See M5 design doc §6.1 for the singleton contract and §6.1a for ordering
 * semantics.
 *
 * <p><b>Not pure.</b> Stacking depends on registration order and per-frame
 * visibility. The pure math lives in
 * {@link com.trevorschoeny.menukit.core.RegionMath}; this class supplies
 * the {@code prefix} input to that math by walking its panel lists.
 *
 * <p><b>Internal only.</b> Consumers don't call this directly — the inventory
 * {@link ScreenPanelAdapter} overload and the HUD panel builder register and
 * resolve on their behalf.
 */
public final class RegionRegistry {

    private RegionRegistry() {}

    // Per-region panel lists. Registration order is append order; same-region
    // panels stack in declaration order.
    private static final Map<InventoryRegion, List<Panel>> INVENTORY =
            new EnumMap<>(InventoryRegion.class);
    private static final Map<HudRegion, List<MKHudPanelDef>> HUD =
            new EnumMap<>(HudRegion.class);

    // ── Inventory ───────────────────────────────────────────────────────

    /** Registers an inventory-context panel into a region. Called from
     *  the {@link ScreenPanelAdapter} region constructor. */
    public static void registerInventory(Panel panel, InventoryRegion region) {
        INVENTORY.computeIfAbsent(region, r -> new ArrayList<>()).add(panel);
    }

    /**
     * Computes the axial stacking prefix for a panel — total extent (plus gap)
     * contributed by visible preceding panels in the same region. Panels
     * registered before {@code self} that are hidden in the current frame
     * contribute zero, so the stack collapses naturally.
     *
     * @throws IllegalStateException if {@code self} is not registered in {@code region}
     */
    public static int axialPrefix(Panel self, InventoryRegion region) {
        List<Panel> panels = INVENTORY.getOrDefault(region, List.of());
        int prefix = 0;
        boolean horizontal = region.isHorizontalFlow();
        for (Panel p : panels) {
            if (p == self) return prefix;
            if (!p.isVisible()) continue;
            int extent = horizontal ? p.getWidth() : p.getHeight();
            prefix += extent + RegionMath.STACK_GAP;
        }
        throw new IllegalStateException(
                "Panel '" + self.getId() + "' is not registered in " + region);
    }

    /**
     * Builds a region-aware {@link ScreenOriginFn} for an inventory-context
     * panel. The returned lambda consults the registry per-frame (for the
     * stacking prefix) and the current {@link ScreenBounds} (for the menu
     * frame), producing a screen-space origin — or {@link ScreenOrigin#OUT_OF_REGION}
     * when the panel overflows its region.
     */
    public static ScreenOriginFn inventoryOriginFn(Panel panel, InventoryRegion region) {
        return bounds -> {
            int prefix = axialPrefix(panel, region);
            return RegionMath.resolveInventory(
                            region, bounds,
                            panel.getWidth(), panel.getHeight(),
                            prefix)
                    .orElse(ScreenOrigin.OUT_OF_REGION);
        };
    }

    // ── HUD ─────────────────────────────────────────────────────────────

    /** Registers a HUD panel def into a region. Called from
     *  {@link com.trevorschoeny.menukit.hud.MKHudPanel.Builder#build()}. */
    public static void registerHud(MKHudPanelDef def, HudRegion region) {
        HUD.computeIfAbsent(region, r -> new ArrayList<>()).add(def);
    }

    /**
     * Axial prefix for a HUD panel. All HUD regions flow vertically, so the
     * prefix is the sum of preceding visible panels' heights plus gaps.
     *
     * @throws IllegalStateException if {@code self} is not registered in {@code region}
     */
    public static int axialPrefix(MKHudPanelDef self, HudRegion region) {
        List<MKHudPanelDef> panels = HUD.getOrDefault(region, List.of());
        int prefix = 0;
        for (MKHudPanelDef p : panels) {
            if (p == self) return prefix;
            if (!p.showWhen().get()) continue;
            int[] size = p.computeSize();
            prefix += size[1] + RegionMath.STACK_GAP;
        }
        throw new IllegalStateException(
                "HUD panel '" + self.name() + "' is not registered in " + region);
    }
}
