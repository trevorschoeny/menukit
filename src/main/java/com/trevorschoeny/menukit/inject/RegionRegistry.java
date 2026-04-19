package com.trevorschoeny.menukit.inject;

import com.trevorschoeny.menukit.core.HudRegion;
import com.trevorschoeny.menukit.core.MenuRegion;
import com.trevorschoeny.menukit.core.Panel;
import com.trevorschoeny.menukit.core.RegionMath;
import com.trevorschoeny.menukit.hud.MKHudPanelDef;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Internal registry mapping M5 regions to their registered panels. Holds
 * process-lifetime state; panels register once at mod init (typically during
 * adapter/builder construction) and remain registered until process exit.
 * See M5 design doc §6.1 for the singleton contract and §6.1a for ordering
 * semantics. See M5 design doc §12.5a for the padding + diagnostic
 * additions from Phase 12.5.
 *
 * <p><b>Not pure.</b> Stacking depends on registration order and per-frame
 * visibility. The pure math lives in
 * {@link com.trevorschoeny.menukit.core.RegionMath}; this class supplies
 * the {@code prefix} input to that math by walking its panel lists, and
 * owns the state needed for one-shot overflow diagnostics.
 *
 * <p><b>Internal only.</b> Consumers don't call this directly — the
 * MenuContext {@link ScreenPanelAdapter} overload and the HUD panel
 * builder register and resolve on their behalf.
 */
