package com.trevorschoeny.menukit.region;

import com.trevorschoeny.menukit.MenuKit;

import com.trevorschoeny.menukit.container.MKContainerSort;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Resolved runtime object representing a group of {@link MKRegion}s that
 * logically belong together. Provides query methods across all member regions.
 *
 * <p>Example: a "player_storage" group containing mk:hotbar and mk:main_inventory
 * lets you ask "how many diamonds does the player have across hotbar + main?"
 * without querying each region separately.
 *
 * <p>Regions are ordered by fill priority (lowest priority value first).
 * This order is used by {@link MKContainerSort#sortGroup} when distributing
 * items back after sorting.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKRegionGroup {

    private final String name;
    private final List<MKRegion> regions;  // sorted by fill priority (ascending)

    public MKRegionGroup(String name, List<MKRegion> regions) {
        this.name = name;
        // Mutable copy — may be extended at runtime when dynamic regions
        // are registered (e.g., peek container becoming visible after menu open).
        // Caller should already have sorted by priority.
        this.regions = new ArrayList<>(regions);
    }

    /**
     * Adds a region to this group at runtime. Used when a dynamic container
     * (e.g., peek) becomes active after the group was initially resolved.
     * Package-private — only {@link MKRegionRegistry} calls this.
     */
    void addRegion(MKRegion region) {
        regions.add(region);
    }

    /**
     * Removes a region from this group by name. Used when a dynamic container
     * (e.g., peek) is deactivated and its region is cleaned up.
     * Package-private — only {@link MKRegionRegistry} calls this.
     *
     * @return true if a region was removed
     */
    boolean removeRegion(String regionName) {
        return regions.removeIf(r -> r.name().equals(regionName));
    }

    // ── Identity ──────────────────────────────────────────────────────────

    /** The group name (e.g., "player_storage"). */
    public String name() { return name; }

    // ── Region Access ─────────────────────────────────────────────────────

    /** All regions in fill-priority order (lowest priority value first). */
    public List<MKRegion> regions() { return regions; }

    /** Alias for {@link #regions()} — same list, same order. */
    public List<MKRegion> fillOrder() { return regions; }

    // ── Membership Checks ─────────────────────────────────────────────────

    /** Whether this group contains a region with the given name. */
    public boolean contains(String regionName) {
        for (MKRegion r : regions) {
            if (r.name().equals(regionName)) return true;
        }
        return false;
    }

    /** Whether a menu slot index falls within any region in this group. */
    public boolean containsMenuSlot(int menuSlotIndex) {
        for (MKRegion r : regions) {
            if (r.containsMenuSlot(menuSlotIndex)) return true;
        }
        return false;
    }

    /** Finds the region that owns a given menu slot index, or null. */
    public MKRegion getRegionForSlot(int menuSlotIndex) {
        for (MKRegion r : regions) {
            if (r.containsMenuSlot(menuSlotIndex)) return r;
        }
        return null;
    }

    // ── Aggregate Queries ─────────────────────────────────────────────────

    /** Total slot capacity across all regions in this group. */
    public int totalSlots() {
        int total = 0;
        for (MKRegion r : regions) {
            total += r.size();
        }
        return total;
    }

    /**
     * Counts items across all regions that match the predicate.
     * Counts individual item stack sizes (e.g., a stack of 64 cobblestone = 64).
     *
     * @param predicate test applied to each non-empty ItemStack
     * @return total count of matching items
     */
    public int countItems(Predicate<ItemStack> predicate) {
        int count = 0;
        for (MKRegion r : regions) {
            for (int i = 0; i < r.size(); i++) {
                ItemStack stack = r.getItem(i);
                if (!stack.isEmpty() && predicate.test(stack)) {
                    count += stack.getCount();
                }
            }
        }
        return count;
    }

    /**
     * Counts a specific item type across all regions.
     * Uses {@link ItemStack#isSameItemSameComponents} for matching.
     *
     * @param target the item to count (only type + components matter, not count)
     * @return total count of matching items
     */
    public int countItem(ItemStack target) {
        return countItems(stack -> ItemStack.isSameItemSameComponents(stack, target));
    }

    /**
     * Returns all non-empty items across all regions in fill-priority order.
     * Each ItemStack is a copy — mutations won't affect the container.
     */
    public List<ItemStack> allItems() {
        List<ItemStack> items = new ArrayList<>();
        for (MKRegion r : regions) {
            for (int i = 0; i < r.size(); i++) {
                ItemStack stack = r.getItem(i);
                if (!stack.isEmpty()) {
                    items.add(stack.copy());
                }
            }
        }
        return items;
    }

    /** Counts empty slots across all regions in this group. */
    public int emptySlotCount() {
        int count = 0;
        for (MKRegion r : regions) {
            for (int i = 0; i < r.size(); i++) {
                if (r.getItem(i).isEmpty()) {
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    public String toString() {
        return "MKRegionGroup[" + name + " regions=" + regions.size()
                + " totalSlots=" + totalSlots() + "]";
    }
}
