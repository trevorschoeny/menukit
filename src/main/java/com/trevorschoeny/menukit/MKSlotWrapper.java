package com.trevorschoeny.menukit;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * An MKSlot that wraps an existing vanilla {@link Slot} in-place.
 *
 * <p>Replaces a vanilla slot in the menu's slot list at the same index,
 * using the same container reference and container slot index. Other mods
 * that access {@code menu.slots.get(i)} get this wrapper — but since it
 * extends Slot and delegates item storage to the same position, it's
 * fully compatible with all vanilla click handling, sync, and rendering.
 *
 * <p>The wrapper adds MKSlot capabilities: panel association, shift-click
 * directional flags, filters, disabledWhen, ghost icons, etc.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKSlotWrapper extends MKSlot {

    private final Slot vanillaSlot;                   // the original slot being wrapped
    private final MKContainerDef.Persistence persistence; // PERSISTENT, TRANSIENT, or OUTPUT

    /**
     * Creates a wrapper around an existing vanilla Slot.
     *
     * @param vanillaSlot the original vanilla Slot to wrap
     * @param panelName   the MK panel this slot belongs to
     * @param persistence how items in this slot behave (PERSISTENT, TRANSIENT, OUTPUT)
     * @param filter      optional item filter (null = accept all)
     */
    public MKSlotWrapper(Slot vanillaSlot,
                          @Nullable String panelName,
                          MKContainerDef.Persistence persistence,
                          @Nullable Predicate<ItemStack> filter) {
        // Construct the MKSlot with the same container, slot index, and position
        // as the vanilla slot. This makes the wrapper fully transparent.
        super(vanillaSlot.container, vanillaSlot.getContainerSlot(),
              vanillaSlot.x, vanillaSlot.y,
              filter,       // item filter (null = accept all)
              64,           // maxStack — use vanilla default
              null,         // ghostIcon — no ghost icon for vanilla slots
              null);        // disabledWhen — always active

        // Associate with the panel for shift-click routing and visibility
        setPanelName(panelName);

        this.vanillaSlot = vanillaSlot;
        this.persistence = persistence;
    }

    // ── Persistence ──────────────────────────────────────────────────────────

    /** Returns how items in this slot behave (PERSISTENT, TRANSIENT, or OUTPUT). */
    public MKContainerDef.Persistence getPersistence() {
        return persistence;
    }

    // ── OUTPUT enforcement ───────────────────────────────────────────────────

    /**
     * For OUTPUT slots, items cannot be placed in — only taken out.
     * Delegates to the vanilla slot's mayPlace for all other cases,
     * then applies the MKSlot filter on top.
     */
    @Override
    public boolean mayPlace(ItemStack stack) {
        // OUTPUT slots never accept items
        if (persistence == MKContainerDef.Persistence.OUTPUT) return false;
        // Delegate to parent MKSlot logic (filter + source constraints)
        return super.mayPlace(stack);
    }

    // ── Vanilla slot access ──────────────────────────────────────────────────

    /** Returns the original vanilla Slot that this wrapper replaced. */
    public Slot getVanillaSlot() {
        return vanillaSlot;
    }

    /**
     * Delegates to the vanilla slot's max stack size if it has a custom one.
     * Some vanilla slots (like armor slots) have custom max stack sizes.
     */
    @Override
    public int getMaxStackSize() {
        return vanillaSlot.getMaxStackSize();
    }

    /**
     * Delegates to the vanilla slot's max stack size for a specific item.
     * Some vanilla slots limit stack size based on item type.
     */
    @Override
    public int getMaxStackSize(ItemStack stack) {
        return vanillaSlot.getMaxStackSize(stack);
    }
}
