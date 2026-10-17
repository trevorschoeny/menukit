package com.trevlar.menukit.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes AbstractContainerScreen's protected position and size fields so other
 * code reads the actual container position instead of recomputing it.
 *
 * <p>When the vanilla recipe book is open, the screen shifts leftPos right by
 * about 77 px (roughly 4 hotbar slots). Anything that re-derives leftPos as
 * (screenWidth - containerWidth) / 2 is wrong while the book is visible and puts
 * indicators on the wrong slots.
 */
@ApiStatus.Internal
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor("leftPos")
    int mk$getLeftPos();

    @Accessor("topPos")
    int mk$getTopPos();

    @Accessor("imageWidth")
    int mk$getImageWidth();

    @Accessor("imageHeight")
    int mk$getImageHeight();

    @Accessor("hoveredSlot")
    Slot mk$getHoveredSlot();

    /** Vanilla's private hovered-slot back highlight, re-issued by {@code ContainerScreenLayers}. */
    @Invoker("extractSlotHighlightBack")
    void mk$extractSlotHighlightBack(GuiGraphicsExtractor graphics);

    /** Vanilla's private hovered-slot front highlight, drawn after a slot a panel shows ({@code PresentedSlots}). */
    @Invoker("extractSlotHighlightFront")
    void mk$extractSlotHighlightFront(GuiGraphicsExtractor graphics);

    /**
     * The screen's own slot draw, for a slot a panel shows, right after that panel
     * ({@code PresentedSlots}). Virtual, so a screen's override (the crafter's) and
     * every other mod's {@code extractSlot} mixin still run.
     */
    @Invoker("extractSlot")
    void mk$extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY);
}
