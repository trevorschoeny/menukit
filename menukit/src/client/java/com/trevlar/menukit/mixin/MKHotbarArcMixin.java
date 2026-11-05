package com.trevlar.menukit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.trevlar.menukit.core.CycleStrip;

import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Draws a hotbar slot empty where a cycle-strip arc stands (7.6.0): the arc draws the
 * slot's stack itself, over its icons at rest and moving between the slot and itself
 * during a turn. The hotbar reads each slot's stack
 * through {@code Inventory.getItem(slot)} once in {@code extractItemHotbar}'s loop; this
 * hands back an empty stack for an owned slot, so vanilla draws neither item nor count.
 */
@ApiStatus.Internal
@Mixin(Hud.class)
public abstract class MKHotbarArcMixin {

    @WrapOperation(method = "extractItemHotbar", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;getItem(I)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack mk$arcOwnsSlot(Inventory inventory, int slot, Operation<ItemStack> original) {
        ItemStack stack = original.call(inventory, slot);
        return CycleStrip.ownsHeld(slot) ? ItemStack.EMPTY : stack;
    }
}
