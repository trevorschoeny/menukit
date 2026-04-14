package com.trevorschoeny.menukit.core;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Static rendering utilities for {@link PanelStyle} backgrounds and
 * slot backgrounds. The {@link PanelStyle} enum is declarative metadata
 * ("what does this panel look like"); this class is the imperative
 * renderer ("draw it like this").
 *
 * <p>Sprite-based styles (RAISED, DARK) use vanilla's 9-slice blitSprite
 * with MenuKit-owned texture assets. Programmatic styles (INSET) draw
 * themselves with fills, matching vanilla's inventory panel look.
 *
 * <p>Part of the canonical MenuKit hierarchy:
 * Screen → Panel → SlotGroup → MenuKitSlot
 */
public final class PanelRendering {

    private PanelRendering() {}

    // ── Panel colors (programmatic rendering) ───────────────────────────────

    private static final int COLOR_BASE      = 0xFFC6C6C6;
    private static final int COLOR_HIGHLIGHT = 0xFFFFFFFF;
    private static final int COLOR_SHADOW    = 0xFF555555;
    private static final int COLOR_BORDER    = 0xFF000000;

    // ── Sprite assets (9-slice) ─────────────────────────────────────────────

    /** MenuKit's RAISED panel sprite — vanilla inventory-panel look. */
    private static final Identifier PANEL_RAISED_SPRITE =
            Identifier.fromNamespaceAndPath("menukit", "panel_raised");

    /** Vanilla's dark panel sprite — used for the effects background. */
    private static final Identifier PANEL_DARK_SPRITE =
            Identifier.withDefaultNamespace("container/inventory/effect_background");

    // ── Panel Rendering ─────────────────────────────────────────────────────

    /**
     * Renders a panel background at the given position and size.
     * RAISED and DARK use 9-slice sprites; INSET renders programmatically
     * with default panel colors; NONE renders nothing.
     */
    public static void renderPanel(GuiGraphics graphics, int x, int y,
                                   int w, int h, PanelStyle style) {
        if (w <= 0 || h <= 0) return;
        if (style == PanelStyle.NONE) return;

        // Sprite-based styles — vanilla's 9-slice blitSprite
        if (style == PanelStyle.RAISED) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                    PANEL_RAISED_SPRITE, x, y, w, h);
            return;
        }
        if (style == PanelStyle.DARK) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                    PANEL_DARK_SPRITE, x, y, w, h);
            return;
        }

        // INSET — programmatic render with default colors
        renderPanel(graphics, x, y, w, h, style,
                COLOR_BASE, COLOR_HIGHLIGHT, COLOR_SHADOW);
    }

    /**
     * Renders a panel background with custom colors and the default black
     * border. Programmatic styles (INSET, RAISED fallback) use these colors;
     * sprite-based styles ignore them.
     */
    public static void renderPanel(GuiGraphics graphics, int x, int y,
                                   int w, int h, PanelStyle style,
                                   int baseColor, int highlightColor,
                                   int shadowColor) {
        renderPanel(graphics, x, y, w, h, style,
                baseColor, highlightColor, shadowColor, COLOR_BORDER);
    }

    /**
     * Renders a panel background with fully-custom colors including border.
     * The entry point for callers that want programmatic rendering with
     * state-dependent colors (e.g., hover/press states on buttons).
     */
    public static void renderPanel(GuiGraphics graphics, int x, int y,
                                   int w, int h, PanelStyle style,
                                   int baseColor, int highlightColor,
                                   int shadowColor, int borderColor) {
        if (w <= 0 || h <= 0) return;
        if (style == PanelStyle.NONE) return;

        int topLeft, bottomRight;
        switch (style) {
            case INSET -> {
                topLeft = shadowColor;
                bottomRight = highlightColor;
            }
            default -> { // RAISED (programmatic fallback)
                topLeft = highlightColor;
                bottomRight = shadowColor;
            }
        }

        // Outer border (1px)
        graphics.fill(x, y, x + w, y + 1, borderColor);                 // top
        graphics.fill(x, y + h - 1, x + w, y + h, borderColor);         // bottom
        graphics.fill(x, y + 1, x + 1, y + h - 1, borderColor);         // left
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, borderColor); // right

        if (w > 2 && h > 2) {
            // Inner highlight/shadow edges
            graphics.fill(x + 1, y + 1, x + w - 1, y + 2, topLeft);
            graphics.fill(x + 1, y + 1, x + 2, y + h - 1, topLeft);
            graphics.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, bottomRight);
            graphics.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, bottomRight);

            if (w > 4 && h > 4) {
                graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, baseColor);
            }

            // Vanilla-style asymmetric corner rounding:
            // Top-right: inner corner pixel set to border color
            graphics.fill(x + w - 2, y + 1, x + w - 1, y + 2, borderColor);
            // Bottom-left: inner corner pixel set to border color
            graphics.fill(x + 1, y + h - 2, x + 2, y + h - 1, borderColor);
            // Bottom-right: inner corner = shadow color
            graphics.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, bottomRight);
        }
    }

    // ── Slot Background ─────────────────────────────────────────────────────

    // Slot colors matched from the vanilla inventory texture
    private static final int SLOT_FILL      = 0xFF8B8B8B; // medium gray (slot interior)
    private static final int SLOT_SHADOW    = 0xFF373737; // dark top-left edge
    private static final int SLOT_HIGHLIGHT = 0xFFFFFFFF; // light bottom-right edge

    /**
     * Renders a vanilla-accurate 18×18 slot background at the given position.
     * Matches vanilla exactly — no outer black border, just inner
     * highlight/shadow edges with a medium gray fill.
     */
    public static void renderSlotBackground(GuiGraphics graphics, int x, int y) {
        int w = 18, h = 18;
        // Top and left edge (dark — inset look)
        graphics.fill(x, y, x + w - 1, y + 1, SLOT_SHADOW);            // top
        graphics.fill(x, y, x + 1, y + h - 1, SLOT_SHADOW);            // left
        // Bottom and right edge (light — inset look)
        graphics.fill(x + 1, y + h - 1, x + w, y + h, SLOT_HIGHLIGHT); // bottom
        graphics.fill(x + w - 1, y + 1, x + w, y + h, SLOT_HIGHLIGHT); // right
        // Fill interior
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, SLOT_FILL);
    }
}
