package com.trevlar.menukit.mixin;

import com.trevlar.menukit.api.element.MKTooltip;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks a container slot's tooltip {@link MKTooltip.Kind#ITEM}, right before
 * {@code AbstractContainerScreen.extractTooltip} queues it. The slot's lines come from
 * {@code getTooltipFromContainerItem}, which the creative menu (and any mod's container
 * screen) may override without going through {@code Screen.getTooltipFromItem}, so
 * {@link MKItemTooltipMarkMixin} alone would miss them. Set at the call, the mark is
 * taken by that same tooltip.
 */
@ApiStatus.Internal
@Mixin(AbstractContainerScreen.class)
public abstract class MKContainerItemTooltipMixin {

    @Inject(
            method = "extractTooltip",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;Z)V"
            )
    )
    private void mk$markSlotTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        MKTooltip.itemTooltipNext();
    }
}
