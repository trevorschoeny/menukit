package com.trevlar.menukit.mixin;

import net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer;
import net.minecraft.client.renderer.special.ShulkerBoxSpecialRenderer;
import net.minecraft.client.resources.model.sprite.SpriteId;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads a shulker box item renderer's parts, to copy it with another openness ({@code ItemOpenness}). */
@ApiStatus.Internal
@Mixin(ShulkerBoxSpecialRenderer.class)
public interface ShulkerBoxSpecialRendererAccessor {

    @Accessor("shulkerBoxRenderer")
    ShulkerBoxRenderer mk$renderer();

    @Accessor("sprite")
    SpriteId mk$sprite();
}
