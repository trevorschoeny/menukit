package com.trevlar.menukit.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.trevlar.menukit.inject.ContainerTabRows;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A tab row's band on the container region's outer edges is outside the frame (8.0.0). The
 * region sets the screen's bounds around its panels and those rows, so vanilla's
 * outside-click check would call the band inside and a click there with an item held would
 * drop nothing; past the rows' tabs it is outside, as it is beside a screen's own frame
 * ({@link ContainerTabRows#pastFrame}). Screens that override the check and ask this one
 * (the recipe book's) get the same answer.
 */
@ApiStatus.Internal
@Mixin(AbstractContainerScreen.class)
public abstract class MKOutsideClickMixin {

    @ModifyReturnValue(method = "hasClickedOutside", at = @At("RETURN"))
    private boolean mk$tabBandOutside(boolean outside, double mouseX, double mouseY, int left, int top) {
        return outside || ContainerTabRows.pastFrame((AbstractContainerScreen<?>) (Object) this, mouseX, mouseY);
    }
}
