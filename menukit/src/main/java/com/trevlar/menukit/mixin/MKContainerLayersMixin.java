package com.trevlar.menukit.mixin;

import com.trevlar.menukit.inject.ContainerScreenLayers;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The single render hook MenuKit places on container screens. Two injection
 * points on one method feed {@link ContainerScreenLayers}, which owns the order
 * (see its layer table). {@code extractContents} is reached by every container
 * screen family — plain, recipe-book, creative — so one mixin covers all of them
 * and there is no per-family render path to diverge.
 *
 * <p>{@code require = 1}: a vanilla refactor that renames the target fails loudly
 * at load rather than leaving every panel silently invisible.
 */
@ApiStatus.Internal
@Mixin(AbstractContainerScreen.class)
public abstract class MKContainerLayersMixin {

    /** Layer 1 — before vanilla's hover resolution and slot pass. */
    @Inject(method = "extractContents", at = @At("HEAD"), require = 1)
    private void mk$belowSlots(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                               float partialTick, CallbackInfo ci) {
        ContainerScreenLayers.belowSlots((AbstractContainerScreen<?>) (Object) this,
                graphics, mouseX, mouseY);
    }

    /** Layer 3 — after vanilla's slot pass, before the carried item's stratum. */
    @Inject(method = "extractContents", at = @At("RETURN"), require = 1)
    private void mk$aboveSlots(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                               float partialTick, CallbackInfo ci) {
        ContainerScreenLayers.aboveSlots((AbstractContainerScreen<?>) (Object) this,
                graphics, mouseX, mouseY);
    }
}
