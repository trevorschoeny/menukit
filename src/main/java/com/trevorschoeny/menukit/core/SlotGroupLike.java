package com.trevorschoeny.menukit.core;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * The uniform abstraction for slot groups. Consumer code programs against
 * this interface and never needs to know whether the underlying screen is
 * MenuKit-native ({@link SlotGroup}) or a vanilla/observed screen
 * ({@link VirtualSlotGroup}).
 *
 * <p>Exposes the consumer query surface only — no layout metadata, no
 * panel references, no flat-index internals. Those are MenuKit-native
 * concepts that don't translate to observed screens.
 *
 * <p>The wildcard return type on {@link #getSlots} is deliberate:
 * {@link SlotGroup} returns {@code List<MKCSlot>} (a valid covariant
 * override), while {@link VirtualSlotGroup} returns {@code List<Slot>}.
 * Consumers writing group-level behavior work with {@code Slot} directly;
 * consumers needing {@code MKCSlot}-specific behavior do an
 * {@code instanceof} check after the call.
 *
 * @see SlotGroup          MenuKit-native implementation
 * @see VirtualSlotGroup   Observed-screen implementation
 * @see HandlerRecognizerRegistry#findGroup  Entry point for consumers
 */
public interface SlotGroupLike {

    /** Returns this group's identifier. */
    String getId();

    /**
     * Returns where items live. For {@link VirtualSlotGroup}s, this is a
     * read-only adapter — {@code setStack} is a no-op because MenuKit
     * doesn't mutate handlers it doesn't own.
     */
    Storage getStorage();

    /** Returns the numeric shift-click priority (higher = tried first). */
    int getShiftClickPriority();

    /**
     * Returns this group's slots. The wildcard allows {@link SlotGroup}
     * to return {@code List<MKCSlot>} and {@link VirtualSlotGroup}
     * to return {@code List<Slot>}.
     *
     * <p><b>Internal plumbing.</b> Hands back raw vanilla {@link Slot}s, used by
     * the grouping engine (shift-click routing, contract verification) — no
     * consumer caller. Consumers query a group's identity/storage/priority through
     * the address world, not its raw slot list.
     *
     * @param handler the handler whose slot list contains this group's slots
     * @return unmodifiable list of slots in this group
     */
    @ApiStatus.Internal
    List<? extends Slot> getSlots(AbstractContainerMenu handler);
}
