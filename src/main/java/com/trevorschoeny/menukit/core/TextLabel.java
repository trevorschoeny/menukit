package com.trevorschoeny.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/**
 * A non-interactive text label within a {@link Panel}. Renders text at
 * a fixed position using {@code drawString}.
 *
 * <p>Two forms for the text content:
 * <ul>
 *   <li><b>Fixed text</b> — pass a {@link Component} directly.</li>
 *   <li><b>Supplier-driven text</b> — pass a {@code Supplier<Component>} for
 *   text that changes over time (dynamic values, state reflections, etc.).</li>
 * </ul>
 *
 * <p><b>ARGB color requirement (1.21.11):</b> Colors must include an
 * explicit alpha byte (e.g., {@code 0xFF404040}, not {@code 0x404040}).
 * {@code GuiGraphics.drawString()} silently discards text when
 * {@code ARGB.alpha(color) == 0}. All color constants in this class
 * use the {@code 0xFF} prefix. Consumer code passing custom colors must
 * do the same.
 *
 * <h3>Dynamic-width limitation with supplier text</h3>
 *
 * TextLabel's width is derived from the rendered text's width. Auto-sizing
 * elements with supplier-based variable content cannot guarantee layout
 * stability — if the supplier returns different-length text each frame,
 * the element's width changes per frame but panel layout is not re-resolved
 * per frame. Consumers needing stable layout should use fixed-content
 * variants or ensure the supplier returns same-width content across all
 * evaluations (e.g., {@code "Mode: AUTO"} vs {@code "Mode: MANUAL"} where
 * both render to similar widths).
 *
 * <p>Render-only; {@link #mouseClicked} inherits the default no-op behavior.
 *
 * @see PanelElement  The interface this implements
 * @see Button        Interactive button element
 */
public class TextLabel implements PanelElement {

    /** Dark gray with shadow off — matches vanilla container labels on light backgrounds. */
    public static final int COLOR_DARK = 0xFF404040;

    /** White with shadow on — readable on dark panel backgrounds. */
    public static final int COLOR_LIGHT = 0xFFFFFFFF;

    private final int childX;
    private final int childY;
    private final Supplier<Component> textSupplier;
    private final int color;
    private final boolean shadow;

    // ── Constructors: fixed text ──────────────────────────────────────

    /**
     * @param childX X position within panel content area
     * @param childY Y position within panel content area
     * @param text   the text to display
     * @param color  ARGB text color (must include alpha byte, e.g., 0xFF404040)
     * @param shadow whether to render with a drop shadow
     */
    public TextLabel(int childX, int childY, Component text, int color, boolean shadow) {
        this(childX, childY, wrap(text), color, shadow);
    }

    /** Convenience: dark gray text, no shadow (vanilla label style). */
    public TextLabel(int childX, int childY, Component text) {
        this(childX, childY, text, COLOR_DARK, false);
    }

    // ── Constructors: supplier text ───────────────────────────────────

    /**
     * Supplier-driven text with explicit color and shadow. The supplier is
     * invoked each frame.
     */
    public TextLabel(int childX, int childY, Supplier<Component> text,
                     int color, boolean shadow) {
        this.childX = childX;
        this.childY = childY;
        this.textSupplier = text;
        this.color = color;
        this.shadow = shadow;
    }

    /** Convenience: supplier-driven text, dark gray, no shadow (vanilla label style). */
    public TextLabel(int childX, int childY, Supplier<Component> text) {
        this(childX, childY, text, COLOR_DARK, false);
    }

    /** Wraps a fixed Component into a one-shot supplier, unifying the render path. */
    private static Supplier<Component> wrap(Component text) {
        return () -> text;
    }

    // ── M8 Layout Spec ─────────────────────────────────────────────────

    /**
     * Returns an {@link com.trevorschoeny.menukit.core.layout.ElementSpec}
     * for static text. Width inferred from font metrics at spec construction
     * (single-shot evaluation of {@code text.getString()} via
     * {@code font.width(text)}); height is {@code font.lineHeight}.
     *
     * <p><b>Static text only.</b> For supplier-driven dynamic text, use the
     * explicit-dimension overload {@link #spec(int, int, Supplier)} —
     * supplier values can vary frame-to-frame and auto-inferred width
     * from a single supplier evaluation would freeze layout against a
     * stale snapshot.
     */
    public static com.trevorschoeny.menukit.core.layout.ElementSpec spec(Component text) {
        int w = Minecraft.getInstance().font.width(text);
        int h = Minecraft.getInstance().font.lineHeight;
        return new com.trevorschoeny.menukit.core.layout.ElementSpec() {
            @Override public int width()  { return w; }
            @Override public int height() { return h; }
            @Override public PanelElement at(int x, int y) {
                return new TextLabel(x, y, text);
            }
        };
    }

    /**
     * Returns an {@link com.trevorschoeny.menukit.core.layout.ElementSpec}
     * for supplier-driven text with consumer-declared dimensions. Required
     * path for dynamic content — Row/Column layout stays stable as
     * supplier values change at runtime because the consumer locks the
     * width up front.
     */
    public static com.trevorschoeny.menukit.core.layout.ElementSpec spec(
            int width, int height, Supplier<Component> text) {
        return new com.trevorschoeny.menukit.core.layout.ElementSpec() {
            @Override public int width()  { return width; }
            @Override public int height() { return height; }
            @Override public PanelElement at(int x, int y) {
                return new TextLabel(x, y, text);
            }
        };
    }

    // ── PanelElement Implementation ────────────────────────────────────

    @Override public int getChildX() { return childX; }
    @Override public int getChildY() { return childY; }

    @Override
    public int getWidth() {
        Component text = textSupplier.get();
        return text != null ? Minecraft.getInstance().font.width(text) : 0;
    }

    @Override
    public int getHeight() {
        return Minecraft.getInstance().font.lineHeight;
    }

    /** Returns the text content the TextLabel would render right now. Resolves the supplier. */
    public Component getCurrentText() { return textSupplier.get(); }

    /** Returns the ARGB text color. */
    public int getColor() { return color; }

    // ── Rendering ──────────────────────────────────────────────────────

    @Override
    public void render(RenderContext ctx) {
        Component text = textSupplier.get();
        if (text == null) return;
        ctx.graphics().drawString(
                Minecraft.getInstance().font,
                text,
                ctx.originX() + childX,
                ctx.originY() + childY,
                color,
                shadow);
    }

    // mouseClicked inherits the default no-op from PanelElement.
}
