package com.trevorschoeny.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import org.jspecify.annotations.Nullable;

import java.util.List;
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
public class TextLabel extends AbstractPanelElement {

    /** Dark gray with shadow off — matches vanilla container labels on light backgrounds. */
    public static final int COLOR_DARK = 0xFF404040;

    /** White with shadow on — readable on dark panel backgrounds. */
    public static final int COLOR_LIGHT = 0xFFFFFFFF;

    private final int childX;
    private final int childY;
    private final Supplier<Component> textSupplier;
    private final int color;
    private final boolean shadow;

    // ── Phase 16g Auto-Wrap ────────────────────────────────────────────
    // When wrapWidth > 0, the label renders multi-line — text is split via
    // font.split(text, wrapWidth) and each FormattedCharSequence is drawn
    // at successive lineHeight offsets. getHeight() then returns
    // lineCount × lineHeight instead of single-line lineHeight. Set by the
    // owning Panel during its configuration pass when pinnedWidth is on
    // the Panel (M5 trigger). Zero (default) = single-line legacy behavior.
    //
    // Mutable rather than final because Panels set wrapWidth lazily once
    // they know their content width; constructing TextLabels with wrap
    // semantics baked in would invert that dependency. See Panel.java's
    // propagateConfiguration() for the propagation entry point.
    private int wrapWidth = 0;

    // tooltipSupplier hoisted to AbstractPanelElement (Phase 18r-2).

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
        if (text == null) return 0;
        // In wrap mode the label takes the full wrapWidth as its declared
        // extent (so panel layout reserves the right amount of horizontal
        // space). In non-wrap mode the width is the natural rendered width.
        if (wrapWidth > 0) return wrapWidth;
        return Minecraft.getInstance().font.width(text);
    }

    @Override
    public int getHeight() {
        if (wrapWidth > 0) {
            // Multi-line: ask the vanilla font splitter how many wrapped
            // lines this text produces at the current wrapWidth, then
            // multiply by lineHeight. font.split() is the same call vanilla
            // uses for chat / tooltips / book pages, so wrap semantics
            // match player expectations.
            Component text = textSupplier.get();
            if (text == null) return 0;
            var font = Minecraft.getInstance().font;
            List<FormattedCharSequence> lines = font.split(text, wrapWidth);
            int lineCount = Math.max(1, lines.size());
            return lineCount * font.lineHeight;
        }
        return Minecraft.getInstance().font.lineHeight;
    }

    /** Returns the text content the TextLabel would render right now. Resolves the supplier. */
    public Component getCurrentText() { return textSupplier.get(); }

    /** Returns the ARGB text color. */
    public int getColor() { return color; }

    // ── Auto-Wrap (Phase 16g) ──────────────────────────────────────────

    /**
     * Sets the maximum horizontal pixel width this label may occupy. Zero
     * (the default) disables wrapping — the label renders on a single line
     * sized to its natural text width.
     *
     * <p>When non-zero, the label switches to multi-line mode:
     * {@link #getHeight()} returns {@code lineCount × font.lineHeight} based
     * on {@code font.split(text, wrapWidth)}, and {@link #render} draws each
     * wrapped line at successive vertical offsets.
     *
     * <p>Called by the owning {@link Panel}'s configuration pass when the
     * Panel has a {@code pinnedWidth} (Phase 16g auto-wrap trigger).
     * Consumers don't typically call this directly — set {@code pinnedWidth}
     * on the Panel and the propagation runs automatically.
     *
     * <p><b>Dynamic-supplier caveat:</b> with supplier-driven text,
     * {@code font.split} is re-evaluated each frame. If the supplier returns
     * different-length text per frame, the wrapped line count (and thus
     * panel height) can fluctuate. Same stability caveat documented on the
     * class javadoc applies here.
     *
     * @param wrapWidth horizontal pixel budget for wrapped lines, or 0 to
     *                  disable wrapping.
     */
    public void setWrapWidth(int wrapWidth) {
        this.wrapWidth = Math.max(0, wrapWidth);
    }

    /** Returns the current wrap width. Zero means wrap is disabled. */
    public int getWrapWidth() {
        return wrapWidth;
    }

    // ── Rendering ──────────────────────────────────────────────────────

    @Override
    public void render(RenderContext ctx) {
        Component text = textSupplier.get();
        if (text == null) return;
        var font = Minecraft.getInstance().font;
        int x = ctx.originX() + childX;
        int y = ctx.originY() + childY;

        if (wrapWidth > 0) {
            // Multi-line render: split into FormattedCharSequence lines and
            // draw each at successive lineHeight offsets. drawString accepts
            // FormattedCharSequence directly (same path tooltips + book pages
            // use), so wrap rendering rides on the existing vanilla pipeline.
            List<FormattedCharSequence> lines = font.split(text, wrapWidth);
            int lineY = y;
            for (FormattedCharSequence line : lines) {
                ctx.graphics().drawString(font, line, x, lineY, color, shadow);
                lineY += font.lineHeight;
            }
        } else {
            // Legacy single-line path.
            ctx.graphics().drawString(font, text, x, y, color, shadow);
        }

        // Tooltip — queues if cursor is over the label bounds.
        Supplier<Component> tooltipSupplier = getTooltipSupplier();
        if (tooltipSupplier != null && ctx.hasMouseInput() && isHovered(ctx)) {
            Component ttText = tooltipSupplier.get();
            if (ttText != null) {
                ctx.graphics().setTooltipForNextFrame(
                        font, ttText, ctx.mouseX(), ctx.mouseY());
            }
        }
    }

    // ── Chainable configuration (Phase 18r-2: covariant returns) ───────

    @Override
    public TextLabel tooltip(Component text) {
        super.tooltip(text);
        return this;
    }

    @Override
    public TextLabel tooltip(@Nullable Supplier<Component> supplier) {
        super.tooltip(supplier);
        return this;
    }

    @Override
    public TextLabel showWhen(@Nullable Supplier<Boolean> supplier) {
        super.showWhen(supplier);
        return this;
    }

    // mouseClicked inherits the default no-op from PanelElement.
    // isVisible + setVisible inherit from AbstractPanelElement (Phase 18r-2).
}
