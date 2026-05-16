package com.trevorschoeny.menukit.input;

import com.trevorschoeny.menukit.MenuKit;

import net.minecraft.world.inventory.Slot;

/**
 * Event fired each time the cursor enters a new slot during an active
 * {@link MKDragMode} drag. Delivered to handlers registered via
 * {@code MKDragMode.Builder.onSlotEntered(...)}.
 *
 * <p>One event per slot transition — re-entering the same slot does not
 * re-fire. The {@link #context} object's {@code visitedSlots()} list is
 * useful when a handler needs to know the full path the cursor has taken
 * so far (e.g., "place one item per slot, but only on slots not yet
 * visited").
 *
 * <p>Example handler:
 * <pre>{@code
 * .onSlotEntered(event -> {
 *     Slot entered = event.slot();
 *     ItemStack onCursor = event.context().cursorStack();
 *     // distribute one item into the newly entered slot
 *     placeOneInto(entered, onCursor);
 * })
 * }</pre>
 *
 * <p>Part of the <b>MenuKit</b> drag mode API.
 *
 * @param slot    the slot the cursor just entered
 * @param context the drag context (cursor stack, visited slots, mouse button, modifiers)
 */
public record MKDragSlotEvent(
        Slot slot,
        MKDragContext context
) {}
