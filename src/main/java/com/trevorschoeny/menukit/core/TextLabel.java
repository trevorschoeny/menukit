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
 * <p><b>ARGB color requirement (26.2):</b> Colors must include an
 * explicit alpha byte (e.g., {@code 0xFF404040}, not {@code 0x404040}).
 * {@code GuiGraphicsExtractor.text()} silently discards text when
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
 * <h3>Construction — prefer {@link #spec} / {@code .at(x,y)}</h3>
 *
 * The positional constructors take {@code childX}/{@code childY} as their
 * leading arguments. The fluent path keeps the position out of the argument
 * list: use {@link #spec(Component)} (or {@link #spec(int, int, Supplier)} for
 * supplier-driven text) to drop a label into a {@code Row}/{@code Column}
 * layout, or construct without a position and call
 * {@link AbstractPanelElement#at(int, int) .at(x, y)}. The positional
 * constructors remain for the cases where threading {@code (x, y)} through the
 * constructor is the clearest spelling; they are not deprecated, just no
 * longer the only path.
 *
 * @see PanelElement  The interface this implements
 * @see Button        Interactive button element
 */
public class TextLabel extends AbstractPanelElement<TextLabel> {

    @Override protected TextLabel self() { return this; }

    /** Dark gray with shadow off — matches vanilla container labels on light backgrounds. */
    public static final int COLOR_DARK = 0xFF404040;

    /** White with shadow on — readable on dark panel backgrounds. */
    public static final int COLOR_LIGHT = 0xFFFFFFFF;

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

    // ── Render scale + backdrop + onRender (folded from MKHudText) ──────
    // These three were the only things the former HUD-only MKHudText class had
    // that plain TextLabel lacked. Folding them in makes HUD text a TextLabel
    // VARIANT (configured by the HUD builder) instead of a parallel class that
    // duplicated the render path and could never wrap. All three default to the
    // no-op value, so every existing TextLabel is byte-identical to pre-fold.
    //
    // renderScale: a uniform glyph scale applied via the GUI pose matrix at
    // render. getWidth/getHeight/naturalWidth report the SCALED extent so panel
    // layout reserves the right space. 1.0 = no scale (the matrix push is skipped
    // entirely — the default path is unchanged). Composes with wrap: wrapWidth
    // stays a FONT-space number (font.split works on the native grid); the scale
    // is applied only at the screen-pixel boundary and inside the render matrix.
    private float renderScale = 1.0f;
    // backdrop: a translucent dark plate behind the text (HUD readability over
    // the world), sized to the text bounds — multi-line when wrapped. Default off.
    private boolean backdrop = false;
    // onRender: optional per-frame side-effect fired at the top of render()
    // (HUD render-tick counters / animation drivers). Null = none.
    private @Nullable Runnable onRender = null;

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


    @Override
    public int getWidth() {
        Component text = textSupplier.get();
        if (text == null) return 0;
        // In wrap mode the label takes the full wrapWidth (font space) as its
        // declared extent (so panel layout reserves the right horizontal space);
        // in non-wrap mode the width is the natural rendered width. Either way the
        // renderScale is applied at this screen-pixel boundary (1.0 = identity, so
        // the unscaled path is unchanged).
        int fontWidth = (wrapWidth > 0) ? wrapWidth : Minecraft.getInstance().font.width(text);
        return (int) (fontWidth * renderScale);
    }

    @Override
    public int getHeight() {
        var font = Minecraft.getInstance().font;
        int fontHeight;
        if (wrapWidth > 0) {
            // Multi-line: ask the vanilla font splitter how many wrapped
            // lines this text produces at the current wrapWidth, then
            // multiply by lineHeight. font.split() is the same call vanilla
            // uses for chat / tooltips / book pages, so wrap semantics
            // match player expectations.
            Component text = textSupplier.get();
            if (text == null) return 0;
            List<FormattedCharSequence> lines = font.split(text, wrapWidth);
            int lineCount = Math.max(1, lines.size());
            fontHeight = lineCount * font.lineHeight;
        } else {
            fontHeight = font.lineHeight;
        }
        // Scale applied at the screen-pixel boundary (1.0 = identity).
        return (int) (fontHeight * renderScale);
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

    // ── Scale / backdrop / onRender (folded from MKHudText) ─────────────

    /**
     * Sets a uniform render scale (folded from the former MKHudText). The text is
     * drawn through a GUI pose-matrix scale, and {@link #getWidth()}/
     * {@link #getHeight()}/{@link #naturalWidth()} report the SCALED extent so the
     * owning Panel reserves the right space. {@code 1.0f} (the default) skips the
     * matrix entirely — the unscaled path is unchanged. Composes with wrap:
     * {@code wrapWidth} stays a font-space number, the scale is applied only at the
     * screen-pixel boundary.
     *
     * <p>Must be {@code > 0}. A non-positive value is floored to a tiny positive
     * minimum rather than allowed to produce a degenerate (zero / mirrored) render
     * matrix — hardening the gap the former MKHudText left open.
     *
     * @return this label, for chaining
     */
    public TextLabel scale(float scale) {
        this.renderScale = Math.max(0.01f, scale);
        return this;
    }

    /**
     * Toggles a translucent dark backdrop plate behind the text (folded from
     * MKHudText — HUD readability over the world). Sized to the text bounds,
     * multi-line when wrapped. Default off.
     *
     * @return this label, for chaining
     */
    public TextLabel backdrop(boolean backdrop) {
        this.backdrop = backdrop;
        return this;
    }

    /** Enables the backdrop plate. See {@link #backdrop(boolean)}. */
    public TextLabel backdrop() {
        return backdrop(true);
    }

    /**
     * Sets an optional side-effect fired at the top of each {@link #render}
     * (folded from MKHudText — HUD render-tick counters / animation hooks). Pass
     * {@code null} to clear it.
     *
     * @return this label, for chaining
     */
    public TextLabel onRender(@Nullable Runnable onRender) {
        this.onRender = onRender;
        return this;
    }

    /**
     * Extra vertical pixels this label occupies BEYOND a single line because it
     * wrapped — i.e. {@code (lineCount - 1) × lineHeight}, or {@code 0} when not
     * wrapped. The owning {@link Panel} uses this to reflow the elements below a
     * wrapped label downward by exactly this amount, so a label that grows from
     * one line to two doesn't paint over the next element (fixed-childY layouts
     * stay overlap-free under auto-wrap).
     */
    public int extraWrapHeight() {
        if (wrapWidth <= 0) return 0;
        // Single-line height in the SAME scaled screen-pixel space as getHeight(),
        // so the reflow delta is correct under a render scale.
        int singleLine = (int) (Minecraft.getInstance().font.lineHeight * renderScale);
        return Math.max(0, getHeight() - singleLine);
    }

    // ── Reactive sizing (Verification-4) ───────────────────────────────

    /**
     * Natural width = the text's single-line rendered width. The owning Panel
     * maxes this across elements to find its hug-width; a wider element (or the
     * screen-edge ceiling) then drives whether this label actually wraps.
     */
    @Override
    public int naturalWidth() {
        Component text = textSupplier.get();
        // Scaled, so the Panel's hug-width (which maxes naturalWidth across
        // elements) is in the same screen-pixel space as getWidth().
        return text == null ? 0 : (int) (Minecraft.getInstance().font.width(text) * renderScale);
    }

    /**
     * Wrap to the panel-assigned budget. Wrapping is engaged ONLY when the
     * budget is narrower than the text's natural single-line width — at a
     * roomy budget the wrap is cleared so the label reports its intrinsic
     * width (and a later wider pass un-wraps it, reversibly).
     *
     * <p>The incoming {@code budget} is scaled screen-pixel space (it is compared
     * against the scaled {@link #naturalWidth()}), but {@code wrapWidth} must be a
     * FONT-space number ({@code font.split} works on the font's native grid), so
     * the scale is divided back out before storing. The scale is then re-applied
     * at {@link #getWidth()}/{@link #getHeight()}/{@link #render}.
     */
    @Override
    public void layoutWithin(int budget) {
        int natural = naturalWidth();
        if (budget < natural) {
            int fontBudget = (renderScale > 0f) ? (int) (budget / renderScale) : budget;
            setWrapWidth(fontBudget);
        } else {
            setWrapWidth(0);
        }
    }

    /** Extra height beyond a single line once wrapped — drives panel reflow. */
    @Override
    public int extraLayoutHeight() {
        return extraWrapHeight();
    }

    // ── Rendering ──────────────────────────────────────────────────────

    @Override
    public void render(RenderContext ctx) {
        // onRender side-effect fires first (folded from MKHudText) — even if the
        // text resolves null this frame, the hook still ran.
        if (onRender != null) onRender.run();
        Component text = textSupplier.get();
        if (text == null) return;
        var font = Minecraft.getInstance().font;
        var graphics = ctx.graphics();
        int x = ctx.originX() + childX;
        int y = ctx.originY() + childY;

        // Render scale via the GUI pose matrix (folded from MKHudText). Only push
        // when actually scaling, so the default 1.0 path is identical to pre-fold:
        // translate to the draw origin, scale, then draw at (0,0) in font space.
        boolean scaled = renderScale != 1.0f;
        if (scaled) {
            graphics.pose().pushMatrix();
            graphics.pose().translate((float) x, (float) y);
            graphics.pose().scale(renderScale, renderScale);
            x = 0;
            y = 0;
        }

        // Backdrop plate behind the (possibly wrapped) text — drawn in font space
        // inside the scaled matrix so it scales with the glyphs.
        if (backdrop) {
            int tw = (wrapWidth > 0) ? wrapWidth : font.width(text);
            int th = (wrapWidth > 0)
                    ? Math.max(1, font.split(text, wrapWidth).size()) * font.lineHeight
                    : font.lineHeight;
            graphics.fill(x - 1, y - 1, x + tw + 1, y + th + 1, 0xBB000000);
        }

        if (wrapWidth > 0) {
            // Multi-line render: split into FormattedCharSequence lines and
            // draw each at successive lineHeight offsets. drawString accepts
            // FormattedCharSequence directly (same path tooltips + book pages
            // use), so wrap rendering rides on the existing vanilla pipeline.
            List<FormattedCharSequence> lines = font.split(text, wrapWidth);
            int lineY = y;
            for (FormattedCharSequence line : lines) {
                graphics.text(font, line, x, lineY, color, shadow);
                lineY += font.lineHeight;
            }
        } else {
            // Legacy single-line path.
            graphics.text(font, text, x, y, color, shadow);
        }

        if (scaled) {
            graphics.pose().popMatrix();
        }

        // Tooltip — queues if cursor is over the label bounds. The hit-test reads
        // the unscaled screen position (getChildX/Y + getWidth/Height), so it's
        // evaluated after the matrix is popped.
        Supplier<Component> tooltipSupplier = getTooltipSupplier();
        if (tooltipSupplier != null && ctx.hasMouseInput() && isHovered(ctx)) {
            Component ttText = tooltipSupplier.get();
            if (ttText != null) {
                MKTooltip.queue(ctx.graphics(), ttText, ctx.mouseX(), ctx.mouseY());
            }
        }
    }

    // ── Chainable configuration ────────────────────────────────────────
    //
    // showWhen + tooltip + at return TextLabel for free via the SELF self-type.

    // mouseClicked inherits the default no-op from PanelElement.
    // isVisible + setVisible inherit from AbstractPanelElement (Phase 18r-2).
}
