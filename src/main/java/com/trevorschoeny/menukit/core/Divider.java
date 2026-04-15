package com.trevorschoeny.menukit.core;

/**
 * A horizontal or vertical line separating content sections within a panel.
 * Pure visual, no interaction, no state.
 *
 * <p>Works in all three rendering contexts. Render-only element — no input
 * consequence, no variable content.
 *
 * <p>Rendered as a solid-color fill via {@code GuiGraphics.fill()}, not as
 * a sprite. A divider doesn't need a texture: a colored rectangle is both
 * simpler and correct. Consumers who want textured separators implement
 * {@link PanelElement} directly.
 *
 * <p>Constructed via factories rather than a public constructor —
 * horizontal and vertical are orthogonal enough that a direction enum
 * would be a meaningless discriminator at every call site.
 *
 * <h3>Default visual</h3>
 * <ul>
 *   <li>Color: {@link #DEFAULT_COLOR} — vanilla inventory-label dark gray.</li>
 *   <li>Thickness: {@link #DEFAULT_THICKNESS} — 1 pixel.</li>
 * </ul>
 *
 * <h3>Scope</h3>
 * <ul>
 *   <li>Solid color only — no gradient, pattern, or textured rendering.</li>
 *   <li>No automatic length — divider length is explicit; consumers compute
 *   their desired length from panel dimensions themselves.</li>
 *   <li>Rectangle only — no rounded ends or caps.</li>
 * </ul>
 *
 * @see PanelElement  The interface this implements
 */
public class Divider implements PanelElement {

    /** Default separator color — vanilla inventory-label dark gray. */
    public static final int DEFAULT_COLOR = 0xFF404040;

    /** Default thickness in pixels. */
    public static final int DEFAULT_THICKNESS = 1;

    private final int childX;
    private final int childY;
    private final int width;
    private final int height;
    private final int color;

    private Divider(int childX, int childY, int width, int height, int color) {
        this.childX = childX;
        this.childY = childY;
        this.width = width;
        this.height = height;
        this.color = color;
    }

    // ── Factories ─────────────────────────────────────────────────────

    /**
     * A horizontal divider with the default color and thickness.
     *
     * @param childX X position within panel content area
     * @param childY Y position within panel content area
     * @param length horizontal extent in pixels
     */
    public static Divider horizontal(int childX, int childY, int length) {
        return horizontal(childX, childY, length, DEFAULT_COLOR, DEFAULT_THICKNESS);
    }

    /**
     * A horizontal divider with explicit color and thickness.
     *
     * @param childX    X position within panel content area
     * @param childY    Y position within panel content area
     * @param length    horizontal extent in pixels
     * @param color     ARGB color (must include alpha byte, e.g. 0xFF404040)
     * @param thickness vertical extent in pixels
     */
    public static Divider horizontal(int childX, int childY, int length,
                                     int color, int thickness) {
        return new Divider(childX, childY, length, thickness, color);
    }

    /**
     * A vertical divider with the default color and thickness.
     *
     * @param childX X position within panel content area
     * @param childY Y position within panel content area
     * @param length vertical extent in pixels
     */
    public static Divider vertical(int childX, int childY, int length) {
        return vertical(childX, childY, length, DEFAULT_COLOR, DEFAULT_THICKNESS);
    }

    /**
     * A vertical divider with explicit color and thickness.
     *
     * @param childX    X position within panel content area
     * @param childY    Y position within panel content area
     * @param length    vertical extent in pixels
     * @param color     ARGB color (must include alpha byte, e.g. 0xFF404040)
     * @param thickness horizontal extent in pixels
     */
    public static Divider vertical(int childX, int childY, int length,
                                   int color, int thickness) {
        return new Divider(childX, childY, thickness, length, color);
    }

    // ── PanelElement Implementation ────────────────────────────────────

    @Override public int getChildX() { return childX; }
    @Override public int getChildY() { return childY; }
    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }

    @Override
    public void render(RenderContext ctx) {
        int x = ctx.originX() + childX;
        int y = ctx.originY() + childY;
        ctx.graphics().fill(x, y, x + width, y + height, color);
    }

    // mouseClicked, isVisible, isHovered inherit defaults from PanelElement.

    /** Returns the divider's ARGB color. */
    public int getColor() { return color; }
}
