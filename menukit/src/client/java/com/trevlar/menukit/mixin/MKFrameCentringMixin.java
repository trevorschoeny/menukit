package com.trevlar.menukit.mixin;

import com.trevlar.menukit.inject.FrameCentring;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A container screen's frame centered with its tab rows (Fluorite; {@link FrameCentring#centre}).
 * Vanilla centers its frame from the window's height, {@code (height - imageHeight) / 2}; its
 * container screen code reads the window as {@code 2 * dy} taller ({@link MKFrameCentringHeightMixin}),
 * so everything vanilla centers from it moves {@code dy} down together. {@code dy} is worked out
 * here, at the head of each init, before vanilla reads it.
 */
@ApiStatus.Internal
@Mixin({AbstractContainerScreen.class, AbstractRecipeBookScreen.class})
public abstract class MKFrameCentringMixin {

    // A recipe screen's init runs this before its super's does it again: the same answer twice.
    @Inject(method = "init", at = @At("HEAD"), require = 1)
    private void mk$centre(CallbackInfo ci) {
        FrameCentring.centre((AbstractContainerScreen<?>) (Object) this);
    }
}
