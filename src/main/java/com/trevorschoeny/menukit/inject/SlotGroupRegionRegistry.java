package com.trevorschoeny.menukit.inject;

import com.trevorschoeny.menukit.core.Panel;
import com.trevorschoeny.menukit.core.RegionAnchor;
import com.trevorschoeny.menukit.core.RegionConstants;
import com.trevorschoeny.menukit.core.SlotGroupCategory;
import com.trevorschoeny.menukit.core.SlotGroupRegion;
import com.trevorschoeny.menukit.window.ClientWindowVisibility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import org.jetbrains.annotations.ApiStatus;

/**
 * Internal registry for SlotGroupContext panel registrations.
 * Post-§0042 split companion to MenuKit's {@link RegionRegistry} (which
 * holds MenuContext + HudContext registrations).
 *
 * <p>Holds process-lifetime per-(category, region) panel lists. Panels
 * register once at mod init (during {@link SlotGroupPanelAdapter#on})
 * and remain registered until process exit.
 *
 * <p><b>Internal only.</b> Consumers don't call this directly — the
 * {@link SlotGroupPanelAdapter#on} method registers on their behalf.
 */
@ApiStatus.Internal
public final class SlotGroupRegionRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger("menukit-containers");

    private SlotGroupRegionRegistry() {}

    // Per-(category, region) panel lists for SlotGroupContext. Composite key
    // because two adapters targeting (PLAYER_INVENTORY, TOP_ALIGN_RIGHT) and
    // (FURNACE_INPUT, TOP_ALIGN_RIGHT) stack independently — they share a
    // region name but anchor to different slot groups.
    private record SlotGroupKey(SlotGroupCategory category, SlotGroupRegion region) {}
    private static final Map<SlotGroupKey, List<Panel>> SLOT_GROUP = new HashMap<>();
    private static final Map<Panel, Integer> SLOT_GROUP_PADDING = new HashMap<>();

    // Phase 5 (B2) — per-panel deterministic-sort metadata, mirroring the
    // Menu/HUD/VanillaScreen tables in RegionRegistry. Sort key is
    // (priority asc, modId asc, registrationSeq asc): lower priority renders
    // first (closer to the region's anchor edge), modId is the cross-mod
    // tiebreaker (captured via RegionRegistry.captureCallerModId so all four
    // region contexts share one capture rule), regSeq stabilizes two panels
    // from the same mod with the same priority. This is what makes
    // SlotGroupRegion.priority(int) actually drive ordering — before this it
    // was a dead method with no registry pathway.
    private static final Map<Panel, Integer> SLOT_GROUP_PRIORITY = new HashMap<>();
    private static final Map<Panel, String>  SLOT_GROUP_MODID = new HashMap<>();
    private static final Map<Panel, Integer> SLOT_GROUP_REG_SEQ = new HashMap<>();
    private static int registrationCounter = 0;

    private static final Map<Panel, Set<SlotGroupKey>> WARNED_SLOT_GROUP =
            Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Registers a SlotGroupContext panel into a (category, region) pair with
     * a content padding and explicit stacking priority. Called from
     * {@link SlotGroupPanelAdapter#on} for each declared target category.
     * A single adapter targeting N categories produces N registrations —
     * each (category, region) key stacks independently.
     *
     * <p>Priority + the captured caller modId drive the deterministic sort
     * applied at {@link #axialPrefix} time (matching the Menu/HUD/Vanilla
     * paths in {@link RegionRegistry}).
     */
    public static void registerSlotGroup(Panel panel, SlotGroupCategory category,
                                          SlotGroupRegion region, int padding,
                                          int priority) {
        SlotGroupKey key = new SlotGroupKey(category, region);
        SLOT_GROUP.computeIfAbsent(key, k -> new ArrayList<>()).add(panel);
        SLOT_GROUP_PADDING.put(panel, padding);
        SLOT_GROUP_PRIORITY.put(panel, priority);
        SLOT_GROUP_MODID.put(panel, RegionRegistry.captureCallerModId());
        SLOT_GROUP_REG_SEQ.put(panel, registrationCounter++);
    }

    /**
     * Back-compat overload — registers with {@link RegionAnchor#DEFAULT_PRIORITY}.
     * Consumers that don't call {@code SlotGroupRegion.priority(...)} hit this
     * path and still get a deterministic sort via the modId tiebreaker.
     */
    public static void registerSlotGroup(Panel panel, SlotGroupCategory category,
                                          SlotGroupRegion region, int padding) {
        registerSlotGroup(panel, category, region, padding,
                RegionAnchor.DEFAULT_PRIORITY);
    }

    /**
     * Phase 16j R5 — removes a previously-registered SlotGroupContext
     * panel from every (category, region) bucket it appears in and clears
     * its per-panel metadata. Idempotent. Symmetric counterpart to
     * {@link #registerSlotGroup}.
     */
    public static void unregisterSlotGroup(Panel panel) {
        for (List<Panel> list : SLOT_GROUP.values()) {
            list.remove(panel);
        }
        SLOT_GROUP_PADDING.remove(panel);
        SLOT_GROUP_PRIORITY.remove(panel);
        SLOT_GROUP_MODID.remove(panel);
        SLOT_GROUP_REG_SEQ.remove(panel);
        WARNED_SLOT_GROUP.remove(panel);
    }

    /**
     * Axial prefix for a SlotGroupContext panel anchored in a given
     * (category, region) pair. Walks the per-key panel list, skipping
     * hidden panels, and sums extent + {@link RegionConstants#MENU_STACK_GAP} for
     * each visible preceding entry.
     *
     * @throws IllegalStateException if {@code self} is not registered
     *         under {@code (category, region)}
     */
    public static int axialPrefix(Panel self, SlotGroupCategory category,
                                   SlotGroupRegion region) {
        SlotGroupKey key = new SlotGroupKey(category, region);
        List<Panel> panels = sortedSlotGroupPanels(key);
        int prefix = 0;
        boolean horizontal = region.isHorizontalFlow();
        for (Panel p : panels) {
            if (p == self) return prefix;
            if (!ClientWindowVisibility.panelShown(p)) continue;
            int pad = SLOT_GROUP_PADDING.getOrDefault(p, 0);
            int extent = (horizontal ? p.getWidth() : p.getHeight()) + 2 * pad;
            prefix += extent + RegionConstants.MENU_STACK_GAP;
        }
        throw new IllegalStateException(
                "Panel '" + self.getId() + "' is not registered in "
                        + category + "/" + region);
    }

    /**
     * Returns the panels registered under a (category, region) key, sorted by
     * the deterministic key {@code (priority asc, modId asc, registrationSeq
     * asc)} — the same ordering the Menu/HUD/Vanilla contexts apply in
     * {@link RegionRegistry}. This is what gives
     * {@link SlotGroupRegion#priority(int)} its effect: lower priority stacks
     * first (closer to the region's anchor edge).
     */
    private static List<Panel> sortedSlotGroupPanels(SlotGroupKey key) {
        List<Panel> panels = SLOT_GROUP.getOrDefault(key, List.of());
        if (panels.size() <= 1) return panels;
        List<Panel> sorted = new ArrayList<>(panels);
        sorted.sort(Comparator
                .comparingInt((Panel p) -> SLOT_GROUP_PRIORITY.getOrDefault(p, RegionAnchor.DEFAULT_PRIORITY))
                .thenComparing(p -> SLOT_GROUP_MODID.getOrDefault(p, ""))
                .thenComparingInt(p -> SLOT_GROUP_REG_SEQ.getOrDefault(p, Integer.MAX_VALUE)));
        return sorted;
    }

    /**
     * Logs a one-shot warning the first time a SlotGroupContext panel
     * overflows a given (category, region) pair. Called from
     * {@link SlotGroupPanelAdapter} when
     * {@link com.trevorschoeny.menukit.core.SlotGroupRegionMath#resolveSlotGroup}
     * returns empty.
     */
    public static void warnSlotGroupOverflowOnce(Panel panel,
                                                  SlotGroupCategory category,
                                                  SlotGroupRegion region,
                                                  int pw, int ph, int prefix,
                                                  SlotGroupBounds bounds) {
        SlotGroupKey key = new SlotGroupKey(category, region);
        Set<SlotGroupKey> warned = WARNED_SLOT_GROUP
                .computeIfAbsent(panel, p -> Collections.synchronizedSet(new HashSet<>()));
        if (!warned.add(key)) return;
        int axisExtent = region.isHorizontalFlow() ? pw : ph;
        int axisCapacity = region.isHorizontalFlow() ? bounds.imageWidth() : bounds.imageHeight();
        LOGGER.warn(
                "[SlotGroupRegionRegistry] Panel '{}' overflows {}/{} — axial extent " +
                "{}px (including padding) + prefix {}px exceeds slot-group capacity {}px. " +
                "Silent OUT_OF_REGION until this panel + (category, region) pair is resized.",
                panel.getId(), category, region, axisExtent, prefix, axisCapacity);
    }
}
