package com.trevorschoeny.menukit.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the protected leftPos/topPos fields of AbstractContainerScreen so that
 * other mixins can read the ACTUAL container position rather than re-computing it.
 *
 * Why this exists: when the vanilla recipe book is open, AbstractContainerScreen shifts
 * leftPos rightward by ~77 px (roughly 4 hotbar slots). Any mixin that re-derives
 * leftPos as (screenWidth - containerWidth) / 2 will be wrong whenever the recipe book
 * is visible, causing indicators to appear on the wrong slots.
 *
 * Usage: cast the InventoryScreen instance to this interface via (AbstractContainerScreenAccessor)(Object)this
 */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor("leftPos")
    int trevorMod$getLeftPos();

    @Accessor("topPos")
    int trevorMod$getTopPos();

    @Accessor("imageWidth")
    int trevorMod$getImageWidth();

    @Accessor("imageHeight")
    int trevorMod$getImageHeight();
}
