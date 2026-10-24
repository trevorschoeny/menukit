package com.trevlar.menukit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.trevlar.menukit.core.ItemTurn;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * An item drawn turned ({@code ItemAnimation.Pose}'s pitch, yaw and roll, see {@link ItemTurn}).
 * Once vanilla has resolved the GUI item's model, and only while MenuKit is drawing an item
 * turned, its layers' display rotations take the turn. Every other draw goes through untouched.
 */
@ApiStatus.Internal
@Mixin(GuiGraphicsExtractor.class)
public abstract class MKItemTurnMixin {

    @WrapOperation(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V",
            require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemModelResolver;updateForTopItem(Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/ItemOwner;I)V"))
    private void mk$turn(ItemModelResolver resolver, ItemStackRenderState output, ItemStack stack, ItemDisplayContext context,
                         Level level, ItemOwner owner, int seed, Operation<Void> original) {
        original.call(resolver, output, stack, context, level, owner, seed);
        ItemTurn.apply(output);
    }
}
