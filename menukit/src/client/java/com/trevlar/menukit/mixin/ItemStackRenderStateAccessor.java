package com.trevlar.menukit.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads an item's layers, to turn each one ({@code ItemTurn}). */
@ApiStatus.Internal
@Mixin(ItemStackRenderState.class)
public interface ItemStackRenderStateAccessor {

    @Accessor("layers")
    ItemStackRenderState.LayerRenderState[] mk$layers();

    @Accessor("activeLayerCount")
    int mk$activeLayerCount();
}
