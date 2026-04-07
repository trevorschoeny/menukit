package com.trevorschoeny.menukit.container;

import com.trevorschoeny.menukit.MenuKit;

import com.trevorschoeny.menukit.region.MKRegion;
import com.trevorschoeny.menukit.region.MKRegionGroup;
import com.trevorschoeny.menukit.region.MKRegionRegistry;
import com.trevorschoeny.menukit.widget.MKSlotState;
import com.trevorschoeny.menukit.widget.MKSlotStateRegistry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Sorting algorithm for containers. Works at the region level when regions
 * exist, or on the entire container when they don't.
 *
 * <p>Sort order: first consolidate partial stacks (auto-stack), then sort
 * by total count of each item type (most abundant first), then by item
 * registry ID as tiebreaker.
 *
 * <p>Locked slots (via {@link MKSlotState}) are excluded from sorting —
 * their items stay in place and other items sort around them.
 *
 * <p>Part of the <b>MenuKit</b> framework (mixin layer).
 */
public class MKContainerSort {

    /**
     * Sorts a specific region within a container. Locked slots are excluded.
     *
     * @param region the region to sort
     * @param menu   the menu (used to look up locked slot state), or null
     */
    public static void sortRegion(MKRegion region, AbstractContainerMenu menu) {
        sortRange(region.container(), region.startIndex(),
                region.startIndex() + region.size(), menu, region);
    }

    /**
     * Sorts across an entire region group. Collects all items from every
     * region in the group, consolidates and sorts them, then distributes
     * back in fill-priority order (region with lowest priority fills first).
     *
     * <p>Locked slots are respected — their items stay in place and other
     * items sort around them.
     *
     * @param group the region group to sort
     * @param menu  the menu (for lock lookups)
     */
    public static void sortGroup(MKRegionGroup group, AbstractContainerMenu menu) {
        // Step 1: Collect all non-locked items across all regions + track free slots
        List<ItemStack> allItems = new ArrayList<>();
        // Each entry: (region, container index) — in fill-priority order
        List<int[]> freeSlots = new ArrayList<>();  // [regionIdx, regionLocal]
        Set<String> lockedKeys = new HashSet<>();

        List<MKRegion> regions = group.fillOrder();

        for (int rIdx = 0; rIdx < regions.size(); rIdx++) {
            MKRegion region = regions.get(rIdx);
            for (int local = 0; local < region.size(); local++) {
                // Check if this slot is locked via menu slot mapping
                int menuSlot = region.getMenuSlotStart() + local;
                boolean locked = false;
                if (menuSlot >= 0 && menuSlot < menu.slots.size()) {
                    net.minecraft.world.inventory.Slot slot = menu.slots.get(menuSlot);
                    MKSlotState state = MKSlotStateRegistry.get(slot);
                    if (state != null && (state.isLocked() || state.isSortLocked())) {
                        locked = true;
                    }
                }

                if (locked) continue;

                ItemStack stack = region.getItem(local);
                if (!stack.isEmpty()) {
                    allItems.add(stack.copy());
                }
                freeSlots.add(new int[]{rIdx, local});
            }
        }

        if (allItems.isEmpty()) return;

        // Step 2: Consolidate partial stacks
        List<ItemStack> consolidated = consolidateStacks(allItems);

        // Step 3: Count totals for sort ordering
        Map<String, Integer> totalCounts = new HashMap<>();
        for (ItemStack stack : consolidated) {
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            totalCounts.merge(id, stack.getCount(), Integer::sum);
        }

        // Step 4: Sort by total count (descending), then by item ID, then stack size
        consolidated.sort((a, b) -> {
            String idA = BuiltInRegistries.ITEM.getKey(a.getItem()).toString();
            String idB = BuiltInRegistries.ITEM.getKey(b.getItem()).toString();
            int countA = totalCounts.getOrDefault(idA, 0);
            int countB = totalCounts.getOrDefault(idB, 0);
            if (countA != countB) return Integer.compare(countB, countA);
            int idComp = idA.compareTo(idB);
            if (idComp != 0) return idComp;
            return Integer.compare(b.getCount(), a.getCount());
        });

        // Step 5: Distribute sorted items back into free slots (fill-priority order)
        int itemIdx = 0;
        for (int[] slot : freeSlots) {
            MKRegion region = regions.get(slot[0]);
            if (itemIdx < consolidated.size()) {
                region.setItem(slot[1], consolidated.get(itemIdx));
                itemIdx++;
            } else {
                region.setItem(slot[1], ItemStack.EMPTY);
            }
        }

        // Mark all affected containers as changed
        for (MKRegion region : regions) {
            region.container().setChanged();
        }
    }

