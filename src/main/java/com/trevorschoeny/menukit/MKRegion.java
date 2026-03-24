package com.trevorschoeny.menukit;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * A named group of indices within a {@link Container}. Regions are the
 * mixin-layer concept that replaces panel-based slot grouping.
 *
 * <p>A single vanilla Container can have multiple regions (e.g., player
 * Inventory has hotbar, main, armor, offhand). A custom MKContainer
 * always has exactly one region covering all its indices.
 *
 * <p>Regions carry a direct reference to the Container they belong to,
 * enabling item access without going through the menu slot system.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKRegion {

    private final String name;
    private final Container container;
    private final int startIndex;   // first index within the container (inclusive)
    private final int size;         // number of indices in this region
    private final MKContainerDef.Persistence persistence;

    // Shift-click directional flags (mutable — can change at runtime)
    private boolean shiftClickIn;
    private boolean shiftClickOut;

    // Menu slot range — set during menu resolution, used for slot lookups
    private int menuSlotStart = -1;
    private int menuSlotEnd = -1;

    public MKRegion(String name, Container container, int startIndex, int size,
                    MKContainerDef.Persistence persistence,
                    boolean shiftClickIn, boolean shiftClickOut) {
        this.name = name;
        this.container = container;
        this.startIndex = startIndex;
        this.size = size;
        this.persistence = persistence;
        this.shiftClickIn = shiftClickIn;
        this.shiftClickOut = shiftClickOut;
    }

    // ── Index Translation ────────────────────────────────────────────────

    /** Converts a region-local index (0-based) to a container index. */
    public int toContainerIndex(int regionLocal) {
        return startIndex + regionLocal;
    }

    /** Converts a container index to a region-local index, or -1 if not in this region. */
    public int fromContainerIndex(int containerIndex) {
        int local = containerIndex - startIndex;
        return (local >= 0 && local < size) ? local : -1;
    }

    /** Whether the given container index falls within this region. */
    public boolean contains(int containerIndex) {
        return containerIndex >= startIndex && containerIndex < startIndex + size;
    }

    // ── Item Access (delegates to container) ─────────────────────────────

    /** Gets the item at a region-local index. */
    public ItemStack getItem(int regionLocal) {
        return container.getItem(toContainerIndex(regionLocal));
    }

    /** Sets the item at a region-local index. */
    public void setItem(int regionLocal, ItemStack stack) {
        container.setItem(toContainerIndex(regionLocal), stack);
    }

    // ── Menu Slot Range ──────────────────────────────────────────────────

    /** Sets the menu slot range this region maps to (called during resolution). */
    public void setMenuSlotRange(int start, int end) {
        this.menuSlotStart = start;
        this.menuSlotEnd = end;
    }

    /** First menu slot index (inclusive), or -1 if not resolved. */
    public int getMenuSlotStart() { return menuSlotStart; }

    /** Last menu slot index (inclusive), or -1 if not resolved. */
    public int getMenuSlotEnd() { return menuSlotEnd; }

    /** Whether a menu slot index falls within this region's menu range. */
    public boolean containsMenuSlot(int menuSlotIndex) {
        return menuSlotStart >= 0 && menuSlotIndex >= menuSlotStart && menuSlotIndex <= menuSlotEnd;
    }

    /** Converts a menu slot index to a region-local index, or -1. */
    public int fromMenuSlot(int menuSlotIndex) {
        if (!containsMenuSlot(menuSlotIndex)) return -1;
        return menuSlotIndex - menuSlotStart;
    }

    // ── Getters ──────────────────────────────────────────────────────────

    public String name() { return name; }
    public Container container() { return container; }
    public int startIndex() { return startIndex; }
    public int size() { return size; }
    public MKContainerDef.Persistence persistence() { return persistence; }

    public boolean shiftClickIn() { return shiftClickIn; }
    public boolean shiftClickOut() { return shiftClickOut; }
    public void setShiftClickIn(boolean value) { this.shiftClickIn = value; }
    public void setShiftClickOut(boolean value) { this.shiftClickOut = value; }

    @Override
    public String toString() {
        return "MKRegion[" + name + " start=" + startIndex + " size=" + size
                + " menu=" + menuSlotStart + "-" + menuSlotEnd + "]";
    }
}
