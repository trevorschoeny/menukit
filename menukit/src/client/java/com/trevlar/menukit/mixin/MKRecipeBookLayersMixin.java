package com.trevlar.menukit.mixin;

import com.trevlar.menukit.inject.ContainerScreenLayers;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Panels over the recipe book (8.0.0): they draw right after the book, in its stratum
 * ({@link ContainerScreenLayers#aboveRecipeBook}). The book's input under them is
 * {@code MKRecipeBookInputMixin}'s.
 */
@ApiStatus.Internal
@Mixin(AbstractRecipeBookScreen.class)
public abstract class MKRecipeBookLayersMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"), require = 1)
    private void mk$bookFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        ContainerScreenLayers.recipeBookFrameStart();
    }

    @Inject(method = "extractRenderState", require = 1, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookComponent;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void mk$aboveBook(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        ContainerScreenLayers.aboveRecipeBook((AbstractContainerScreen<?>) (Object) this, graphics, mouseX, mouseY);
    }
}
