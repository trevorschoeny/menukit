package com.trevorschoeny.menukit.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor for the private {@code target} field on CreativeModeInventoryScreen.SlotWrapper.
 * Allows MenuKit to identify which original MKSlot a SlotWrapper wraps during
 * creative mode tab switching.
 *
 * <p>Part of the <b>MenuKit</b> framework internals.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen$SlotWrapper")
public interface SlotWrapperAccessor {
    @Accessor("target")
    Slot menuKit$getTarget();
}
