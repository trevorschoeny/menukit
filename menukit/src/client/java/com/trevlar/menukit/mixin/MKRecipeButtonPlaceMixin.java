package com.trevlar.menukit.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.trevlar.menukit.inject.ScreenSeams;

import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The recipe book button on a stretched screen ({@code ScreenFrames}): vanilla places it from the
 * window's middle, as if the screen were its own size where vanilla centers it, at init and again
 * on each click; it goes to the same place in the frame as it is. Every other screen's is left as
 * it is (a frame centered with its tab rows moves it in vanilla's own code, {@code MKFrameCentringHeightMixin}).
 */
@ApiStatus.Internal
@Mixin(AbstractRecipeBookScreen.class)
public abstract class MKRecipeButtonPlaceMixin {

    @ModifyExpressionValue(method = {"initButton", "lambda$initButton$0"}, require = 2, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractRecipeBookScreen;getRecipeBookButtonPosition()Lnet/minecraft/client/gui/navigation/ScreenPosition;"))
    private ScreenPosition mk$stretchedPlace(ScreenPosition pos) {
        int y = ScreenSeams.place((Screen) (Object) this, pos.y());
        return y == pos.y() ? pos : new ScreenPosition(pos.x(), y);
    }
}
