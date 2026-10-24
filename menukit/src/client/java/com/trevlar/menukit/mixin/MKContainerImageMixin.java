package com.trevlar.menukit.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.trevlar.menukit.api.panel.ScreenFrames;
import com.trevlar.menukit.inject.ScreenBackground;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The screen's own image in its background pass (7.4.0; {@link ScreenBackground#hidesBlit}):
 * noted, and left out while panels replace it (the container region's). Every texture blit
 * by location reaches this overload. Priority 800: its head runs before OptiGUI's (priority
 * 900) swap of the texture, so the screen's own texture is what is seen, and a hidden image
 * is never swapped at all. A stretched screen's image ({@link ScreenFrames}) draws in its parts
 * instead, each through here and OptiGUI's swap again. Outside such a pass, and for any other
 * texture, it draws as always.
 */
@ApiStatus.Internal
@Mixin(value = GuiGraphicsExtractor.class, priority = 800)
public abstract class MKContainerImageMixin {

    @Inject(method = "blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void mk$screenImage(RenderPipeline pipeline, Identifier location, int x, int y, float u, float v, int width,
                                int height, int uWidth, int vHeight, int textureWidth, int textureHeight, int color,
                                CallbackInfo ci) {
        if (ScreenBackground.hidesBlit(location)) {
            ci.cancel();
            return;
        }
        Screen owner = ScreenBackground.imageOf(location);
        if (owner != null && ScreenFrames.drawParts(owner, (GuiGraphicsExtractor) (Object) this, pipeline, location, x, y,
                u, v, width, height, uWidth, vHeight, textureWidth, textureHeight, color)) ci.cancel();
    }
}
