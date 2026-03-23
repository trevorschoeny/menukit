package com.trevorschoeny.menukit.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes protected methods on AbstractContainerMenu
 * so that MenuKit's mixins can access them.
 */
@Mixin(AbstractContainerMenu.class)
public interface AbstractContainerMenuInvoker {

    @Invoker("addSlot")
    Slot trevorMod$addSlot(Slot slot);

    @Invoker("moveItemStackTo")
    boolean trevorMod$moveItemStackTo(ItemStack stack, int startIndex, int endIndex, boolean reverse);
}
