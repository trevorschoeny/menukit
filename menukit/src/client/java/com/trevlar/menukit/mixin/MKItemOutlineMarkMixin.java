package com.trevlar.menukit.mixin;

import com.trevlar.menukit.core.ItemOutline;

import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The first half of the coloured item outline ({@code SlotRendering.drawItemOutline}):
 * as an item's render state is recorded, mark it when MenuKit is drawing an
 * outline's silhouette copy. See {@link ItemOutline} for why the outline takes two
 * steps. Observes only; changes nothing about the state.
 */
@ApiStatus.Internal
@Mixin(GuiRenderState.class)
public abstract class MKItemOutlineMarkMixin {

    @Inject(method = "addItem", at = @At("HEAD"))
    private void menukit$markSilhouette(GuiItemRenderState state, CallbackInfo ci) {
        ItemOutline.mark(state);
    }
}
