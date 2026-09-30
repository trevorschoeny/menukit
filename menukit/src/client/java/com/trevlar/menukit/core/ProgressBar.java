package com.trevlar.menukit.core;

import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * A bar filled to a fraction, read every frame: health, progress, a timer. No
 * input.
 *
 * <pre>{@code
 * ProgressBar.builder().size(80, 6).value(() -> boat.health() / 40.0)
 *         .fillColor(0xFFC08040).label(() -> Component.literal("Hull")).build();
 * }</pre>
 *
 * <p>{@code value} is read-only: the bar shows a fraction (clamped to 0..1) and
 * never writes one, so it takes no {@code state}. It fills in any of four
 * directions; an optional label is centred on it. Caps to its panel's width.
 */
public class ProgressBar extends AbstractPanelElement {

    /** Which way the fill grows. */
    public enum Direction { LEFT_TO_RIGHT, RIGHT_TO_LEFT, BOTTOM_TO_TOP, TOP_TO_BOTTOM }

    public static final int DEFAULT_FILL_COLOR = 0xFFFFFFFF;
    public static final int DEFAULT_BG_COLOR = 0xFF333333;
    public static final Direction DEFAULT_DIRECTION = Direction.LEFT_TO_RIGHT;

    private final DoubleSupplier value;
    private final Direction direction;
    private final int fillColor;
    private final int bgColor;
    private final @Nullable Supplier<Component> label;

    protected ProgressBar(Builder b) {
        super(b);
        this.value = b.value;
        this.direction = b.direction;
        this.fillColor = b.fillColor;
        this.bgColor = b.bgColor;
        this.label = b.label;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The fraction the bar would show right now, clamped to 0..1. */
    public float getCurrentValue() {
        return (float) Math.max(0.0, Math.min(1.0, value.getAsDouble()));
    }

    @Override
    public void render(RenderContext ctx) {
        var g = ctx.graphics();
        int x = ctx.originX() + childX;
        int y = ctx.originY() + childY;
        g.fill(x, y, x + width, y + height, bgColor);
        float v = getCurrentValue();
        int fill = disabled(ctx) ? ElementConstants.TEXT_DISABLED : fillColor;
        switch (direction) {
            case LEFT_TO_RIGHT -> g.fill(x, y, x + (int) (v * width), y + height, fill);
            case RIGHT_TO_LEFT -> g.fill(x + width - (int) (v * width), y, x + width, y + height, fill);
            case BOTTOM_TO_TOP -> g.fill(x, y + height - (int) (v * height), x + width, y + height, fill);
            case TOP_TO_BOTTOM -> g.fill(x, y, x + width, y + (int) (v * height), fill);
        }
        if (label != null) {
            Component text = label.get();
            if (text != null) MKText.renderCentered(g, text, x, y, width, height, ElementConstants.TEXT_LIGHT, true);
        }
        queueTooltip(ctx);
    }

    public static class Builder extends AbstractPanelElement.Builder<ProgressBar, Builder> {
        private DoubleSupplier value = () -> 0.0;
        private Direction direction = DEFAULT_DIRECTION;
        private int fillColor = DEFAULT_FILL_COLOR;
        private int bgColor = DEFAULT_BG_COLOR;
        private @Nullable Supplier<Component> label;

        protected Builder() {}

        @Override protected Builder self() { return this; }

        /** Required: size in pixels. */
        @Override
        public Builder size(int width, int height) {
            return super.size(width, height);
        }

        /** The fraction to show, 0 to 1, read every frame. Default 0. */
        public Builder value(DoubleSupplier value) {
            this.value = Objects.requireNonNull(value, "value");
            return this;
        }

        /** Which way the fill grows. Default left to right. */
        public Builder direction(Direction direction) {
            this.direction = Objects.requireNonNull(direction, "direction");
            return this;
        }

        /** ARGB fill colour. Default white. */
        public Builder fillColor(int argb) {
            this.fillColor = argb;
            return this;
        }

        /** ARGB background colour. Default dark grey. */
        public Builder bgColor(int argb) {
            this.bgColor = argb;
            return this;
        }

        /** Text centred on the bar, read every frame. */
        public Builder label(Supplier<Component> label) {
            this.label = Objects.requireNonNull(label, "label");
            return this;
        }

        @Override
        public ProgressBar build() {
            require(width > 0 && height > 0, "size(w, h) is required");
            return new ProgressBar(this);
        }
    }
}
