package com.trevlar.menukit.mixin;

import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Writes a slot's place, from its screen's origin (7.4.0). Only the container region
 * uses it: when it moves a screen's origin to the rectangle around its two panels, the
 * player's inventory slots move back by as much, so on screen they stay where vanilla
 * put them ({@code ContainerRegions}).
 */
@ApiStatus.Internal
@Mixin(Slot.class)
public interface SlotAccessor {

    @Mutable
    @Accessor("x")
    void mk$setX(int x);

    @Mutable
    @Accessor("y")
    void mk$setY(int y);
}
