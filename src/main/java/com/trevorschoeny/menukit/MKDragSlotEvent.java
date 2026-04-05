package com.trevorschoeny.menukit;

import net.minecraft.world.inventory.Slot;

/**
 * Event fired when the cursor enters a new slot during a custom drag.
 *
 * <p>Part of the <b>MenuKit</b> drag mode API.
 */
public record MKDragSlotEvent(
        Slot slot,
        MKDragContext context
) {}
