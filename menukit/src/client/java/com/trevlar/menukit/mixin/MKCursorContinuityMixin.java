package com.trevlar.menukit.mixin;

import com.trevlar.menukit.input.CursorContinuity;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Where a screen change ends ({@link CursorContinuity}): right after vanilla reads the
 * pointer back from the OS, the cursor goes back to where it was on the screen taken down.
 */
@ApiStatus.Internal
@Mixin(Gui.class)
public abstract class MKCursorContinuityMixin {

    @Inject(method = "setScreen",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;resyncMousePosition()V", shift = At.Shift.AFTER),
            require = 1)
    private void mk$restoreCursor(@Nullable Screen screen, CallbackInfo ci) {
        CursorContinuity.restoreIfAny();
    }
}
