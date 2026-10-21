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
 * up ({@link ContainerRegions#replacesImage}). The "Inventory" label draws as always.
 *
 * <p>Every label {@code extractLabels} draws is wrapped, and the title is told by the
 * component itself (the screen's {@code title}), not by which call it is: another mod
 * drawing a label of its own before vanilla's, or wrapping one, moves no ordinal under it.
 * A mod that redirects the title call to draw a different component in its place is drawing
 * its own label, and keeps it.
 */
@ApiStatus.Internal
@Mixin(AbstractContainerScreen.class)
public abstract class MKContainerTitleMixin {

    @WrapWithCondition(method = "extractLabels", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"))
    private boolean mk$regionTitle(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color,
                                   boolean shadow) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        return text != screen.getTitle() || !ContainerRegions.replacesImage(screen);
    }
}
