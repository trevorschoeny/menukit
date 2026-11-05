package com.trevlar.menukit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.trevlar.menukit.core.CycleStrip;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Where the cycle-strip arcs draw on the HUD (7.6.0): in the hotbar, after its bar and
 * selection sprites and before its items, so a slot's own stack covers the arc's icons
 * that reach into it. The loop reads slot 0 first, so the arcs draw then, once.
 *
 * <p>And draws a hotbar slot empty while a cycle-strip arc owns its stack (7.6.0): during a turn
 * the arc moves the stacks between the slot and itself, and while a predicted turn waits
 * for the server it draws the predicted stack there. The hotbar reads each slot's stack
 * through {@code Inventory.getItem(slot)} once in {@code extractItemHotbar}'s loop; this
 * hands back an empty stack for an owned slot, so vanilla draws neither item nor count.
 */
@ApiStatus.Internal
@Mixin(Hud.class)
public abstract class MKHotbarArcMixin {

    @WrapOperation(method = "extractItemHotbar", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;getItem(I)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack mk$arcOwnsSlot(Inventory inventory, int slot, Operation<ItemStack> original,
                                     @Local(argsOnly = true) GuiGraphicsExtractor graphics) {
        if (slot == 0) CycleStrip.renderArcs(graphics);
        ItemStack stack = original.call(inventory, slot);
        return CycleStrip.ownsHeld(slot, stack) ? ItemStack.EMPTY : stack;
    }
}
