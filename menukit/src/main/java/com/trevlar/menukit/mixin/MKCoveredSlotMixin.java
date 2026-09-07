package com.trevlar.menukit.mixin;

import com.trevlar.menukit.inject.CoveredSlots;

import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A vanilla slot behind a visible opaque panel reports {@code isActive() ==
 * false} for the frame ({@link CoveredSlots}). {@code isActive} is vanilla's own
 * "this slot is not here right now" switch — {@code extractSlots} skips
 * inactive slots and {@code getHoveredSlot} ignores them — so covered slots
 * neither draw under the panel nor take hover, and a third party's slot hook
 * that respects {@code isActive} (as vanilla's does) never sees them either.
 * Client-only: the set holds client slot instances; server slots are untouched.
 */
@ApiStatus.Internal
@Mixin(Slot.class)
public abstract class MKCoveredSlotMixin {

    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void mk$inactiveWhenCovered(CallbackInfoReturnable<Boolean> cir) {
        if (CoveredSlots.isCovered((Slot) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
