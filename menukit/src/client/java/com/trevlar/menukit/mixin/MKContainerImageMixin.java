package com.trevlar.menukit.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.trevlar.menukit.inject.ScreenBackground;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaves vanilla's own container image out of a background pass whose panels replace it
 * (7.4.0; {@link ScreenBackground#hidesBlit}): the chest texture of a screen the container
 * region is on. Every textured blit by location goes through this one method; outside such
 * a pass, and for any other texture, it draws as always.
 */
@ApiStatus.Internal
@Mixin(GuiGraphicsExtractor.class)
public abstract class MKContainerImageMixin {

    @Inject(method = "innerBlit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIFFFFI)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void mk$hideContainerImage(RenderPipeline pipeline, Identifier location, int x0, int x1, int y0, int y1,
                                       float u0, float u1, float v0, float v1, int color, CallbackInfo ci) {
        if (ScreenBackground.hidesBlit(location)) ci.cancel();
    }
}
