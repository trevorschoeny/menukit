package com.trevlar.menukit.mixin;

import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.special.ChestSpecialRenderer;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads a chest item renderer's parts, to copy it with another openness ({@code ItemOpenness}). */
@ApiStatus.Internal
@Mixin(ChestSpecialRenderer.class)
public interface ChestSpecialRendererAccessor {

    @Accessor("sprites")
    SpriteGetter mk$sprites();

    @Accessor("model")
    ChestModel mk$model();

    @Accessor("sprite")
    SpriteId mk$sprite();
}
