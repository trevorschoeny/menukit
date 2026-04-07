package com.trevorschoeny.menukit.widget;

import com.trevorschoeny.menukit.MenuKit;

import com.trevorschoeny.menukit.container.MKContainerDef;

import com.trevorschoeny.menukit.container.MKContainerDef.Persistence;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Backend (server-side) state for a slot. Contains item validation rules,
 * persistence, and region membership — the "what exists" concerns.
 *
 * <p>Separated from frontend concerns (visual decorations, ghost icons, etc.)
 * as part of the MenuKit v2 separation of concerns.
 *
 * <p>Read by {@code MKSlotMixin.mayPlace()} and server-side validation code.
 * Never touches rendering or visual state.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKSlotBackendState {

    // ── Region & Container Association ──────────────────────────────────
    private @Nullable String regionName;
    private int regionIndex = -1;  // index within the region (0-based)
    private int containerSlot = -1; // unified container slot index

    // ── Item Rules ──────────────────────────────────────────────────────
    private @Nullable Predicate<ItemStack> filter;
    private int maxStackSize;  // 0 = vanilla default

    // ── Persistence ─────────────────────────────────────────────────────
    private @Nullable Persistence persistence;

    // ── Shift-Click ─────────────────────────────────────────────────────
    private boolean shiftClickIn;
    private boolean shiftClickOut;

    // ── Sort Lock ───────────────────────────────────────────────────────
    // Sort-locked slots are excluded from sorting and shift-click-in
    // routing, but remain fully interactive for direct clicks.
    private boolean sortLocked;

    // ── Region & Container ──────────────────────────────────────────────

    public @Nullable String getRegionName() { return regionName; }
    public void setRegionName(@Nullable String name) { this.regionName = name; }

    public int getRegionIndex() { return regionIndex; }
    public void setRegionIndex(int index) { this.regionIndex = index; }

    public int getContainerSlot() { return containerSlot; }
    public void setContainerSlot(int slot) { this.containerSlot = slot; }

    // ── Filter ──────────────────────────────────────────────────────────

    public @Nullable Predicate<ItemStack> getFilter() { return filter; }
    public void setFilter(@Nullable Predicate<ItemStack> filter) { this.filter = filter; }

    public boolean passesFilter(ItemStack stack) {
        return filter == null || filter.test(stack);
    }

    // ── Max Stack Size ──────────────────────────────────────────────────

    public int getMaxStackSize() { return maxStackSize; }
    public void setMaxStackSize(int max) { this.maxStackSize = max; }

    public int getEffectiveMaxStackSize(int vanillaDefault) {
        return maxStackSize > 0 ? maxStackSize : vanillaDefault;
    }

    // ── Persistence ─────────────────────────────────────────────────────

    public @Nullable Persistence getPersistence() { return persistence; }
    public void setPersistence(@Nullable Persistence persistence) { this.persistence = persistence; }

    public boolean isPersistenceOutput() {
        return persistence == Persistence.OUTPUT;
    }

    // ── Shift-Click ─────────────────────────────────────────────────────

    public boolean canShiftClickIn() { return shiftClickIn; }
    public void setShiftClickIn(boolean value) { this.shiftClickIn = value; }

    public boolean canShiftClickOut() { return shiftClickOut; }
    public void setShiftClickOut(boolean value) { this.shiftClickOut = value; }

    // ── Sort Lock ───────────────────────────────────────────────────────

    public boolean isSortLocked() { return sortLocked; }
    public void setSortLocked(boolean sortLocked) { this.sortLocked = sortLocked; }
    public void toggleSortLocked() { this.sortLocked = !this.sortLocked; }
}