    /**
     * Sorts an entire container. If the container has regions registered
     * for the given menu, sorts each region independently. Otherwise,
     * sorts all slots as one group.
     *
     * @param container the container to sort
     * @param menu      the menu (for region and lock lookups), or null
     */
    public static void sortContainer(Container container, AbstractContainerMenu menu) {
        // Check if this container has regions in the current menu
        List<MKRegion> regions = MKRegionRegistry.getRegions(menu);
        List<MKRegion> matching = new ArrayList<>();
        for (MKRegion r : regions) {
            if (r.container() == container) matching.add(r);
        }

        if (matching.isEmpty()) {
            // No regions — sort entire container
            sortRange(container, 0, container.getContainerSize(), menu, null);
        } else {
            // Sort each region independently
            for (MKRegion r : matching) {
                sortRegion(r, menu);
            }
        }
    }

    /**
     * Core sort: consolidate stacks then sort by total count + item ID.
     *
     * @param container the container holding the items
     * @param start     first index (inclusive)
     * @param end       last index (exclusive)
     * @param menu      menu for lock lookups (nullable)
     * @param region    region for menu-slot-to-lock lookups (nullable)
     */
    private static void sortRange(Container container, int start, int end,
                                   AbstractContainerMenu menu, MKRegion region) {
        // Step 1: Identify locked indices (these slots don't participate).
        // Lock state lives on the Slot object via MKSlotStateRegistry, but we're
        // iterating by container index. We need to find the menu Slot that maps to
        // each container index and check its lock state. If no menu is provided,
        // we can't check locks — treat all slots as unlocked.
        Set<Integer> lockedIndices = new HashSet<>();
        if (menu != null) {
            for (net.minecraft.world.inventory.Slot slot : menu.slots) {
                // Only check slots that belong to this container
                if (slot.container != container) continue;
                int ci = slot.getContainerSlot();
                if (ci < start || ci >= end) continue;

                MKSlotState state = MKSlotStateRegistry.get(slot);
                if (state != null && (state.isLocked() || state.isSortLocked())) {
                    lockedIndices.add(ci);
                }
            }
        }

        // Step 2: Collect all non-locked items
        List<ItemStack> items = new ArrayList<>();
        List<Integer> freeIndices = new ArrayList<>();
        for (int i = start; i < end; i++) {
            if (lockedIndices.contains(i)) continue;
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                items.add(stack.copy());
            }
            freeIndices.add(i);
        }

        if (items.isEmpty()) return;

        // Step 3: Consolidate stacks (merge partial stacks of the same item)
        List<ItemStack> consolidated = consolidateStacks(items);

        // Step 4: Count total of each item type across consolidated stacks
        Map<String, Integer> totalCounts = new HashMap<>();
        for (ItemStack stack : consolidated) {
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            totalCounts.merge(id, stack.getCount(), Integer::sum);
        }

        // Step 5: Sort by total count (descending), then by item ID (alphabetical)
        consolidated.sort((a, b) -> {
            String idA = BuiltInRegistries.ITEM.getKey(a.getItem()).toString();
            String idB = BuiltInRegistries.ITEM.getKey(b.getItem()).toString();
            int countA = totalCounts.getOrDefault(idA, 0);
            int countB = totalCounts.getOrDefault(idB, 0);

            // Most abundant first
            if (countA != countB) return Integer.compare(countB, countA);
            // Then alphabetical by ID
            int idComp = idA.compareTo(idB);
            if (idComp != 0) return idComp;
            // Then largest stack first (full stacks before partial)
            return Integer.compare(b.getCount(), a.getCount());
        });

        // Step 6: Place sorted items back into free indices
        int slotIdx = 0;
        for (ItemStack sorted : consolidated) {
            if (slotIdx < freeIndices.size()) {
                container.setItem(freeIndices.get(slotIdx), sorted);
                slotIdx++;
            }
        }
        // Clear remaining free slots
        while (slotIdx < freeIndices.size()) {
            container.setItem(freeIndices.get(slotIdx), ItemStack.EMPTY);
            slotIdx++;
        }

        container.setChanged();
    }

    /**
     * Merges partial stacks of the same item type into full stacks.
     * E.g., three stacks of 20 cobblestone → one stack of 60 + one of 4.
     */
    private static List<ItemStack> consolidateStacks(List<ItemStack> items) {
        // Group by item identity (same item + same components)
        List<ItemStack> result = new ArrayList<>();

        for (ItemStack item : items) {
            boolean merged = false;
            for (ItemStack existing : result) {
                if (ItemStack.isSameItemSameComponents(existing, item)
                        && existing.getCount() < existing.getMaxStackSize()) {
                    int space = existing.getMaxStackSize() - existing.getCount();
                    int toAdd = Math.min(item.getCount(), space);
                    existing.grow(toAdd);
                    item.shrink(toAdd);
                    if (item.isEmpty()) {
                        merged = true;
                        break;
                    }
                }
            }
            if (!merged && !item.isEmpty()) {
                result.add(item.copy());
            }
        }

        return result;
    }
}
