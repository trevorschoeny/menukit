package com.trevorschoeny.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * A filled bar indicating progress on a 0-to-1 scale. The "bounded-progress
 * indicator" primitive of the component library.
 *
 * <p>Works in all three rendering contexts. Render-only element.
 *
 * <p>Two forms for the progress value:
 * <ul>
 *   <li><b>Fixed value</b> — pass a {@code float} directly. Unusual; useful
 *   for static-progress decorative bars.</li>
 *   <li><b>Supplier-driven value</b> — pass a {@code Supplier<Float>} for
 *   progress that changes over time (the common case).</li>
 * </ul>
 *
 * <p>Configuration fixed at construction: fill direction ({@link Direction}),
 * fill color, background color, and optional label.
 *
 * <h3>Clamping</h3>
 * Values outside [0, 1] are clamped silently. A value of {@code 1.5f} renders
 * as a full bar; a value of {@code -0.5f} renders as empty. No exception,
 * no warning. This is deliberate — progress computations sometimes
 * legitimately overshoot (a timer tick briefly exceeding duration before
 * reset), and exceptions on progress values would be noisy. Consumers
 * debugging unexpected display can rely on this documented behavior.
 *
 * <h3>Label positioning</h3>
 * If a label is supplied, it renders centered on the bar's 2D bounds —
 * horizontal-center and vertical-center of the bar rectangle. Vertical bars
 * still render the label on the same 2D center (not rotated). Consumers
 * wanting a label above or below a vertical bar position a separate
 * {@link TextLabel} alongside the bar.
 *
 * <h3>Rendering</h3>
 * Solid-color fills via {@code GuiGraphics.fill()}. No textures; no sprite.
 * Consumers wanting themed sprite-backed bars implement {@link PanelElement}
 * directly.
 *
 * <h3>Scope</h3>
 * <ul>
 *   <li>No animation — value changes render immediately per frame.</li>
 *   <li>No multi-segment bars — consumers compose multiple ProgressBars
 *   for segmented displays.</li>
 *   <li>No percentage formatting — label supplier returns literal text.</li>
 * </ul>
 *
 * @see PanelElement The interface this implements
 */
public class ProgressBar implements PanelElement {

    /** Fill direction for the progress bar. */
    public enum Direction {
        LEFT_TO_RIGHT,
        RIGHT_TO_LEFT,
        BOTTOM_TO_TOP,
        TOP_TO_BOTTOM
    }

    /** Default fill color — white. */
    public static final int DEFAULT_FILL_COLOR = 0xFFFFFFFF;

    /** Default background color — dark gray. */
    public static final int DEFAULT_BG_COLOR = 0xFF333333;

    /** Default direction — left-to-right. */
    public static final Direction DEFAULT_DIRECTION = Direction.LEFT_TO_RIGHT;

    private final int childX;
    private final int childY;
    private final int width;
    private final int height;
    private final Supplier<Float> valueSupplier;
    private final Direction direction;
    private final int fillColor;
    private final int bgColor;
    private final @Nullable Supplier<Component> label;

    // ── Constructors: fixed value ─────────────────────────────────────

    /**
     * Creates a ProgressBar with a fixed value, left-to-right direction,
     * default colors, and no label.
     */
    public ProgressBar(int childX, int childY, int width, int height, float value) {
        this(childX, childY, width, height, wrap(value),
                DEFAULT_DIRECTION, DEFAULT_FILL_COLOR, DEFAULT_BG_COLOR, null);
    }

    /**
     * Creates a ProgressBar with a fixed value and full configuration.
     *
     * @param childX    X position within panel content area
     * @param childY    Y position within panel content area
     * @param width     bar width in pixels
     * @param height    bar height in pixels
     * @param value     progress value (clamped to [0, 1])
     * @param direction fill direction
     * @param fillColor ARGB fill color (must include alpha byte)
     * @param bgColor   ARGB background color (must include alpha byte)
     * @param label     optional label supplier; null for no label
     */
    public ProgressBar(int childX, int childY, int width, int height,
                       float value,
                       Direction direction, int fillColor, int bgColor,
                       @Nullable Supplier<Component> label) {
        this(childX, childY, width, height, wrap(value),
                direction, fillColor, bgColor, label);
    }

