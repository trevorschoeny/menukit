package com.trevorschoeny.menukit.inject;

import com.trevorschoeny.menukit.core.HudRegion;
import com.trevorschoeny.menukit.core.MenuRegion;
import com.trevorschoeny.menukit.core.Panel;
import com.trevorschoeny.menukit.core.RegionAnchor;
import com.trevorschoeny.menukit.core.RegionMath;
import com.trevorschoeny.menukit.hud.MKHudPanelDef;

import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    // Phase 16i deterministic ordering — per-panel sort metadata captured
    // at registration so axialPrefix iterates in a stable order independent
    // of mod-load order.
    //
    // Sort key: (priority asc, modId asc, registrationSeq asc) — modId comes
    // from FabricLoader via protection-domain matching at register time;
    // registrationSeq is the final tiebreaker for two panels registered
    // from the same mod with the same priority (preserves declaration
    // order within a mod, which the mod controls).
    private static final Map<Panel, Integer> MENU_PRIORITY = new HashMap<>();
    private static final Map<Panel, String> MENU_MODID = new HashMap<>();
    private static final Map<Panel, Integer> MENU_REG_SEQ = new HashMap<>();
    private static final Map<MKHudPanelDef, Integer> HUD_PRIORITY = new HashMap<>();
    private static final Map<MKHudPanelDef, String> HUD_MODID = new HashMap<>();
    private static final Map<MKHudPanelDef, Integer> HUD_REG_SEQ = new HashMap<>();
    private static int registrationCounter = 0;

    // Deduplication state for the one-shot OUT_OF_REGION warn. First
    // overflow per (panel identity, region) pair logs once; subsequent
    // overflows are silent. WeakHashMap so entries GC with the panel when
    // the consumer drops its reference.
    private static final Map<Panel, Set<MenuRegion>> WARNED_MENU =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<MKHudPanelDef, Set<HudRegion>> WARNED_HUD =
            Collections.synchronizedMap(new WeakHashMap<>());

    // Post-§0042 split: SlotGroupContext registry state + methods live in
    // menukit-containers' parallel SlotGroupRegionRegistry. The slot-group
    // registries are independent because their key shape differs
    // ((category, region) tuple vs single MenuRegion / HudRegion enum).

    // ── MenuContext ─────────────────────────────────────────────────────

    /**
     * Registers a MenuContext panel into a region with a content
     * padding and explicit priority. Called from the {@link ScreenPanelAdapter}
     * region constructor. Padding participates in axial-prefix stacking and
     * overflow math; priority + captured modId drive the deterministic sort
     * order applied at {@link #axialPrefix} time.
     */
    public static void registerMenu(Panel panel, MenuRegion region, int padding,
                                     int priority) {
        MENU.computeIfAbsent(region, r -> new ArrayList<>()).add(panel);
        MENU_PADDING.put(panel, padding);
        MENU_PRIORITY.put(panel, priority);
        MENU_MODID.put(panel, captureCallerModId());
        MENU_REG_SEQ.put(panel, registrationCounter++);
    }

    /**
     * Back-compatible overload — registers with
     * {@link RegionAnchor#DEFAULT_PRIORITY}. Consumers that don't care
     * about explicit ordering hit this path and still get a deterministic
     * sort via the modId tiebreaker.
     */
    public static void registerMenu(Panel panel, MenuRegion region, int padding) {
        registerMenu(panel, region, padding, RegionAnchor.DEFAULT_PRIORITY);
    }

    /**
     * Back-compatible overload for consumers registering without the
     * padding extension. Delegates with {@link ScreenPanelAdapter#DEFAULT_PADDING}
     * so behavior matches the default-padding adapter constructors.
     */
    public static void registerMenu(Panel panel, MenuRegion region) {
        registerMenu(panel, region, ScreenPanelAdapter.DEFAULT_PADDING,
                RegionAnchor.DEFAULT_PRIORITY);
    }

    /**
     * Computes the axial stacking prefix for a panel — total extent (plus gap)
     * contributed by visible preceding panels in the same region. Each panel's
     * axial extent includes its registered padding. Panels registered before
     * {@code self} that are hidden in the current frame contribute zero, so
     * the stack collapses naturally.
     *
     * <p>Phase 16i: iteration order is a deterministic sort, not registration
     * order. Sort key is {@code (priority asc, modId asc, registrationSeq asc)}
     * — lower priority renders first (closer to the region's anchor edge),
     * with modId as the natural tiebreaker for two consumers sharing a
     * priority and registration sequence as a final stabilizer for two
     * panels from the same mod with the same priority.
     *
     * @throws IllegalStateException if {@code self} is not registered in {@code region}
     */
    public static int axialPrefix(Panel self, MenuRegion region) {
        List<Panel> panels = sortedMenuPanels(region);
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
     * Returns the panels registered in {@code region}, sorted by the
     * deterministic key (priority, modId, registrationSeq). Phase 16i.
     */
    private static List<Panel> sortedMenuPanels(MenuRegion region) {
        List<Panel> panels = MENU.getOrDefault(region, List.of());
        if (panels.size() <= 1) return panels;
        List<Panel> sorted = new ArrayList<>(panels);
        sorted.sort(Comparator
                .comparingInt((Panel p) -> MENU_PRIORITY.getOrDefault(p, RegionAnchor.DEFAULT_PRIORITY))
                .thenComparing(p -> MENU_MODID.getOrDefault(p, ""))
                .thenComparingInt(p -> MENU_REG_SEQ.getOrDefault(p, Integer.MAX_VALUE)));
        return sorted;
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

    /** Registers a HUD panel def into a region with explicit priority. Called
     *  from {@link com.trevorschoeny.menukit.hud.MKHudPanel.Builder#build()}.
     *  Phase 16i: priority + captured modId drive deterministic sort. */
    public static void registerHud(MKHudPanelDef def, HudRegion region, int priority) {
        HUD.computeIfAbsent(region, r -> new ArrayList<>()).add(def);
        HUD_PRIORITY.put(def, priority);
        HUD_MODID.put(def, captureCallerModId());
        HUD_REG_SEQ.put(def, registrationCounter++);
    }

    /** Back-compat overload — uses {@link RegionAnchor#DEFAULT_PRIORITY}. */
    public static void registerHud(MKHudPanelDef def, HudRegion region) {
        registerHud(def, region, RegionAnchor.DEFAULT_PRIORITY);
    }

    /**
     * Axial prefix for a HUD panel. All HUD regions flow vertically, so the
     * prefix is the sum of preceding visible panels' heights plus gaps.
     *
     * <p>Phase 16i: iteration order is sorted by
     * {@code (priority, modId, registrationSeq)} — same semantic as the
     * MenuContext path.
     *
     * @throws IllegalStateException if {@code self} is not registered in {@code region}
     */
    public static int axialPrefix(MKHudPanelDef self, HudRegion region) {
        List<MKHudPanelDef> panels = sortedHudPanels(region);
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

    /** Sorts HUD panels by the deterministic key (priority, modId, regSeq). */
    private static List<MKHudPanelDef> sortedHudPanels(HudRegion region) {
        List<MKHudPanelDef> panels = HUD.getOrDefault(region, List.of());
        if (panels.size() <= 1) return panels;
        List<MKHudPanelDef> sorted = new ArrayList<>(panels);
        sorted.sort(Comparator
                .comparingInt((MKHudPanelDef d) -> HUD_PRIORITY.getOrDefault(d, RegionAnchor.DEFAULT_PRIORITY))
                .thenComparing(d -> HUD_MODID.getOrDefault(d, ""))
                .thenComparingInt(d -> HUD_REG_SEQ.getOrDefault(d, Integer.MAX_VALUE)));
        return sorted;
    }

    // ── modId capture (Phase 16i) ──────────────────────────────────────

    /**
     * Walks the call stack to find the first frame outside the MenuKit
     * package, then matches the caller's class to its owning Fabric mod
     * via the JAR/build-directory path stored in the mod's origin.
     * Returns the modId on match, or a stable fallback (the caller's
     * package name) when no mod owns the class — keeps the sort key
     * total even in odd classloader scenarios.
     *
     * <p>Called at registration time only (not per-frame) so the stack
     * walk + path comparison cost lives behind one-shot setup, not the
     * render hot path.
     */
    private static String captureCallerModId() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> frames
                        .map(StackWalker.StackFrame::getDeclaringClass)
                        .filter(c -> !c.getPackageName().startsWith("com.trevorschoeny.menukit"))
                        .map(RegionRegistry::findModIdForClass)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse("zzz_unknown"));
    }

    private static String findModIdForClass(Class<?> caller) {
        try {
            var domain = caller.getProtectionDomain();
            if (domain == null) return caller.getPackageName();
            var codeSource = domain.getCodeSource();
            if (codeSource == null) return caller.getPackageName();
            URL url = codeSource.getLocation();
            if (url == null) return caller.getPackageName();
            Path callerPath = Paths.get(url.toURI()).toAbsolutePath().normalize();
            for (var mod : FabricLoader.getInstance().getAllMods()) {
                for (Path modPath : mod.getOrigin().getPaths()) {
                    if (callerPath.equals(modPath.toAbsolutePath().normalize())) {
                        return mod.getMetadata().getId();
                    }
                }
            }
        } catch (Exception ignored) {
            // Fall through to package-name fallback.
        }
        return caller.getPackageName();
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

    // Post-§0042 split: SlotGroupContext registry methods
    // (registerSlotGroup, axialPrefix(Panel, SlotGroupCategory, SlotGroupRegion),
    // warnSlotGroupOverflowOnce) live in menukit-containers'
    // SlotGroupRegionRegistry. The split is parallel to ScreenPanelRegistry's
    // — slot-group concerns extract to a containers-side companion.
}
