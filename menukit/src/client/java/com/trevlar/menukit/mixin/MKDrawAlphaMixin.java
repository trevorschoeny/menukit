package com.trevlar.menukit.mixin;

import com.trevlar.menukit.api.DrawAlpha;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * {@link DrawAlpha}'s opacity on every color vanilla's GUI records (7.6.0). Each hook is on
 * the one private method a kind of draw funnels through, so a draw is faded once: the fill
 * ({@code innerFill}, both gradient colors), the blit every sprite, nine-slice and texture
 * ends in ({@code innerBlit} with a texture view), the tiled blit, and text. At full opacity
 * each is a pass-through.
 */
@ApiStatus.Internal
@Mixin(GuiGraphicsExtractor.class)
public abstract class MKDrawAlphaMixin {

    @ModifyVariable(method = "innerFill", at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private int mk$fillAlpha(int color) {
        return DrawAlpha.apply(color);
    }

    @ModifyVariable(method = "innerFill", at = @At("HEAD"), argsOnly = true)
    private Integer mk$fillAlpha2(Integer color) {
        return color == null ? null : DrawAlpha.apply(color);
    }

    @ModifyVariable(method = "innerBlit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lcom/mojang/renderpearl/api/textures/GpuTextureView;Lcom/mojang/renderpearl/api/textures/GpuSampler;IIIIFFFFI)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private int mk$blitAlpha(int color) {
        return DrawAlpha.apply(color);
    }

    @ModifyVariable(method = "innerTiledBlit", at = @At("HEAD"), argsOnly = true, ordinal = 6)
    private int mk$tiledAlpha(int color) {
        return DrawAlpha.apply(color);
    }

    @ModifyVariable(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int mk$textAlpha(int color) {
        return DrawAlpha.apply(color);
    }
}