    // ── Constructors: supplier-driven value ───────────────────────────

    /**
     * Creates a ProgressBar with a supplier-driven value, left-to-right
     * direction, default colors, and no label.
     */
    public ProgressBar(int childX, int childY, int width, int height,
                       Supplier<Float> value) {
        this(childX, childY, width, height, value,
                DEFAULT_DIRECTION, DEFAULT_FILL_COLOR, DEFAULT_BG_COLOR, null);
    }

    /**
     * Creates a ProgressBar with a supplier-driven value and full configuration.
     *
     * @param childX    X position within panel content area
     * @param childY    Y position within panel content area
     * @param width     bar width in pixels
     * @param height    bar height in pixels
     * @param value     progress supplier; invoked each frame; result clamped to [0, 1]
     * @param direction fill direction
     * @param fillColor ARGB fill color (must include alpha byte)
     * @param bgColor   ARGB background color (must include alpha byte)
     * @param label     optional label supplier; null for no label
     */
    public ProgressBar(int childX, int childY, int width, int height,
                       Supplier<Float> value,
                       Direction direction, int fillColor, int bgColor,
                       @Nullable Supplier<Component> label) {
        this.childX = childX;
        this.childY = childY;
        this.width = width;
        this.height = height;
        this.valueSupplier = value;
        this.direction = direction;
        this.fillColor = fillColor;
        this.bgColor = bgColor;
        this.label = label;
    }

    /** Wraps a fixed float into a one-shot supplier, unifying the render path. */
    private static Supplier<Float> wrap(float value) {
        Float boxed = value;
        return () -> boxed;
    }

    // ── PanelElement Implementation ────────────────────────────────────

    @Override public int getChildX() { return childX; }
    @Override public int getChildY() { return childY; }
    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }

    @Override
    public void render(RenderContext ctx) {
        var graphics = ctx.graphics();
        int drawX = ctx.originX() + childX;
        int drawY = ctx.originY() + childY;

        // Background
        graphics.fill(drawX, drawY, drawX + width, drawY + height, bgColor);

        // Value clamped to [0, 1] — silent per the class javadoc
        float v = Math.max(0f, Math.min(1f, valueSupplier.get()));

        // Fill according to direction
        switch (direction) {
            case LEFT_TO_RIGHT -> {
                int filled = (int) (v * width);
                graphics.fill(drawX, drawY, drawX + filled, drawY + height, fillColor);
            }
            case RIGHT_TO_LEFT -> {
                int filled = (int) (v * width);
                graphics.fill(drawX + width - filled, drawY,
                        drawX + width, drawY + height, fillColor);
            }
            case BOTTOM_TO_TOP -> {
                int filled = (int) (v * height);
                graphics.fill(drawX, drawY + height - filled,
                        drawX + width, drawY + height, fillColor);
            }
            case TOP_TO_BOTTOM -> {
                int filled = (int) (v * height);
                graphics.fill(drawX, drawY, drawX + width, drawY + filled, fillColor);
            }
        }

        // Optional label — centered on the 2D bar bounds
        if (label != null) {
            Component text = label.get();
            if (text != null) {
                var mc = Minecraft.getInstance();
                int tw = mc.font.width(text);
                int tx = drawX + (width - tw) / 2;
                int ty = drawY + (height - mc.font.lineHeight) / 2;
                graphics.drawString(mc.font, text, tx, ty, 0xFFFFFFFF, true);
            }
        }
    }

    // mouseClicked, isVisible, isHovered inherit defaults from PanelElement.

    // ── Element Queries ────────────────────────────────────────────────

    /** Returns the current progress value, clamped to [0, 1]. Resolves the supplier. */
    public float getCurrentValue() {
        return Math.max(0f, Math.min(1f, valueSupplier.get()));
    }

    /** Returns the fill direction. */
    public Direction getDirection() { return direction; }

    /** Returns the ARGB fill color. */
    public int getFillColor() { return fillColor; }

    /** Returns the ARGB background color. */
    public int getBgColor() { return bgColor; }
}
