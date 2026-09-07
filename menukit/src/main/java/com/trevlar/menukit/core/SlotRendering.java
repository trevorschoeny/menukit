package com.trevlar.menukit.core;

import net.minecraft.client.gui.GuiGraphicsExtractor;

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
}
