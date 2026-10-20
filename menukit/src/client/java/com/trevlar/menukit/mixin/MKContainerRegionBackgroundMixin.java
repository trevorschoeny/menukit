package com.trevlar.menukit.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.trevlar.menukit.inject.ContainerRegions;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The background of a screen the container region is on (7.4.0). While the screen's
 * bounds are the rectangle around the region's two panels, its own background texture
 * would be drawn around that rectangle's centre, so the region draws the background
 * instead: the screen's dim and the inventory panel's frame, under vanilla's slots
 * ({@link ContainerRegions#drawBackground}). Every other screen, and a region screen
 * whose bounds are vanilla's, draws its own as before. Fabric's after-background event
 * still fires either way.
 */
@ApiStatus.Internal
@Mixin(Screen.class)
public abstract class MKContainerRegionBackgroundMixin {

    @WrapWithCondition(method = "extractRenderStateWithTooltipAndSubtitles", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private boolean mk$regionBackground(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        return !ContainerRegions.drawBackground(screen, graphics);
    }
}
