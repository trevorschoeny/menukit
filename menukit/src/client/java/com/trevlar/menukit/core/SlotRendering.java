package com.trevlar.menukit.core;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * The recessed 18×18 slot frame for registered slots that live outside a
 * vanilla container texture — the one piece of a created slot's presentation
 * that is <em>panel chrome</em> rather than the slot itself.
 *
 * <p>Everything else a slot shows — item, count, durability, hover highlight,
 * ghost icon, quick-craft preview — is vanilla's slot pass, which draws a created
 * slot exactly as it draws a vanilla slot once the presenting panel has written
 * the slot's {@code Slot.x/y} (see {@code ContainerScreenLayers}, layer 1 vs 2).
 * This class deliberately reimplements none of it.
 *
 * <p>Parallel to {@link PanelRendering}, which handles panel-level backgrounds.
 *
 * <p>It also holds the one item-decoration primitive a slot decoration needs and
 * vanilla lacks, {@link #drawItemOutline}: a coloured outline around an item's own
 * silhouette (Inventory Plus marks locked items with their lock group's colour).
 */
public final class SlotRendering {

    private SlotRendering() {}

    /** Default slot size (18×18) — 16px item area + 1px padding each side. */
    public static final int DEFAULT_SIZE = 18;

    /** Inset between slot edge and item area — where vanilla's {@code Slot.x/y} sits. */
    public static final int ITEM_INSET = 1;

    /**
     * Slot background. For enabled 18×18 slots, delegates to
     * {@link PanelRendering#renderSlotBackground} for vanilla-accurate visuals.
     * For non-default sizes, falls back to {@link PanelStyle#INSET}. For
     * disabled slots of any size, uses {@link PanelStyle#DARK}.
     */
    public static void drawSlotBackground(GuiGraphicsExtractor g, int sx, int sy,
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

    /**
     * Draws {@code stack} at {@code (x, y)} with a one-pixel outline of
     * {@code argb} around the item's own silhouette, the shape of its sprite, not
     * its 16x16 box. For a slot decoration or any hand-drawn item; an
     * {@link ItemDisplay} takes it as {@code outline(...)}.
     *
     * <p>How: the item is drawn four times, one pixel left, right, up and down,
     * as a flat silhouette in {@code argb} (MenuKit's silhouette pipeline over the
     * item's own sprite in vanilla's item atlas), then drawn normally on top. Every
     * item vanilla draws from its atlas gets it, which is every item at gui size;
     * an oversized item (one whose model reaches outside its 16x16 box, which
     * vanilla draws through a separate picture-in-picture path) draws without an
     * outline. Call it where the item would be drawn: it draws the item itself.
     *
     * @param argb the outline's colour, alpha byte included; 0 draws the item alone
     */
    public static void drawItemOutline(GuiGraphicsExtractor g, ItemStack stack, int x, int y, int argb) {
        if (stack.isEmpty()) return;
        if (argb != 0) {
            ItemOutline.silhouettes(argb, () -> {
                g.fakeItem(stack, x - 1, y);
                g.fakeItem(stack, x + 1, y);
                g.fakeItem(stack, x, y - 1);
                g.fakeItem(stack, x, y + 1);
            });
        }
        g.item(stack, x, y);
    }
}
