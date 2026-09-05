package com.trevlar.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.function.DoubleSupplier;
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
 *   <li><b>Supplier-driven value</b> — pass a {@code DoubleSupplier} returning
 *   a normalized {@code 0.0–1.0} value for progress that changes over time
 *   (the common case). This is the same canonical numeric-supplier shape that
 *   {@link Slider} and {@link ScrollContainer} read their normalized values
 *   from, so a {@code Slider}'s {@code double} value feeds a {@code ProgressBar}
 *   with no box-and-cast: {@code ProgressBar.spec(w, h, () -> s.value)}.</li>
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
 * Solid-color fills via {@code GuiGraphicsExtractor.fill()}. No textures; no sprite.
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
public class ProgressBar extends AbstractPanelElement<ProgressBar> {

    @Override protected ProgressBar self() { return this; }

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

    private int width;
    private int height;
    // Normalized 0.0–1.0 value source, read each frame. DoubleSupplier (not a
    // boxed Supplier<Float>) is the canonical numeric-supplier shape across the
    // library — Slider and ScrollContainer read their normalized values the same
    // way — so a double-valued source feeds this bar with no box-and-cast.
    private final DoubleSupplier valueSupplier;
    private final Direction direction;
    private final int fillColor;
    private final int bgColor;
    private final @Nullable Supplier<Component> label;

    // tooltipSupplier hoisted to AbstractPanelElement (Phase 18r-2).

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
     *
     * <p>{@code value} is a {@link DoubleSupplier} returning a normalized
     * {@code 0.0–1.0} progress — the same canonical numeric-supplier shape
     * {@link Slider} and {@link ScrollContainer} use, so a {@code double}-valued
     * source feeds this bar with no box-and-cast.
     */
    public ProgressBar(int childX, int childY, int width, int height,
                       DoubleSupplier value) {
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
     * @param value     normalized progress supplier ({@code DoubleSupplier},
     *                  value in {@code 0..1}); invoked each frame; result clamped to [0, 1]
     * @param direction fill direction
     * @param fillColor ARGB fill color (must include alpha byte)
     * @param bgColor   ARGB background color (must include alpha byte)
     * @param label     optional label supplier; null for no label
     */
    public ProgressBar(int childX, int childY, int width, int height,
                       DoubleSupplier value,
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

    /** Wraps a fixed float into a constant supplier, unifying the render path. */
    private static DoubleSupplier wrap(float value) {
        double constant = value;
        return () -> constant;
    }

    // ── M8 Layout Spec ─────────────────────────────────────────────────

    /**
     * Returns an {@link com.trevlar.menukit.core.layout.ElementSpec}
     * for a default-styled progress bar (left-to-right, default colors,
     * no label).
     *
     * <p>{@code value} is a {@link DoubleSupplier} returning a normalized
     * {@code 0.0–1.0} progress — the canonical numeric-supplier shape — so a
     * {@code Slider}'s {@code double} value feeds this with no cast:
     * {@code ProgressBar.spec(w, h, () -> s.slider)}.
     */
    public static com.trevlar.menukit.core.layout.ElementSpec spec(
            int width, int height, DoubleSupplier value) {
        return spec(width, height, value, DEFAULT_DIRECTION,
                DEFAULT_FILL_COLOR, DEFAULT_BG_COLOR, null);
    }

    /** Layout spec with full configuration. */
    public static com.trevlar.menukit.core.layout.ElementSpec spec(
            int width, int height, DoubleSupplier value,
            Direction direction, int fillColor, int bgColor,
            @Nullable Supplier<Component> label) {
        return new com.trevlar.menukit.core.layout.ElementSpec() {
            @Override public int width()  { return width; }
            @Override public int height() { return height; }
            @Override public PanelElement at(int x, int y) {
                return new ProgressBar(x, y, width, height, value,
                        direction, fillColor, bgColor, label);
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

    /** Column-fill (Pass 3): stretch this bar to the column's widest extent. */
    @Override public void fillWidth(int width) { this.authoredWidth = width; this.width = width; }

    /** Natural (authored) width before any panel constraint. */
    @Override public int naturalWidth() { return authoredW(); }

    /** Cap the bar to the panel's budget so it never bleeds; reversible. */
    @Override public void layoutWithin(int budget) { this.width = Math.min(authoredW(), budget); }

    @Override
    public void render(RenderContext ctx) {
        var graphics = ctx.graphics();
        int drawX = ctx.originX() + childX;
        int drawY = ctx.originY() + childY;

        // Background
        graphics.fill(drawX, drawY, drawX + width, drawY + height, bgColor);

        // Value clamped to [0, 1] — silent per the class javadoc. Read as a
        // double from the canonical DoubleSupplier shape, then cast to the float
        // the fill arithmetic needs.
        float v = (float) Math.max(0.0, Math.min(1.0, valueSupplier.getAsDouble()));

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

        // Optional label — centered on the 2D bar bounds.
        // Scroll-on-overflow via MKText: when the label is longer than
        // the bar's width, it scrolls back and forth within the bar
        // instead of overflowing the bar bounds.
        if (label != null) {
            Component text = label.get();
            if (text != null) {
                MKText.renderCentered(graphics, text,
                        drawX, drawY, width, height,
                        0xFFFFFFFF, true);
            }
        }

        // Tooltip — queues if cursor is over the bar bounds.
        Supplier<Component> tooltipSupplier = getTooltipSupplier();
        if (tooltipSupplier != null && ctx.hasMouseInput() && isHovered(ctx)) {
            Component ttText = tooltipSupplier.get();
            if (ttText != null) {
                MKTooltip.queue(graphics, ttText,
                        ctx.mouseX(), ctx.mouseY());
            }
        }
    }

    // ── Chainable configuration ────────────────────────────────────────
    //
    // showWhen + tooltip + at return ProgressBar for free via the SELF self-type.

    /**
     * Fluent resize sugar — sets the bar's pixel dimensions and returns this
     * bar for chaining. Additive to the positional constructors.
     */
    public ProgressBar size(int width, int height) {
        this.authoredWidth = width;   // re-author the cap intent (matches Button.size)
        this.width = width;
        this.height = height;
        return this;
    }

    // mouseClicked + isHovered inherit from PanelElement. isVisible +
    // setVisible inherit from AbstractPanelElement (Phase 18r-2).

    // ── Element Queries ────────────────────────────────────────────────

    /** Returns the current progress value, clamped to [0, 1]. Resolves the supplier. */
    public float getCurrentValue() {
        return (float) Math.max(0.0, Math.min(1.0, valueSupplier.getAsDouble()));
    }

    /** Returns the fill direction. */
    public Direction getDirection() { return direction; }

    /** Returns the ARGB fill color. */
    public int getFillColor() { return fillColor; }

    /** Returns the ARGB background color. */
    public int getBgColor() { return bgColor; }
}
