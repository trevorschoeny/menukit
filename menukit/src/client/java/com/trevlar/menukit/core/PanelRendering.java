package com.trevlar.menukit.core;

import com.trevlar.menukit.api.panel.PanelStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Draws {@link PanelStyle} backgrounds and slot backgrounds. {@link PanelStyle}
 * says what a panel looks like; this class draws it.
 *
 * <p>RAISED and DARK are nine-sliced sprites drawn with blitSprite. INSET is drawn
 * with fills to match vanilla's inventory panel look.
 */
public final class PanelRendering {

    private PanelRendering() {}

    // Colours for the fill-drawn styles

    private static final int COLOR_BASE      = 0xFFC6C6C6;
    private static final int COLOR_HIGHLIGHT = 0xFFFFFFFF;
    private static final int COLOR_SHADOW    = 0xFF555555;
    private static final int COLOR_BORDER    = 0xFF000000;

    // MenuKit's RAISED panel sprite, in vanilla's inventory-panel look.
    private static final Identifier PANEL_RAISED_SPRITE =
            Identifier.fromNamespaceAndPath("menukit", "panel_raised");

    // MenuKit's RAISED control sprite (buttons, toggles, popovers): the same art as the
    // panel's, a file of its own so a resource pack can theme controls apart from panels.
    private static final Identifier CONTROL_RAISED_SPRITE =
            Identifier.fromNamespaceAndPath("menukit", "control_raised");

    // Vanilla's dark panel sprite, the one behind status effects.
    private static final Identifier PANEL_DARK_SPRITE =
            Identifier.withDefaultNamespace("container/inventory/effect_background");

    /**
     * Draws a panel background. RAISED and DARK are nine-sliced sprites, INSET is
     * drawn with the default colours, and NONE draws nothing.
     */
    public static void renderPanel(GuiGraphicsExtractor graphics, int x, int y,
                                   int w, int h, PanelStyle style) {
        if (w <= 0 || h <= 0) return;
        if (style == PanelStyle.NONE) return;

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

        renderPanel(graphics, x, y, w, h, style,
                COLOR_BASE, COLOR_HIGHLIGHT, COLOR_SHADOW);
    }

    /**
     * Draws a stateless control surface (an info box): as {@link #renderPanel}, except RAISED,
     * which is MenuKit's {@code menukit:control_raised} sprite, so a resource pack themes
     * controls and panels ({@code menukit:panel_raised}) each on its own. Unthemed, the two are
     * the same art. Controls with states (buttons, toggles, a dropdown, the colour field) draw
     * through {@link ControlLook#drawRaised}, every state from one source.
     */
    public static void renderControl(GuiGraphicsExtractor graphics, int x, int y, int w, int h, PanelStyle style) {
        if (style == PanelStyle.RAISED && w > 0 && h > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, CONTROL_RAISED_SPRITE, x, y, w, h);
            return;
        }
        renderPanel(graphics, x, y, w, h, style);
    }

    /**
     * Draws a Panel-look <b>panel surface</b>: the background of a panel itself (a settings
     * screen, the container region's two panels), which follows the resource pack's
     * container art ({@link PackLook}). Not for controls: a button, toggle or popover that
     * is RAISED draws {@link #renderControl}'s sprite, since a frame cut from a chest would
     * be wrong on a button. With vanilla's own textures it draws exactly what
     * {@code renderPanel(..., RAISED)} draws.
     *
     * @param look the surface's look, from {@link PackLook#look} (cached; cheap per frame)
     */
    public static void renderPanelSurface(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
                                          PackLook.Look look) {
        PackLook.drawPanel(graphics, x, y, w, h, look);
    }

    /**
     * Draws a panel background with custom colours and the default black border.
     * Every style except NONE is drawn with fills here: INSET with the shadow at
     * the top left, any other style raised.
     */
    public static void renderPanel(GuiGraphicsExtractor graphics, int x, int y,
                                   int w, int h, PanelStyle style,
                                   int baseColor, int highlightColor,
                                   int shadowColor) {
        renderPanel(graphics, x, y, w, h, style,
                baseColor, highlightColor, shadowColor, COLOR_BORDER);
    }

    /**
     * Draws a panel background with custom colours, border included. For callers
     * whose colours depend on state, such as a button's hover and press.
     */
    public static void renderPanel(GuiGraphicsExtractor graphics, int x, int y,
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
            default -> { // RAISED, drawn with fills
                topLeft = highlightColor;
                bottomRight = shadowColor;
            }
        }

        // 1 px outer border
        graphics.fill(x, y, x + w, y + 1, borderColor);                 // top
        graphics.fill(x, y + h - 1, x + w, y + h, borderColor);         // bottom
        graphics.fill(x, y + 1, x + 1, y + h - 1, borderColor);         // left
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, borderColor); // right

        if (w > 2 && h > 2) {
            // Inner highlight and shadow edges
            graphics.fill(x + 1, y + 1, x + w - 1, y + 2, topLeft);
            graphics.fill(x + 1, y + 1, x + 2, y + h - 1, topLeft);
            graphics.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, bottomRight);
            graphics.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, bottomRight);

            if (w > 4 && h > 4) {
                graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, baseColor);
            }

            // Vanilla's asymmetric corner rounding: the top-right and bottom-left
            // inner corners take the border colour, the bottom-right the shadow.
            graphics.fill(x + w - 2, y + 1, x + w - 1, y + 2, borderColor);
            graphics.fill(x + 1, y + h - 2, x + 2, y + h - 1, borderColor);
            graphics.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, bottomRight);
        }
    }

    // Slot colours, matched from the vanilla inventory texture
    private static final int SLOT_FILL      = 0xFF8B8B8B; // medium gray (slot interior)
    private static final int SLOT_SHADOW    = 0xFF373737; // dark top-left edge
    private static final int SLOT_HIGHLIGHT = 0xFFFFFFFF; // light bottom-right edge

    /**
     * Draws an 18x18 slot background exactly as vanilla does: no outer black
     * border, just the inner highlight and shadow edges around a medium grey fill.
     */
    public static void renderSlotBackground(GuiGraphicsExtractor graphics, int x, int y) {
        renderInsetRect(graphics, x, y, 18, 18);
    }

    /**
     * Draws an inset rectangle of any size with the slot background's edges and
     * fill, for other recessed areas such as scrollbar tracks. Lighter than
     * {@link PanelStyle#INSET}, which adds an outer black border and a heavier
     * bevel.
     */
    public static void renderInsetRect(GuiGraphicsExtractor graphics,
                                       int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        // Dark top and left edges, light bottom and right
        graphics.fill(x, y, x + w - 1, y + 1, SLOT_SHADOW);            // top
        graphics.fill(x, y, x + 1, y + h - 1, SLOT_SHADOW);            // left
        graphics.fill(x + 1, y + h - 1, x + w, y + h, SLOT_HIGHLIGHT); // bottom
        graphics.fill(x + w - 1, y + 1, x + w, y + h, SLOT_HIGHLIGHT); // right
        if (w > 2 && h > 2) {
            graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, SLOT_FILL);
        }
    }
}
