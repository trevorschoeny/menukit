package com.trevlar.menukit.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.trevlar.menukit.inject.ContainerRegions;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The screen's own title on a screen the container region is on (7.4.0). The container
 * list is part of the screen's background, so vanilla's labels draw over it; the list's
 * sections title their own containers, so vanilla's title is left out while the region is
 * up ({@link ContainerRegions#ownsTitle}). The "Inventory" label draws as always.
 */
@ApiStatus.Internal
@Mixin(AbstractContainerScreen.class)
public abstract class MKContainerRegionMixin {

    @WrapWithCondition(method = "extractLabels", require = 1, at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"))
    private boolean mk$regionTitle(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color,
                                   boolean shadow) {
        return !ContainerRegions.ownsTitle((AbstractContainerScreen<?>) (Object) this);
    }
}
