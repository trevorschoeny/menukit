package com.trevlar.menukit.mixin;

import com.trevlar.menukit.inject.ContainerScreenLayers;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The render hooks MenuKit places on container screens. Three injection points
 * feed {@link ContainerScreenLayers}, which owns the order (see its layer table); two
 * wraps leave the slots a panel shows (and their hover highlights) out of vanilla's
 * slot pass, so they draw right after their panel (7.3.0); the RETURN of
 * {@code extractSlot} and the two hover highlights' HEAD and RETURN clip a slot shown
 * in part to what its panel shows of it.
 * All are on {@code AbstractContainerScreen}, which every container screen family
 * (plain, recipe-book, creative) reaches, so no family has its own render path.
 *
 * <p>{@code require = 1}: a vanilla refactor that renames a target fails loudly
 * at load rather than leaving every panel silently invisible.
 */
@ApiStatus.Internal
@Mixin(AbstractContainerScreen.class)
public abstract class MKContainerLayersMixin {

    /** A new frame: the place pass, so every panel's slots are known before vanilla's slot pass. */
    @Inject(method = "extractContents", at = @At("HEAD"), require = 1)
    private void mk$frameStart(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                               float partialTick, CallbackInfo ci) {
        ContainerScreenLayers.frameStart((AbstractContainerScreen<?>) (Object) this, graphics, mouseX, mouseY);
    }

    /**
     * Vanilla's slot pass, wrapped rather than replaced: a slot a panel shows is left
     * out (its panel draws it, right after itself); every other slot draws as before.
     */
    @WrapWithCondition(method = "extractSlots", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractSlot(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/inventory/Slot;II)V"))
    private boolean mk$slotInVanillaPass(AbstractContainerScreen<?> self, GuiGraphicsExtractor graphics, Slot slot,
                                         int mouseX, int mouseY) {
        return ContainerScreenLayers.drawsInVanillaPass(slot);
    }

    /** Vanilla's hover highlights around its slot pass, left out when the hovered slot is a panel's (it gets them there). */
    @WrapWithCondition(method = "extractContents", require = 2, at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractSlotHighlightBack(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"),
            @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractSlotHighlightFront(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V")})
    private boolean mk$highlightInVanillaPass(AbstractContainerScreen<?> self, GuiGraphicsExtractor graphics) {
        return ContainerScreenLayers.highlightsInVanillaPass(self);
    }

    /** Layer 2, at the first unreported created slot vanilla is about to draw. */
    @Inject(method = "extractSlot", at = @At("HEAD"), require = 1)
    private void mk$beforeSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY,
                               CallbackInfo ci) {
        ContainerScreenLayers.beforeSlot((AbstractContainerScreen<?>) (Object) this,
                graphics, slot, mouseX, mouseY);
    }

    /** The end of a slot's draw: its over-decorations draw, then its clip ends, if it has one. */
    @Inject(method = "extractSlot", at = @At("RETURN"), require = 1)
    private void mk$afterSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY,
                              CallbackInfo ci, @Local(ordinal = 0) ItemStack stack) {
        ContainerScreenLayers.afterSlot((AbstractContainerScreen<?>) (Object) this, graphics, slot, stack, mouseX, mouseY);
    }

    /** Vanilla's hover highlights, clipped as the hovered slot is. */
    @Inject(method = {"extractSlotHighlightBack", "extractSlotHighlightFront"}, at = @At("HEAD"), require = 2)
    private void mk$beforeHighlight(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        ContainerScreenLayers.beforeHighlight((AbstractContainerScreen<?>) (Object) this, graphics);
    }

    @Inject(method = {"extractSlotHighlightBack", "extractSlotHighlightFront"}, at = @At("RETURN"), require = 2)
    private void mk$afterHighlight(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        ContainerScreenLayers.afterHighlight(graphics);
    }

    /** Layer 2 fallback and layer 4, after vanilla's slot pass and before the carried item's stratum. */
    @Inject(method = "extractContents", at = @At("RETURN"), require = 1)
    private void mk$aboveSlots(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                               float partialTick, CallbackInfo ci) {
        ContainerScreenLayers.aboveSlots((AbstractContainerScreen<?>) (Object) this,
                graphics, mouseX, mouseY);
    }
}
