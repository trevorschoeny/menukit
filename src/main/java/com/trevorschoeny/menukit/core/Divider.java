package com.trevorschoeny.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

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
public class Divider extends AbstractPanelElement<Divider> {

    @Override protected Divider self() { return this; }

    /** Default separator color — vanilla inventory-label dark gray. */
    public static final int DEFAULT_COLOR = 0xFF404040;

    /** Default thickness in pixels. */
    public static final int DEFAULT_THICKNESS = 1;

    private int width;
    private int height;
    private final int color;

    // tooltipSupplier hoisted to AbstractPanelElement (Phase 18r-2).

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

    // ── M8 Layout Specs ────────────────────────────────────────────────

    /** Layout spec for a horizontal divider with default color + thickness. */
    public static com.trevorschoeny.menukit.core.layout.ElementSpec horizontalSpec(int length) {
        return horizontalSpec(length, DEFAULT_COLOR, DEFAULT_THICKNESS);
    }

    /** Layout spec for a horizontal divider with explicit color + thickness. */
    public static com.trevorschoeny.menukit.core.layout.ElementSpec horizontalSpec(
            int length, int color, int thickness) {
        return new com.trevorschoeny.menukit.core.layout.ElementSpec() {
            @Override public int width()  { return length; }
            @Override public int height() { return thickness; }
            @Override public PanelElement at(int x, int y) {
                return Divider.horizontal(x, y, length, color, thickness);
            }
        };
    }

    /** Layout spec for a vertical divider with default color + thickness. */
    public static com.trevorschoeny.menukit.core.layout.ElementSpec verticalSpec(int length) {
        return verticalSpec(length, DEFAULT_COLOR, DEFAULT_THICKNESS);
    }

    /** Layout spec for a vertical divider with explicit color + thickness. */
    public static com.trevorschoeny.menukit.core.layout.ElementSpec verticalSpec(
            int length, int color, int thickness) {
        return new com.trevorschoeny.menukit.core.layout.ElementSpec() {
            @Override public int width()  { return thickness; }
            @Override public int height() { return length; }
            @Override public PanelElement at(int x, int y) {
                return Divider.vertical(x, y, length, color, thickness);
            }
        };
    }

    // ── PanelElement Implementation ────────────────────────────────────

    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }

    // Authored width for the reactive cap (Verification-4) — see Button.
    private int authoredWidth = Integer.MIN_VALUE;
    private int authoredW() {
        if (authoredWidth == Integer.MIN_VALUE) authoredWidth = width;
        return authoredWidth;
    }

    /**
     * Column-fill (Pass 3): stretch a HORIZONTAL divider to the column's
     * widest extent — the canonical "section separator spans the column"
     * use. A vertical divider (taller than it is wide) is left untouched:
     * filling its width would thicken the line, not lengthen it.
     */
    @Override
    public void fillWidth(int width) {
        if (this.width >= this.height) { // horizontal orientation
            this.authoredWidth = width;
            this.width = width;
        }
    }

    /** Natural (authored) length before any panel constraint. */
    @Override public int naturalWidth() { return authoredW(); }

    /**
     * Cap a HORIZONTAL divider to the panel's budget so it never bleeds past
     * the edge; reversible. A vertical divider (taller than wide) is left
     * alone — capping its width would thin the line, not shorten it.
     */
    @Override
    public void layoutWithin(int budget) {
        if (authoredW() >= height) {
            this.width = Math.min(authoredW(), budget);
        }
    }

    @Override
    public void render(RenderContext ctx) {
        int x = ctx.originX() + childX;
        int y = ctx.originY() + childY;
        ctx.graphics().fill(x, y, x + width, y + height, color);

        // Tooltip — fires over the divider bounds. Useful even on a 1px
        // line: hover area is the declared width × height, which can be
        // padded by the consumer if needed.
        Supplier<Component> tooltipSupplier = getTooltipSupplier();
        if (tooltipSupplier != null && ctx.hasMouseInput() && isHovered(ctx)) {
            Component ttText = tooltipSupplier.get();
            if (ttText != null) {
                MKTooltip.queue(ctx.graphics(), ttText,
                        ctx.mouseX(), ctx.mouseY());
            }
        }
    }

    // mouseClicked, isHovered inherit defaults from PanelElement. isVisible
    // + setVisible inherit from AbstractPanelElement (Phase 18r-2).

    // ── Chainable configuration ────────────────────────────────────────
    //
    // showWhen + tooltip + at return Divider for free via the SELF self-type.

    /**
     * Fluent resize sugar — sets the divider's raw pixel extent (width ×
     * height) and returns this divider for chaining. Note the divider's
     * orientation is fixed by the {@code horizontal(...)} / {@code vertical(...)}
     * factory it was created from; {@code .size()} overrides the raw bounds
     * directly, so callers should pass the extent in the same axis convention
     * (a horizontal divider is {@code (length, thickness)}, a vertical one is
     * {@code (thickness, length)}).
     */
    public Divider size(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    /** Returns the divider's ARGB color. */
    public int getColor() { return color; }
}
