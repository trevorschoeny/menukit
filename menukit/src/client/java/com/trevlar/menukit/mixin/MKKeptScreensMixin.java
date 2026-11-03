package com.trevlar.menukit.mixin;

import com.trevlar.menukit.inject.KeptScreens;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every screen change ({@link KeptScreens}): which screen is opening, seen before the open
 * one is taken down, and the change's end.
 */
@ApiStatus.Internal
@Mixin(Gui.class)
public abstract class MKKeptScreensMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), require = 1)
    private void mk$opening(@Nullable Screen screen, CallbackInfo ci) {
        KeptScreens.opening(screen);
    }

    @Inject(method = "setScreen", at = @At("RETURN"), require = 1)
    private void mk$opened(@Nullable Screen screen, CallbackInfo ci) {
        KeptScreens.opened();
    }
}
