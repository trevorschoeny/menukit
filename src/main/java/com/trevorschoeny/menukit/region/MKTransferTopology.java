package com.trevorschoeny.menukit.region;

import com.trevorschoeny.menukit.MenuKit;

import com.trevorschoeny.menukit.widget.MKSlotActions;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Answers the question: "given this slot or region, where should items transfer to?"
 *
 * <p>Resolution order:
 * <ol>
 *   <li>Explicit routes declared via {@link RegionGroupBuilder#transferRoute}</li>
 *   <li>Implicit: other regions in the same group with {@code shiftClickIn = true}</li>
 *   <li>Empty list if nothing matches</li>
 * </ol>
 *
 * <p>Consumer modules use this to implement region-aware scroll-transfer,
 * drag-to-move, and other targeted item movement behaviors.
 *
 * <p>Part of the <b>MenuKit</b> gesture-to-action framework.
 *
 * <h3>Example usage:</h3>
 * <pre>{@code
 * List<MKRegion> targets = MKTransferTopology.getTransferTargets(menu, hoveredSlot);
 * if (!targets.isEmpty()) {
 *     // Transfer item to first target region
 *     MKSlotActions.shiftClick(hoveredSlot);
 * }
 * }</pre>
 */
public final class MKTransferTopology {

    private MKTransferTopology() {}

    /**
     * Returns the target region(s) for items in the given slot.
     *
     * @param menu the container menu
     * @param slot the source slot
     * @return target regions (may be empty if no routes or group membership found)
     */
    public static List<MKRegion> getTransferTargets(AbstractContainerMenu menu, Slot slot) {
        // Find the region this slot belongs to
        MKRegion sourceRegion = MKRegionRegistry.getRegionForSlot(menu, slot.index);
        if (sourceRegion == null) return List.of();

        return getTransferTargets(menu, sourceRegion);
    }

    /**
     * Returns the target region(s) for items in the given region.
     *
     * @param menu   the container menu
     * @param source the source region
     * @return target regions (may be empty)
     */
    public static List<MKRegion> getTransferTargets(AbstractContainerMenu menu, MKRegion source) {
        String sourceName = source.name();

        // ── Phase 1: Explicit routes from group definitions ─────────────
        // Check all groups this region belongs to for declared transfer routes
        List<MKRegion> explicitTargets = resolveExplicitRoutes(menu, sourceName);
        if (!explicitTargets.isEmpty()) {
            return explicitTargets;
        }

        // ── Phase 2: Implicit — other regions in same group ─────────────
        // If no explicit routes, fall back to all other regions in the same
        // group(s) that accept incoming items (shiftClickIn = true)
        return resolveImplicitTargets(menu, sourceName);
    }

    // ── Internal ─────────────────────────────────────────────────────────

    /**
     * Searches all group definitions for explicit transfer routes FROM the
     * given source region, and resolves them to live MKRegion instances.
     */
    private static List<MKRegion> resolveExplicitRoutes(AbstractContainerMenu menu,
                                                         String sourceName) {
        List<MKRegion> targets = new ArrayList<>();

        for (MKRegionGroup group : MKRegionRegistry.getGroups(menu)) {
            // Look up the group definition to find declared routes
            List<MKRegionGroupDef> defs = getGroupDefs();
            for (MKRegionGroupDef def : defs) {
                if (!def.name().equals(group.name())) continue;

                for (MKTransferRoute route : def.routes()) {
                    if (!route.sourceRegion().equals(sourceName)) continue;

                    // Resolve the target region name to a live region
                    MKRegion target = MKRegionRegistry.getRegion(menu, route.targetRegion());
                    if (target != null && target.shiftClickIn()) {
                        targets.add(target);
                    }
                }
            }
        }

        return targets;
    }

    /**
     * Falls back to implicit topology: other regions in the same group(s)
     * that have shiftClickIn enabled.
     */
    private static List<MKRegion> resolveImplicitTargets(AbstractContainerMenu menu,
                                                          String sourceName) {
        List<MKRegion> targets = new ArrayList<>();

        for (MKRegionGroup group : MKRegionRegistry.getGroups(menu)) {
            if (!group.contains(sourceName)) continue;

            for (MKRegion region : group.regions()) {
                // Skip the source region itself and regions that don't accept items
                if (region.name().equals(sourceName)) continue;
                if (!region.shiftClickIn()) continue;
                targets.add(region);
            }
        }

        return targets;
    }

    /**
     * Accesses the registered group definitions. Uses MKRegionRegistry's
     * internal storage rather than duplicating it.
     */
    private static List<MKRegionGroupDef> getGroupDefs() {
        return MKRegionRegistry.getGroupDefs();
    }
}
