package com.trevlar.menukit.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
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

    // The container region (7.4.0) sets the screen's bounds to the rectangle around its
    // two panels, moves vanilla's title back by as much as the origin moved, and moves the
    // "Inventory" label with the inventory panel.

    @Accessor("leftPos")
    void mk$setLeftPos(int leftPos);

    @Accessor("topPos")
    void mk$setTopPos(int topPos);

    @Mutable
    @Accessor("imageWidth")
    void mk$setImageWidth(int imageWidth);

    @Mutable
    @Accessor("imageHeight")
    void mk$setImageHeight(int imageHeight);

    @Accessor("titleLabelX")
    int mk$getTitleLabelX();

    @Accessor("titleLabelX")
    void mk$setTitleLabelX(int x);

    @Accessor("titleLabelY")
    int mk$getTitleLabelY();

    @Accessor("titleLabelY")
    void mk$setTitleLabelY(int y);

    @Accessor("inventoryLabelX")
    int mk$getInventoryLabelX();

    @Accessor("inventoryLabelX")
    void mk$setInventoryLabelX(int x);

    /** Where vanilla writes "Inventory": the container region's inventory panel is around it. */
    @Accessor("inventoryLabelY")
    int mk$getInventoryLabelY();

    @Accessor("inventoryLabelY")
    void mk$setInventoryLabelY(int y);

    @Accessor("hoveredSlot")
    Slot mk$getHoveredSlot();

    /** Set for the length of a background panel's slot draw, then put back ({@code PresentedSlots}). */
    @Accessor("hoveredSlot")
    void mk$setHoveredSlot(Slot slot);

    /**
     * Vanilla's own slot lookup, MenuKit's claim and every mod's hook on it included: the
     * hovered slot for a background panel's slots, drawn before vanilla works it out
     * ({@code PresentedSlots}).
     */
    @Invoker("getHoveredSlot")
    Slot mk$findHoveredSlot(double mouseX, double mouseY);

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
