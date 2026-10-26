package com.trevlar.menukit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.trevlar.menukit.api.element.ItemAnimation;
import com.trevlar.menukit.inject.SlotDecorationRegistry;
import com.trevlar.menukit.inject.SlotItemLookRegistry;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inside a slot's draw ({@code extractSlot}): its under-decorations, then its item as a
 * mod's {@code SlotItemLooks} says. Its over-decorations draw at the RETURN, from
 * {@code ContainerScreenLayers.afterSlot}, before the slot's clip ends.
 * <ul>
 *   <li>Wraps only the item draw (a real item or a fake one), so everything around it, the
 *       drag preview's tint, the count and durability bar, is vanilla's draw as before. With
 *       no look for the slot, or an animation that has thrown, the item draw goes through
 *       untouched.</li>
 *   <li>An empty slot with an icon (an armor slot's) draws the icon and no item: its
 *       under-decorations draw after the icon.</li>
 * </ul>
 */
@ApiStatus.Internal
@Mixin(AbstractContainerScreen.class)
public abstract class MKSlotDrawMixin {

    @WrapOperation(method = "extractSlot", require = 2, at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;item(Lnet/minecraft/world/item/ItemStack;III)V"),
            @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fakeItem(Lnet/minecraft/world/item/ItemStack;III)V")})
    private void mk$slotItem(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, int seed,
                             Operation<Void> original, @Local(argsOnly = true) Slot slot) {
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        SlotDecorationRegistry.draw(true, graphics, screen, slot, stack, x, y);
        ItemAnimation.Frame frame = stack.isEmpty() ? null : SlotItemLookRegistry.frameFor(screen, slot, stack);
        if (frame == null || !SlotItemLookRegistry.draw(frame, graphics, stack, x, y)) original.call(graphics, stack, x, y, seed);
    }

    @Inject(method = "extractSlot", require = 1, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void mk$underAfterIcon(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci,
                                   @Local(ordinal = 0) ItemStack stack) {
        SlotDecorationRegistry.draw(true, graphics, (AbstractContainerScreen<?>) (Object) this, slot, stack, slot.x, slot.y);
    }
}
