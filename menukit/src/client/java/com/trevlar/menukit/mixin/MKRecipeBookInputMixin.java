package com.trevlar.menukit.mixin;

import com.trevlar.menukit.inject.ScreenPanelRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.input.MouseButtonEvent;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The recipe book under a panel (7.6.0): panels draw over the book
 * ({@code MKRecipeBookLayersMixin}), so a click or drag on a point a panel claims is not
 * the book's. Vanilla offers the book every click twice (first from the screen's
 * {@code mouseClicked}, then as one of its children), so the book itself declines, at
 * its own HEAD, and the click goes on to the screen's slots and panels.
 */
@ApiStatus.Internal
@Mixin(RecipeBookComponent.class)
public abstract class MKRecipeBookInputMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 1)
    private void mk$clickUnderPanel(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (panelClaims(event)) cir.setReturnValue(false);
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true, require = 1)
    private void mk$dragUnderPanel(MouseButtonEvent event, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        if (panelClaims(event)) cir.setReturnValue(false);
    }

    /** A panel (or a shown modal) on the open screen claims the point: the book is under it. */
    private static boolean panelClaims(MouseButtonEvent event) {
        return ScreenPanelRegistry.claimAt(Minecraft.getInstance().gui.screen(), event.x(), event.y()) != null;
    }
}
