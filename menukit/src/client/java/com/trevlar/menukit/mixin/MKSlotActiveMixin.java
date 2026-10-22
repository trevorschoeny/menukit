package com.trevlar.menukit.mixin;

import com.trevlar.menukit.inject.RegionTabs;

import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A hidden tab's slots are off (the container list's tabs mode): while another container's
 * tab is picked, every slot of the containers not shown answers {@code isActive} false on the
 * client, so vanilla neither draws nor hit-tests them and a slot library leaves them out. Only
 * slots of the client's open menu are ever off ({@code RegionTabs.switchedOff}); the integrated
 * server's are its own objects, and answer as before.
 */
@ApiStatus.Internal
@Mixin(Slot.class)
public abstract class MKSlotActiveMixin {

    @Inject(method = "isActive()Z", at = @At("HEAD"), cancellable = true)
    private void mk$hiddenTab(CallbackInfoReturnable<Boolean> cir) {
        if (RegionTabs.switchedOff((Slot) (Object) this)) cir.setReturnValue(false);
    }
}
