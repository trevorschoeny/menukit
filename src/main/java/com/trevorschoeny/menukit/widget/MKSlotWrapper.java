package com.trevorschoeny.menukit.widget;

import com.trevorschoeny.menukit.MenuKit;

import com.trevorschoeny.menukit.container.MKContainerDef;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

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
 * directional flags, filters, disabledWhen, ghost icons, etc. All behavioral
 * features (OUTPUT enforcement, max stack delegation) are handled by
 * {@link com.trevorschoeny.menukit.mixin.MKSlotMixin} via {@link MKSlotState},
 * not by method overrides on this class.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKSlotWrapper extends MKSlot {

    /**
     * Creates a wrapper around an existing vanilla Slot.
     *
     * <p>All behavioral features (OUTPUT enforcement, max stack delegation)
     * are pushed into {@link MKSlotState} and enforced by the mixin layer.
     * This constructor only sets up the state — no method overrides needed.
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

        // Push persistence and wrapped slot reference into MKSlotState.
        // The mixin layer reads these to enforce OUTPUT blocking and
        // delegate max stack size to the original vanilla slot.
        MKSlotState state = MKSlotStateRegistry.getOrCreate(this);
        state.setPersistence(persistence);
        state.setWrappedSlot(vanillaSlot);
    }

    // ── Convenience Accessors (delegate to MKSlotState) ──────────────────────

    /** Returns how items in this slot behave (PERSISTENT, TRANSIENT, or OUTPUT). */
    public MKContainerDef.Persistence getPersistence() {
        MKSlotState state = MKSlotStateRegistry.get(this);
        return state != null ? state.getPersistence() : MKContainerDef.Persistence.PERSISTENT;
    }

    /** Returns the original vanilla Slot that this wrapper replaced. */
    public Slot getVanillaSlot() {
        MKSlotState state = MKSlotStateRegistry.get(this);
        Slot wrapped = state != null ? state.getWrappedSlot() : null;
        // Should never be null for a properly constructed wrapper,
        // but fall back to self as a safety measure.
        return wrapped != null ? wrapped : this;
    }
}
