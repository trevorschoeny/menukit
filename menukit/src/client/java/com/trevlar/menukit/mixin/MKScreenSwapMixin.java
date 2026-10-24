package com.trevlar.menukit.mixin;

import com.trevlar.menukit.inject.ScreenSwap;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The start of every frame the game draws, a screen open or not ({@link ScreenSwap}): a
 * container screen taken down before it, and not replaced, was not replaced in place.
 */
@ApiStatus.Internal
@Mixin(Gui.class)
public abstract class MKScreenSwapMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"), require = 1)
    private void mk$frameStart(DeltaTracker deltaTracker, boolean shouldRenderLevel, boolean resourcesLoaded, CallbackInfo ci) {
        ScreenSwap.frameStart();
    }
}
