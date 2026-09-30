package com.trevlar.menukit.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.trevlar.menukit.core.ItemOutline;
import com.trevlar.menukit.core.MKRenderPipelines;

import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The second half of the coloured item outline ({@code SlotRendering.drawItemOutline}):
 * when the GUI renderer blits an item from its item atlas, a state MenuKit marked
 * as a silhouette is blitted through MenuKit's silhouette pipeline in the mark's
 * colour instead: the item's own sprite, every visible pixel in one colour. The
 * blit is otherwise vanilla's own (same texture, sampler, pose, bounds, UVs,
 * scissor). An unmarked item is untouched. See {@link ItemOutline}.
 */
@ApiStatus.Internal
@Mixin(GuiRenderer.class)
public abstract class MKItemOutlineDrawMixin {

    @Shadow @Final private GuiRenderState renderState;

    @Inject(method = "submitBlitFromItemAtlas", at = @At("HEAD"), cancellable = true)
    private void menukit$blitSilhouette(GuiItemRenderState state, GuiItemAtlas.SlotView view, CallbackInfo ci) {
        int argb = ItemOutline.take(state);
        if (argb == 0) return;
        renderState.addBlitToCurrentLayer(new BlitRenderState(
                MKRenderPipelines.GUI_SILHOUETTE,
                TextureSetup.singleTexture(view.textureView(), RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)),
                state.pose(), state.x(), state.y(), state.x() + 16, state.y() + 16,
                view.u0(), view.u1(), view.v0(), view.v1(), argb, state.scissorArea(), null));
        ci.cancel();
    }
}
