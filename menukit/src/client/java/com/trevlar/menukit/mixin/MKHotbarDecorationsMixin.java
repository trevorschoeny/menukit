package com.trevlar.menukit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.trevlar.menukit.inject.HotbarDecorationRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Where {@link com.trevlar.menukit.api.hud.HotbarDecorations} draw (8.0.0): in the hotbar,
 * after its bar and selection sprites and before its items. The item loop reads each slot's
 * stack through {@code Inventory.getItem(slot)} once in {@code extractItemHotbar}, slot 0
 * first, so the under decorations of all nine slots draw then, once, and every item covers
 * them. A slot a hide rule hides gets an empty stack back, so vanilla draws neither its item
 * nor its count.
 */
@ApiStatus.Internal
@Mixin(Hud.class)
public abstract class MKHotbarDecorationsMixin {

    @WrapOperation(method = "extractItemHotbar", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Inventory;getItem(I)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack mk$hotbarDecorations(Inventory inventory, int slot, Operation<ItemStack> original,
                                           @Local(argsOnly = true) GuiGraphicsExtractor graphics) {
        if (slot == 0) {
            var window = Minecraft.getInstance().getWindow();
            HotbarDecorationRegistry.drawUnder(graphics, inventory, window.getGuiScaledWidth(), window.getGuiScaledHeight());
        }
        ItemStack stack = original.call(inventory, slot);
        return HotbarDecorationRegistry.hidden(slot, stack) ? ItemStack.EMPTY : stack;
    }
}
