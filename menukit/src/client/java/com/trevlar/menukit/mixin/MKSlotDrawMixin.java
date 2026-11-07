package com.trevlar.menukit.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.trevlar.menukit.api.element.ItemAnimation;
import com.trevlar.menukit.inject.SlotDecorationRegistry;
import com.trevlar.menukit.inject.SlotItemLookRegistry;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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
 *   <li>A look of {@link ItemAnimation#HIDDEN} (8.0.0) skips the slot's count, durability
 *       bar and cooldown too: the item draw notes it, and the decorations draw right after
 *       it in the same {@code extractSlot} reads and clears the note.</li>
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
        mk$hidden = frame != null && frame.animation() == ItemAnimation.HIDDEN;
        if (frame == null || !SlotItemLookRegistry.draw(frame, graphics, stack, x, y)) original.call(graphics, stack, x, y, seed);
    }

    /** Set by the item draw when its look is {@link ItemAnimation#HIDDEN}; the decorations draw clears it. Render thread. */
    @Unique
    private boolean mk$hidden;

    @WrapWithCondition(method = "extractSlot", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V"))
    private boolean mk$slotDecorations(GuiGraphicsExtractor graphics, Font font, ItemStack stack, int x, int y, String count) {
        boolean hidden = mk$hidden;
        mk$hidden = false;
        return !hidden;
    }

    @Inject(method = "extractSlot", require = 1, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void mk$underAfterIcon(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci,
                                   @Local(ordinal = 0) ItemStack stack) {
        SlotDecorationRegistry.draw(true, graphics, (AbstractContainerScreen<?>) (Object) this, slot, stack, slot.x, slot.y);
    }
}
