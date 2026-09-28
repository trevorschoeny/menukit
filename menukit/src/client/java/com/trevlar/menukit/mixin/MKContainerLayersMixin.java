package com.trevlar.menukit.mixin;

import com.trevlar.menukit.inject.ContainerScreenLayers;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The render hooks MenuKit places on container screens. Three injection points
 * feed {@link ContainerScreenLayers}, which owns the order (see its layer table).
 * All on {@code AbstractContainerScreen}, reached by every container screen
 * family — plain, recipe-book, creative — so there is no per-family render path
 * to diverge.
 *
 * <p>{@code require = 1}: a vanilla refactor that renames a target fails loudly
 * at load rather than leaving every panel silently invisible.
 */
@ApiStatus.Internal
@Mixin(AbstractContainerScreen.class)
public abstract class MKContainerLayersMixin {

    /** Frame start. */
    @Inject(method = "extractContents", at = @At("HEAD"), require = 1)
    private void mk$frameStart(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                               float partialTick, CallbackInfo ci) {
        ContainerScreenLayers.frameStart((AbstractContainerScreen<?>) (Object) this);
    }

    /** Layer 2 — at the first created slot vanilla is about to draw. */
    @Inject(method = "extractSlot", at = @At("HEAD"), require = 1)
    private void mk$beforeSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY,
                               CallbackInfo ci) {
        ContainerScreenLayers.beforeSlot((AbstractContainerScreen<?>) (Object) this,
                graphics, slot, mouseX, mouseY);
    }

    /** Layer 2 fallback + layer 4 — after vanilla's slot pass, before the carried item's stratum. */
    @Inject(method = "extractContents", at = @At("RETURN"), require = 1)
    private void mk$aboveSlots(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                               float partialTick, CallbackInfo ci) {
        ContainerScreenLayers.aboveSlots((AbstractContainerScreen<?>) (Object) this,
                graphics, mouseX, mouseY);
    }
}
