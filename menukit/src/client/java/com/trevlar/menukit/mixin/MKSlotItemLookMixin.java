package com.trevlar.menukit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.trevlar.menukit.api.element.ItemAnimation;
import com.trevlar.menukit.api.panel.SlotItemLooks;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A slot's item drawn as a mod's {@code SlotItemLooks} says. Wraps only the item draw in
 * {@code extractSlot} (a real item or a fake one), so everything around it, the drag
 * preview's tint, the count and durability bar, other mods' marks, is vanilla's draw as
 * before. With no look for the slot, the draw goes through untouched.
 */
@ApiStatus.Internal
@Mixin(AbstractContainerScreen.class)
public abstract class MKSlotItemLookMixin {

    @WrapOperation(method = "extractSlot", require = 2, at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;item(Lnet/minecraft/world/item/ItemStack;III)V"),
            @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fakeItem(Lnet/minecraft/world/item/ItemStack;III)V")})
    private void mk$slotItemLook(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, int seed,
                                 Operation<Void> original, @Local(argsOnly = true) Slot slot) {
        ItemAnimation.Frame frame = stack.isEmpty() ? null
                : SlotItemLooks.frameFor((AbstractContainerScreen<?>) (Object) this, slot, stack);
        if (frame == null) original.call(graphics, stack, x, y, seed);
        else frame.animation().draw(graphics, stack, x, y, frame.t());
    }
}
