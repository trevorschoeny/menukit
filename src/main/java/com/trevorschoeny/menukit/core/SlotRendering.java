package com.trevorschoeny.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Shared slot-rendering utility for grafted slots that live outside a
 * vanilla container texture. Used by M4 consumer code to render slot
 * backgrounds, hover highlights, ghost icons, and items for grafted
 * {@link MenuKitSlot} instances.
 *
 * <p>Parallel to {@link PanelRendering}, which handles panel-level backgrounds.
 *
 * <p>Design: {@code menukit/Design Docs/Phase 12/M4_VANILLA_SLOT_INJECTION.md}.
 */
public final class SlotRendering {

    private SlotRendering() {}

    /** Default slot size (18×18) — 16px item area + 1px padding each side. */
    public static final int DEFAULT_SIZE = 18;

    /** Inset between slot edge and item area. */
    public static final int ITEM_INSET = 1;

    /** Hover highlight color — ~50% white overlay. */
    public static final int HOVER_COLOR = 0x80FFFFFF;

    /** Alpha multiplier for ghost-icon rendering (~40%). */
    public static final float DISABLED_ALPHA = 0.4f;

    /**
     * Slot background. For enabled 18×18 slots, delegates to
     * {@link PanelRendering#renderSlotBackground} for vanilla-accurate visuals.
     * For non-default sizes, falls back to {@link PanelStyle#INSET}. For
     * disabled slots of any size, uses {@link PanelStyle#DARK}.
     */
    public static void drawSlotBackground(GuiGraphics g, int sx, int sy,
                                          int size, boolean disabled) {
        if (disabled) {
            PanelRendering.renderPanel(g, sx, sy, size, size, PanelStyle.DARK);
            return;
        }
        if (size == DEFAULT_SIZE) {
            PanelRendering.renderSlotBackground(g, sx, sy);
            return;
        }
        PanelRendering.renderPanel(g, sx, sy, size, size, PanelStyle.INSET);
    }

    /** Translucent hover-highlight overlay inside the slot's item area. */
    public static void drawHoverHighlight(GuiGraphics g, int sx, int sy, int size) {
        g.fill(sx + ITEM_INSET, sy + ITEM_INSET,
                sx + size - ITEM_INSET, sy + size - ITEM_INSET,
                HOVER_COLOR);
    }

    /**
     * Renders the item centered in the slot's item area, with count and
     * durability decorations. When {@code dimmed=true}, overlays a translucent
     * fill to signal disabled state.
     */
    public static void drawItem(GuiGraphics g, ItemStack stack, int sx, int sy,
                                int size, boolean dimmed) {
        if (stack == null || stack.isEmpty()) return;
        int itemX = sx + ITEM_INSET;
        int itemY = sy + ITEM_INSET;
        var mc = Minecraft.getInstance();
        g.renderItem(stack, itemX, itemY);
        g.renderItemDecorations(mc.font, stack, itemX, itemY);
        if (dimmed) {
            g.fill(itemX, itemY, itemX + 16, itemY + 16, 0x80000000);
        }
    }

    /**
     * Ghost-icon overlay — dimmed sprite for empty filtered slots.
     */
    public static void drawGhostIcon(GuiGraphics g, Identifier sprite,
                                     int sx, int sy, int size) {
        if (sprite == null) return;
        int iconX = sx + ITEM_INSET;
        int iconY = sy + ITEM_INSET;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite,
                iconX, iconY, 16, 16, DISABLED_ALPHA);
    }
}
