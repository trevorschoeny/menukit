package com.trevorschoeny.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * A non-interactive text label within a {@link Panel}. Renders text at
 * a fixed position using {@code drawString}.
 *
 * <p><b>ARGB color requirement (1.21.11):</b> Colors must include an
 * explicit alpha byte (e.g., {@code 0xFF404040}, not {@code 0x404040}).
 * {@code GuiGraphics.drawString()} silently discards text when
 * {@code ARGB.alpha(color) == 0}. All color constants in this class
 * use the {@code 0xFF} prefix. Consumer code passing custom colors must
 * do the same.
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
    private final Component text;
    private final int color;
    private final boolean shadow;

    /**
     * @param childX X position within panel content area
     * @param childY Y position within panel content area
     * @param text   the text to display
     * @param color  ARGB text color (must include alpha byte, e.g., 0xFF404040)
     * @param shadow whether to render with a drop shadow
     */
    public TextLabel(int childX, int childY, Component text, int color, boolean shadow) {
        this.childX = childX;
        this.childY = childY;
        this.text = text;
        this.color = color;
        this.shadow = shadow;
    }

    /** Convenience: dark gray text, no shadow (vanilla label style). */
    public TextLabel(int childX, int childY, Component text) {
        this(childX, childY, text, COLOR_DARK, false);
    }

    // ── PanelElement Implementation ────────────────────────────────────

    @Override public int getChildX() { return childX; }
    @Override public int getChildY() { return childY; }

    @Override
    public int getWidth() {
        return Minecraft.getInstance().font.width(text);
    }

    @Override
    public int getHeight() {
        return Minecraft.getInstance().font.lineHeight;
    }

    /** Returns the text content. */
    public Component getText() { return text; }

    /** Returns the ARGB text color. */
    public int getColor() { return color; }

    // ── Rendering ──────────────────────────────────────────────────────

    @Override
    public void render(RenderContext ctx) {
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