public final class RegionRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger("menukit");

    private RegionRegistry() {}

    // Per-region panel lists. Registration order is append order; same-region
    // panels stack in declaration order.
    private static final Map<MenuRegion, List<Panel>> MENU =
            new EnumMap<>(MenuRegion.class);
    private static final Map<HudRegion, List<MKHudPanelDef>> HUD =
            new EnumMap<>(HudRegion.class);

    // Per-panel content padding — set at registration time so axial-prefix
    // stacking and overflow math can include padding when deriving axial extent.
    // Keyed on Panel identity (not class) so two distinct panels with the
    // same class get independent paddings.
    private static final Map<Panel, Integer> MENU_PADDING = new HashMap<>();

    // Deduplication state for the one-shot OUT_OF_REGION warn. First
    // overflow per (panel identity, region) pair logs once; subsequent
    // overflows are silent. WeakHashMap so entries GC with the panel when
    // the consumer drops its reference.
    private static final Map<Panel, Set<MenuRegion>> WARNED_MENU =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<MKHudPanelDef, Set<HudRegion>> WARNED_HUD =
            Collections.synchronizedMap(new WeakHashMap<>());

    // ── MenuContext ─────────────────────────────────────────────────────

    /**
     * Registers a MenuContext panel into a region with a content
     * padding. Called from the {@link ScreenPanelAdapter} region constructor.
     * Padding participates in axial-prefix stacking and overflow math so the
     * region's capacity accounting is accurate.
     */
    public static void registerMenu(Panel panel, MenuRegion region, int padding) {
        MENU.computeIfAbsent(region, r -> new ArrayList<>()).add(panel);
        MENU_PADDING.put(panel, padding);
    }

    /**
     * Back-compatible overload for consumers registering without the
     * padding extension. Delegates with {@link ScreenPanelAdapter#DEFAULT_PADDING}
     * so behavior matches the default-padding adapter constructors.
     */
    public static void registerMenu(Panel panel, MenuRegion region) {
        registerMenu(panel, region, ScreenPanelAdapter.DEFAULT_PADDING);
    }

    /**
     * Computes the axial stacking prefix for a panel — total extent (plus gap)
     * contributed by visible preceding panels in the same region. Each panel's
     * axial extent includes its registered padding. Panels registered before
     * {@code self} that are hidden in the current frame contribute zero, so
     * the stack collapses naturally.
     *
     * @throws IllegalStateException if {@code self} is not registered in {@code region}
     */
    public static int axialPrefix(Panel self, MenuRegion region) {
        List<Panel> panels = MENU.getOrDefault(region, List.of());
        int prefix = 0;
        boolean horizontal = region.isHorizontalFlow();
        for (Panel p : panels) {
            if (p == self) return prefix;
            if (!p.isVisible()) continue;
            int pad = MENU_PADDING.getOrDefault(p, 0);
            int extent = (horizontal ? p.getWidth() : p.getHeight()) + 2 * pad;
            prefix += extent + RegionMath.STACK_GAP;
        }
        throw new IllegalStateException(
                "Panel '" + self.getId() + "' is not registered in " + region);
    }

    /**
     * Builds a region-aware {@link ScreenOriginFn} for a MenuContext panel. The returned lambda consults the registry per-frame (for the
     * stacking prefix and the panel's registered padding), the current
     * {@link ScreenBounds} (for the menu frame), and
     * {@link MenuChrome#of(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen)}
     * (for chrome extents outside the declared frame), producing a screen-space
     * origin — or {@link ScreenOrigin#OUT_OF_REGION} when the panel overflows
     * its region.
     *
     * <p>Chrome-extended bounds are computed once per frame and passed to
     * {@link RegionMath}. Math stays pure; chrome logic lives here.
     */
    public static ScreenOriginFn menuOriginFn(Panel panel, MenuRegion region) {
        return (bounds, screen) -> {
            int pad = MENU_PADDING.getOrDefault(panel, 0);
            int pw = panel.getWidth() + 2 * pad;
            int ph = panel.getHeight() + 2 * pad;
            int prefix = axialPrefix(panel, region);

            // Extend bounds by the screen's chrome extents on all axes —
            // treats the chrome as part of the menu's visible extent, which
            // is what consumers mean when they say "the TOP of the menu" in
            // a screen with a top tab row. Each region then anchors within
            // those chrome-extended bounds per RegionMath's usual logic.
            MenuChrome.ChromeExtents chrome = MenuChrome.of(screen);
            ScreenBounds effective = new ScreenBounds(
                    bounds.leftPos() - chrome.left(),
                    bounds.topPos() - chrome.top(),
                    bounds.imageWidth() + chrome.left() + chrome.right(),
                    bounds.imageHeight() + chrome.top() + chrome.bottom());

            var result = RegionMath.resolveMenu(region, effective, pw, ph, prefix);
            if (result.isEmpty()) {
                warnMenuOverflowOnce(panel, region, pw, ph, prefix, effective);
                return ScreenOrigin.OUT_OF_REGION;
            }
            return result.get();
        };
    }

    private static void warnMenuOverflowOnce(Panel panel, MenuRegion region,
                                                   int pw, int ph, int prefix,
                                                   ScreenBounds bounds) {
        Set<MenuRegion> warned = WARNED_MENU
                .computeIfAbsent(panel, p -> Collections.synchronizedSet(
                        EnumSet.noneOf(MenuRegion.class)));
        if (!warned.add(region)) return;
        int axisExtent = region.isHorizontalFlow() ? pw : ph;
        int axisCapacity = region.isHorizontalFlow() ? bounds.imageWidth() : bounds.imageHeight();
        LOGGER.warn(
                "[RegionRegistry] Panel '{}' overflows MenuRegion.{} — axial extent " +
                "{}px (including padding) + prefix {}px exceeds capacity {}px. " +
                "Silent OUT_OF_REGION until this panel + region pair is resized.",
                panel.getId(), region, axisExtent, prefix, axisCapacity);
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

    /**
     * Logs a one-shot warning the first time a HUD panel overflows its region.
     * Called from {@link com.trevorschoeny.menukit.MenuKit}'s HUD render loop
     * when {@link RegionMath#resolveHud} returns empty.
     */
    public static void warnHudOverflowOnce(MKHudPanelDef def, HudRegion region,
                                            int pw, int ph, int prefix,
                                            int screenWidth, int screenHeight) {
        Set<HudRegion> warned = WARNED_HUD
                .computeIfAbsent(def, d -> Collections.synchronizedSet(
                        EnumSet.noneOf(HudRegion.class)));
        if (!warned.add(region)) return;
        LOGGER.warn(
                "[RegionRegistry] HUD panel '{}' overflows HudRegion.{} — axial extent " +
                "{}px + prefix {}px exceeds the region's available height in the " +
                "{}x{} screen. Silent no-render until resized.",
                def.name(), region, ph, prefix, screenWidth, screenHeight);
    }
}
