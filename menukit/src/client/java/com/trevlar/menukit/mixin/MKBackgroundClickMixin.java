package com.trevlar.menukit.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.trevlar.menukit.inject.ScreenPanelRegistry;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A click the screen's widgets declined goes to the background panels (7.4.0) before
 * vanilla's slot handling: {@code AbstractContainerScreen.mouseClicked} asks its widgets
 * first ({@code super.mouseClicked}, which is where every other mod's buttons are) and
 * then always answers "used", so this is the one place between the two. A click one of
 * them takes ends there; one on a slot a background panel shows is left to vanilla.
 */
@ApiStatus.Internal
@Mixin(AbstractContainerScreen.class)
public abstract class MKBackgroundClickMixin {

    @ModifyExpressionValue(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z", require = 1,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/Screen;mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z"))
    private boolean mk$backgroundClick(boolean widgetTookIt, @Local(argsOnly = true) MouseButtonEvent event) {
        return widgetTookIt || ScreenPanelRegistry.dispatchBackgroundClick((AbstractContainerScreen<?>) (Object) this,
                event.x(), event.y(), event.button());
    }
}
