package com.trevlar.menukit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.trevlar.menukit.core.ItemOpenness;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.SpecialModelWrapper;
import net.minecraft.client.renderer.special.SpecialModelRenderer;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A shulker box or chest item drawn with its lid open ({@code ItemAnimation.OPEN_LID}, see
 * {@link ItemOpenness}). Where a special item model hands its renderer to the item's layer,
 * and only while MenuKit is drawing an item open, the layer gets the renderer's open copy,
 * the copy's extents, an identity element for the openness, and the oversized mark so the
 * lid draws past the slot. Every other item, and every item outside such a draw, goes
 * through untouched.
 */
@ApiStatus.Internal
@Mixin(SpecialModelWrapper.class)
public abstract class MKItemOpennessMixin {

    @WrapOperation(method = "update", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState$LayerRenderState;setupSpecialModel(Lnet/minecraft/client/renderer/special/SpecialModelRenderer;Ljava/lang/Object;)V"))
    private void mk$openLid(ItemStackRenderState.LayerRenderState layer, SpecialModelRenderer<?> renderer, Object argument,
                            Operation<Void> original, @Local(argsOnly = true) ItemStackRenderState output) {
        ItemOpenness.Opened opened = ItemOpenness.opened(renderer);
        if (opened == null) {
            original.call(layer, renderer, argument);
            return;
        }
        layer.setExtents(opened.extents());
        output.appendModelIdentityElement(opened.identity());
        output.setOversizedInGui(true);
        original.call(layer, opened.renderer(), argument);
    }
}
