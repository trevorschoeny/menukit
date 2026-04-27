package com.trevorschoeny.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/**
 * A persistent info box rendered at a declared panel position. Auto-sizes to
 * its text content and draws with a {@link PanelStyle#RAISED} background.
 *
 * <p>Distinct from the hover-triggered tooltip set via {@code .tooltip(...)}
 * on interactive elements (Button, Toggle, Checkbox, Radio, Icon). That form
 * uses vanilla's tooltip rendering at the mouse position. This form renders
 * at a declared position persistently.
 *
 * <p>Works in all three rendering contexts. Render-only element.
 *
 * <h3>Dynamic-width limitation with supplier text</h3>
 *
 * Auto-sizing elements with supplier-based variable content cannot guarantee
 * layout stability — if the supplier returns different-length text each
 * frame, the element's width changes per frame but panel layout is not
 * re-resolved per frame. Consumers needing stable layout should use
 * fixed-content variants or ensure the supplier returns same-width content
 * across evaluations.
 *
 * @see PanelElement The interface this implements
 */
public class Tooltip implements PanelElement {

    /** Padding on all sides of the tooltip text, in pixels. */
    public static final int PADDING = 4;

    /** Default text color — vanilla inventory-label dark gray. */
    public static final int DEFAULT_TEXT_COLOR = 0xFF404040;

    private final int childX;
    private final int childY;
    private final Supplier<Component> textSupplier;

    /**
     * Creates a Tooltip with fixed text.
     *
     * @param childX X position within panel content area
     * @param childY Y position within panel content area
     * @param text   the text to display
     */
    public Tooltip(int childX, int childY, Component text) {
        this(childX, childY, () -> text);
    }

    /**
     * Creates a Tooltip whose text is driven by a supplier. The supplier is
     * invoked each frame.
     *
     * @param childX X position within panel content area
     * @param childY Y position within panel content area
     * @param text   supplier invoked each frame; must not return null
     */
    public Tooltip(int childX, int childY, Supplier<Component> text) {
        this.childX = childX;
        this.childY = childY;
        this.textSupplier = text;
    }

    // ── M8 Layout Spec ─────────────────────────────────────────────────

    /**
     * Returns an {@link com.trevorschoeny.menukit.core.layout.ElementSpec}
     * for a static-text tooltip. Width inferred from font metrics + padding.
     */
    public static com.trevorschoeny.menukit.core.layout.ElementSpec spec(Component text) {
        int textW = Minecraft.getInstance().font.width(text);
        int w = textW + 2 * PADDING;
        int h = Minecraft.getInstance().font.lineHeight + 2 * PADDING;
        return new com.trevorschoeny.menukit.core.layout.ElementSpec() {
            @Override public int width()  { return w; }
            @Override public int height() { return h; }
            @Override public PanelElement at(int x, int y) {
                return new Tooltip(x, y, text);
            }
        };
    }

    /**
     * Layout spec for supplier-driven tooltip text with consumer-declared
     * dimensions. Required for dynamic content — consumer locks max-width
     * up front so layout stays stable as supplier values change.
     */
    public static com.trevorschoeny.menukit.core.layout.ElementSpec spec(
            int width, int height, Supplier<Component> text) {
        return new com.trevorschoeny.menukit.core.layout.ElementSpec() {
            @Override public int width()  { return width; }
            @Override public int height() { return height; }
            @Override public PanelElement at(int x, int y) {
                return new Tooltip(x, y, text);
            }
        };
    }

    // ── PanelElement Implementation ────────────────────────────────────

    @Override public int getChildX() { return childX; }
    @Override public int getChildY() { return childY; }

    @Override
    public int getWidth() {
        Component text = textSupplier.get();
        int textWidth = text != null ? Minecraft.getInstance().font.width(text) : 0;
        return textWidth + 2 * PADDING;
    }

    @Override
    public int getHeight() {
        return Minecraft.getInstance().font.lineHeight + 2 * PADDING;
    }

    @Override
    public void render(RenderContext ctx) {
        Component text = textSupplier.get();
        if (text == null) return;

        var font = Minecraft.getInstance().font;
        int sx = ctx.originX() + childX;
        int sy = ctx.originY() + childY;
        int width = font.width(text) + 2 * PADDING;
        int height = font.lineHeight + 2 * PADDING;

        // Raised panel background
        PanelRendering.renderPanel(ctx.graphics(), sx, sy, width, height, PanelStyle.RAISED);

        // Text left-aligned inside the padded content area
        ctx.graphics().drawString(font, text,
                sx + PADDING, sy + PADDING,
                DEFAULT_TEXT_COLOR, false);
    }

    /** Returns the current tooltip text. Resolves the supplier. */
    public Component getCurrentText() { return textSupplier.get(); }
}
