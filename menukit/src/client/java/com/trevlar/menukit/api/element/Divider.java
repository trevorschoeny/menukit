package com.trevlar.menukit.api.element;

/**
 * A line between sections of a panel: a solid fill, no texture, no input.
 *
 * <pre>{@code
 * Divider.horizontal().at(0, y).build();                 // fills the panel's width
 * Divider.horizontal().at(0, y).size(160, 1).build();    // 160 px long
 * Divider.vertical().at(x, 0).size(1, 40).build();       // 40 px tall
 * }</pre>
 *
 * <h3>Filling (§0066)</h3>
 *
 * A horizontal divider given no {@code size} fills whatever width its panel or
 * container gives it, every layout pass, and asks for none itself, so it never
 * widens the panel: the section rule of a settings body, as wide as the body
 * whatever the window's size. A vertical divider needs a size.
 *
 * <p>With a {@code size}, a horizontal divider is {@code (length, thickness)} and
 * is capped to its panel's width; a vertical one is {@code (thickness, length)}.
 */
public class Divider extends AbstractPanelElement {

    /** Default colour: vanilla's container-label grey. */
    public static final int DEFAULT_COLOR = ElementConstants.TEXT_DARK;

    /** Default thickness in pixels. */
    public static final int DEFAULT_THICKNESS = 1;

    private final int color;
    private final boolean horizontal;
    private final boolean fills;

    protected Divider(Builder b) {
        super(b);
        this.color = b.color;
        this.horizontal = b.horizontal;
        // A horizontal divider built without a size fills: it starts with no width
        // (the layout pass hands it the width it fills) and the builder's thickness.
        this.fills = horizontal && b.width < 0;
        if (fills) {
            this.width = 0;
            this.authoredWidth = 0;
            this.height = b.thickness;
        }
    }

    /** A horizontal divider. Without a {@code size} it fills its panel's width. */
    public static Builder horizontal() {
        return new Builder(true);
    }

    /** A vertical divider. Give it {@code size(thickness, length)}. */
    public static Builder vertical() {
        return new Builder(false);
    }

    /** A filling divider asks for no width, so it never widens its panel. */
    @Override
    public int naturalWidth() {
        return fills ? 0 : authoredWidth;
    }

    /** A filling divider takes the whole budget; a sized horizontal one caps to it; a vertical one keeps its size. */
    @Override
    public void layoutWithin(int budget) {
        if (fills) width = budget;
        else if (horizontal) width = Math.min(authoredWidth, budget);
    }

    /** Column fill lengthens a horizontal divider; a vertical one would only thicken. */
    @Override
    public void fillWidth(int width) {
        if (horizontal) super.fillWidth(width);
    }

    @Override
    public void render(RenderContext ctx) {
        int x = ctx.originX() + childX;
        int y = ctx.originY() + childY;
        ctx.graphics().fill(x, y, x + width, y + height, color);
        queueTooltip(ctx);
    }

    /** The divider's ARGB colour. */
    public int getColor() { return color; }

    // ── Builder ────────────────────────────────────────────────────────

    public static class Builder extends AbstractPanelElement.Builder<Divider, Builder> {
        private final boolean horizontal;
        private int color = DEFAULT_COLOR;
        private int thickness = DEFAULT_THICKNESS;

        Builder(boolean horizontal) {
            this.horizontal = horizontal;
        }

        @Override protected Builder self() { return this; }

        /**
         * The raw extent: {@code (length, thickness)} for a horizontal divider,
         * {@code (thickness, length)} for a vertical one. A horizontal divider
         * without it fills.
         */
        @Override
        public Builder size(int width, int height) {
            return super.size(width, height);
        }

        /** The thickness of a filling divider, in pixels. Default 1. */
        public Builder thickness(int pixels) {
            this.thickness = Math.max(1, pixels);
            return this;
        }

        /** ARGB colour, alpha byte included. Default {@link #DEFAULT_COLOR}. */
        public Builder color(int argb) {
            this.color = argb;
            return this;
        }

        @Override
        public Divider build() {
            require(horizontal || (width > 0 && height > 0), "a vertical divider needs size(thickness, length)");
            return new Divider(this);
        }
    }
}
