package com.trevlar.menukit.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.cuboid.ItemTransform;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads a layer's display transform, to turn it ({@code ItemTurn}). */
@ApiStatus.Internal
@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface ItemLayerRenderStateAccessor {

    @Accessor("itemTransform")
    ItemTransform mk$itemTransform();
}
