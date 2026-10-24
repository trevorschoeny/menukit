package com.trevlar.menukit.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.trevlar.menukit.inject.ContainerTabRows;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;

import org.jetbrains.annotations.ApiStatus;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A container screen's frame centered with its tab rows (Fluorite; {@link ContainerTabRows#centre}).
 * Vanilla centers its frame from the window's height, {@code (height - imageHeight) / 2}; inside
 * vanilla's own container screen code the window reads as {@code 2 * dy} taller, so everything
 * vanilla centers from it moves {@code dy} down together, with vanilla's own rounding: here the
 * frame's {@code topPos} and the recipe book beside it; the screens' own images and what they
 * draw from them in {@link MKFrameCentringBackgroundMixin}. {@code dy} is worked out at the head
 * of each init, before vanilla reads it; 0 on a screen with no tab row, so nothing moves there.
 */
@ApiStatus.Internal
@Mixin({AbstractContainerScreen.class, AbstractRecipeBookScreen.class})
public abstract class MKFrameCentringMixin {

    // A recipe screen's init runs this before its super's does it again: the same answer twice.
    @Inject(method = "init", at = @At("HEAD"), require = 1)
    private void mk$centre(CallbackInfo ci) {
        ContainerTabRows.centre((AbstractContainerScreen<?>) (Object) this);
    }

    // Read as each class's own field: javac names the class the read is in as its owner.
    @ModifyExpressionValue(method = "init", require = 1, at = {
            @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;height:I", opcode = Opcodes.GETFIELD),
            @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractRecipeBookScreen;height:I", opcode = Opcodes.GETFIELD)})
    private int mk$centredHeight(int height) {
        return height + 2 * ContainerTabRows.centring((Screen) (Object) this);
    }
}
