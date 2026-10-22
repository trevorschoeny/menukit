package com.trevlar.menukit.mixin;

import com.trevlar.menukit.inject.RegionSlots;

import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A hidden section's slots are off (the container region): while a section of the region
 * isn't shown, every slot of it answers {@code isActive} false on the client, so vanilla
 * neither draws nor hit-tests it and a slot library leaves it out. Only slots of the client's
 * open menu are ever off ({@code RegionSlots.switchedOff}); the integrated server's are its own
 * objects, and answer as before.
 */
@ApiStatus.Internal
@Mixin(Slot.class)
public abstract class MKSlotActiveMixin {

    @Inject(method = "isActive()Z", at = @At("HEAD"), cancellable = true)
    private void mk$hiddenSection(CallbackInfoReturnable<Boolean> cir) {
        if (RegionSlots.switchedOff((Slot) (Object) this)) cir.setReturnValue(false);
    }
}
