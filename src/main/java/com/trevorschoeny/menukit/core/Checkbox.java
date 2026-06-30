package com.trevorschoeny.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A two-state boolean control rendered as a small square with a check-mark
 * indicator and an adjacent label. The settings-ready convention for boolean
 * interactions — pre-composed with a label, conventional visual.
 *
 * <p>Auto-sizes from label content: total width is
 * {@code BOX_SIZE + LABEL_GAP + fontWidth(label)}; total height is
 * {@link #BOX_SIZE}. Consumers do not pass width or height at construction;
 * the element derives them from the label.
 *
 * <p>Clicking anywhere within the element bounds (square OR label area)
 * toggles the state — matches HTML/native checkbox convention.
 *
 * <p>Rendering uses vanilla's {@code icon/checkmark} sprite for the checked
 * indicator and MenuKit's {@link PanelStyle#INSET} for the box background.
 * Resource packs that re-texture vanilla GUI sprites adapt MenuKit
 * checkboxes automatically.
 *
 * <h3>Mutable-state exception</h3>
 *
 * Checkbox owns a mutable boolean, a narrow exception to MenuKit's
 * declared-structure discipline. See {@link Toggle} for the full architectural
 * justification; the same rationale applies here.
 *
 * <h3>Dynamic-width limitation with supplier labels</h3>
 *
 * The supplier-based constructor accepts a {@code Supplier<Component>} for
 * dynamic label content. Auto-sizing elements with supplier-based variable
 * content cannot guarantee layout stability — if the supplier returns
 * different-length text each frame, the element's width changes per frame
 * but panel layout is not re-resolved per frame. Consumers needing stable
 * layout should use fixed-content variants or ensure the supplier returns
 * same-width content across all evaluations (e.g., "Mode: AUTO" vs
 * "Mode: MANUAL" where both strings render to similar widths).
 *
 * <h3>Scope</h3>
 * <ul>
 *   <li>Boolean only — no tri-state/indeterminate.</li>
 *   <li>Fixed {@code 10×10} checkbox square, sized to the {@code 9×8}
 *   vanilla check-mark sprite. Larger checkboxes require custom
 *   {@link PanelElement} implementation.</li>
 *   <li>No animation on state change.</li>
 *   <li>Label text rendered in {@link #DEFAULT_LABEL_COLOR} without shadow.</li>
 * </ul>
 *
 * @see PanelElement The interface this implements
 * @see Toggle       The general boolean primitive
 */
public class Checkbox extends AbstractPanelElement<Checkbox> {

    @Override protected Checkbox self() { return this; }

    /** Size of the checkbox square, in pixels. */
    public static final int BOX_SIZE = 10;

    /** Horizontal gap between the checkbox square and the label text. */
    public static final int LABEL_GAP = 4;

    /** Vanilla check-mark sprite used for the checked state (9×8 pixels). */
    public static final Identifier CHECKMARK_SPRITE =
            Identifier.withDefaultNamespace("icon/checkmark");

    /** Vanilla check-mark sprite width. */
    private static final int CHECKMARK_WIDTH = 9;

    /** Vanilla check-mark sprite height. */
    private static final int CHECKMARK_HEIGHT = 8;

    /** Default label color — vanilla inventory-label dark gray. */
    public static final int DEFAULT_LABEL_COLOR = 0xFF404040;

    /** Muted label color when disabled. */
    public static final int DISABLED_LABEL_COLOR = 0xFF808080;

    private final Supplier<Component> labelSupplier;
    private final Consumer<Boolean> onToggle;
    private final @Nullable BooleanSupplier disabledWhen;

    // Mutable state — same narrow exception as Toggle (see Toggle javadoc).
    private boolean state;

    // tooltipSupplier hoisted to AbstractPanelElement (Phase 18r-2).

    // Render-frame state.
    private boolean hovered = false;

    // ── Reactive label wrap ────────────────────────────────────────────
    // When wrapWidth > 0, the LABEL renders multi-line — the label text is
    // split via font.split(label, wrapWidth) and each FormattedCharSequence
    // is drawn beside the box at successive lineHeight offsets. getHeight()
    // then grows to lineCount × lineHeight (floored at BOX_SIZE so a wrapped
    // checkbox never shrinks below its authored square). Mirrors TextLabel's
    // auto-wrap mechanism; the value is the horizontal pixel budget for the
    // LABEL AREA (already net of the box + gap), not the whole element.
    //
    // Mutable, set by the owning Panel via layoutWithin() once it knows its
    // resolved content width. Zero (default) = single-line legacy behavior,
    // and the wrap is REVERSIBLE — a later wider budget clears it back to 0
    // and restores the single-line, box-centered label.
    private int wrapWidth = 0;

    // ── Constructors: fixed label ─────────────────────────────────────

    public Checkbox(int childX, int childY, boolean initialState,
                    Component label, Consumer<Boolean> onToggle) {
        this(childX, childY, initialState, wrap(label), onToggle, null);
    }

    public Checkbox(int childX, int childY, boolean initialState,
                    Component label, Consumer<Boolean> onToggle,
                    @Nullable BooleanSupplier disabledWhen) {
        this(childX, childY, initialState, wrap(label), onToggle, disabledWhen);
    }

    // ── Constructors: supplier label ──────────────────────────────────

    public Checkbox(int childX, int childY, boolean initialState,
                    Supplier<Component> label, Consumer<Boolean> onToggle) {
        this(childX, childY, initialState, label, onToggle, null);
    }

    public Checkbox(int childX, int childY, boolean initialState,
                    Supplier<Component> label, Consumer<Boolean> onToggle,
                    @Nullable BooleanSupplier disabledWhen) {
        this.childX = childX;
        this.childY = childY;
        this.state = initialState;
        this.labelSupplier = label;
        this.onToggle = onToggle;
        this.disabledWhen = disabledWhen;
    }

    /** Wraps a fixed label into a one-shot supplier, unifying the render path. */
    private static Supplier<Component> wrap(Component label) {
        return () -> label;
    }

    // ── M8 Layout Spec ─────────────────────────────────────────────────

    /**
     * Returns an {@link com.trevorschoeny.menukit.core.layout.ElementSpec}
     * for static label. Width inferred from font metrics + box + gap;
     * height is {@link #BOX_SIZE}.
     */
    public static com.trevorschoeny.menukit.core.layout.ElementSpec spec(
            boolean initialState, Component label, Consumer<Boolean> onToggle) {
        return spec(initialState, label, onToggle, null);
    }

    /** Layout spec for static label with optional disabled-predicate. */
    public static com.trevorschoeny.menukit.core.layout.ElementSpec spec(
            boolean initialState, Component label, Consumer<Boolean> onToggle,
            @Nullable BooleanSupplier disabledWhen) {
        int labelWidth = Minecraft.getInstance().font.width(label);
        int w = BOX_SIZE + LABEL_GAP + labelWidth;
        return new com.trevorschoeny.menukit.core.layout.ElementSpec() {
            @Override public int width()  { return w; }
            @Override public int height() { return BOX_SIZE; }
            @Override public PanelElement at(int x, int y) {
                return new Checkbox(x, y, initialState, label, onToggle, disabledWhen);
            }
        };
    }

    // ── PanelElement Implementation ────────────────────────────────────


    @Override
    public int getWidth() {
        Component label = labelSupplier.get();
        int labelWidth = label != null ? Minecraft.getInstance().font.width(label) : 0;
        return BOX_SIZE + LABEL_GAP + labelWidth;
    }

    @Override
    public int getHeight() {
        if (wrapWidth > 0) {
            // Multi-line label: ask the vanilla font splitter how many wrapped
            // lines the label produces at the current wrapWidth, then multiply
            // by lineHeight. font.split() is the same call vanilla uses for
            // chat / tooltips / book pages, so wrap semantics match player
            // expectations. Floor at BOX_SIZE (Math.max) so the element NEVER
            // shrinks below its authored square — wrapping only ever grows it.
            Component label = labelSupplier.get();
            if (label != null) {
                var font = Minecraft.getInstance().font;
                List<FormattedCharSequence> lines = font.split(label, wrapWidth);
                int lineCount = Math.max(1, lines.size());
                return Math.max(BOX_SIZE, lineCount * font.lineHeight);
            }
        }
        // Single-line (or no label): the authored square height.
        return BOX_SIZE;
    }

    /** Interactive — handles clicks, so it claims (blocks vanilla behind) on a non-opaque panel. */
    @Override public boolean isInteractive() { return true; }

    // ── Reactive label wrap (mirrors TextLabel) ─────────────────────────
    //
    // Width flows DOWN from the panel: the panel resolves one content width
    // and hands each element a horizontal budget via layoutWithin(). The
    // checkbox wraps its LABEL into the remaining space beside the box,
    // reversibly — a later wider budget un-wraps it. getWidth()/getHeight()
    // are the hit-test + hover bounds basis (see PanelElement#isHovered /
    // #hitTest), so the enlarged multi-line hit-rect comes for free.

    /**
     * Natural width = box + gap + the label's single-line rendered width —
     * what the checkbox wants when the panel has room. The owning Panel maxes
     * this across elements to find its hug-width; a wider element (or the
     * screen-edge ceiling) then drives whether this label actually wraps.
     * Identical to {@link #getWidth()} in non-wrap mode, but kept distinct so
     * a narrow pass can shrink presentation while natural intent stays put.
     */
    @Override
    public int naturalWidth() {
        Component label = labelSupplier.get();
        int labelWidth = label != null ? Minecraft.getInstance().font.width(label) : 0;
        return BOX_SIZE + LABEL_GAP + labelWidth;
    }

    /**
     * Wrap the LABEL to the panel-assigned budget. The budget covers the whole
     * element, so the label's own area is {@code budget - BOX_SIZE - LABEL_GAP};
     * we engage wrap ONLY when the label's natural single-line width exceeds
     * that area, else clear it back to 0. Reversible: a later wider budget
     * restores the single-line, box-centered label (same engage-only-when-
     * natural-exceeds-available rule TextLabel uses).
     */
    @Override
    public void layoutWithin(int budget) {
        Component label = labelSupplier.get();
        if (label == null) { wrapWidth = 0; return; }
        var font = Minecraft.getInstance().font;
        // The horizontal pixels the label itself may occupy, net of the box
        // and the box→label gap. Floor at 1 so font.split never gets a
        // non-positive width even at an absurdly tight budget.
        int labelArea = Math.max(1, budget - BOX_SIZE - LABEL_GAP);
        // Engage wrap only when the label can't fit on one line in that area.
        wrapWidth = font.width(label) > labelArea ? labelArea : 0;
    }

    /**
     * Extra vertical pixels this checkbox occupies BEYOND its authored square
     * because the label wrapped — i.e. {@code getHeight() - BOX_SIZE}, or 0
     * when not wrapped. The owning {@link Panel} reflows the elements below a
     * wrapped checkbox downward by exactly this amount, so a label that grows
     * from one line to two pushes — never paints over — what's beneath it.
     */
    @Override
    public int extraLayoutHeight() {
        return Math.max(0, getHeight() - BOX_SIZE);
    }

    // ── State ──────────────────────────────────────────────────────────

    /** Returns the current checked state. */
    public boolean isChecked() { return state; }

    /**
     * Sets the checked state programmatically. Fires {@code onToggle} with
     * the new state if it differs from the current state; no-op otherwise.
     */
    public void setChecked(boolean checked) {
        if (this.state == checked) return;
        this.state = checked;
        onToggle.accept(checked);
    }

    /** Returns whether the checkbox is currently disabled. */
    public boolean isDisabled() {
        return disabledWhen != null && disabledWhen.getAsBoolean();
    }

    /** Returns whether the mouse is currently over this element (updated each frame). */
    public boolean isHovered() { return hovered; }

    // ── Chainable configuration ────────────────────────────────────────
    //
    // showWhen + tooltip + at return Checkbox for free via the SELF self-type.

    // ── Rendering ──────────────────────────────────────────────────────

    @Override
    public void render(RenderContext ctx) {
        int sx = ctx.originX() + childX;
        int sy = ctx.originY() + childY;

        // Update hover state from current mouse position. Whole element
        // bounds (square + label) counts for hover.
        hovered = isHovered(ctx);

        boolean disabled = isDisabled();
        var graphics = ctx.graphics();

        // Checkbox square background
        PanelStyle bg = disabled ? PanelStyle.DARK : PanelStyle.INSET;
        PanelRendering.renderPanel(graphics, sx, sy, BOX_SIZE, BOX_SIZE, bg);

        // Hover highlight on the square
        if (!disabled && hovered) {
            graphics.fill(sx + 1, sy + 1, sx + BOX_SIZE - 1, sy + BOX_SIZE - 1,
                    0x30FFFFFF);
        }

        // Check-mark sprite when checked
        if (state) {
            // Center the 9×8 sprite in the 10×10 box: 0.5px horizontal margin
            // (flush-left with 1px right), 1px top margin.
            int markX = sx;
            int markY = sy + (BOX_SIZE - CHECKMARK_HEIGHT) / 2;
            graphics.blitSprite(
                    RenderPipelines.GUI_TEXTURED,
                    CHECKMARK_SPRITE,
                    markX, markY, CHECKMARK_WIDTH, CHECKMARK_HEIGHT);
        }

        // Label text, drawn beside the box.
        Component label = labelSupplier.get();
        if (label != null) {
            var font = Minecraft.getInstance().font;
            int textX = sx + BOX_SIZE + LABEL_GAP;
            int color = disabled ? DISABLED_LABEL_COLOR : DEFAULT_LABEL_COLOR;

            if (wrapWidth > 0) {
                // Multi-line: split the label into FormattedCharSequence lines
                // and draw each at successive lineHeight offsets, starting at
                // the box TOP. Once multi-line we TOP-align (box beside line 1)
                // rather than vertically center against the square — centering a
                // tall block against a 10px box would float the text off the box.
                // drawString accepts FormattedCharSequence directly (the same
                // path tooltips + book pages use), so wrap rendering rides on the
                // existing vanilla pipeline.
                List<FormattedCharSequence> lines = font.split(label, wrapWidth);
                int lineY = sy;
                for (FormattedCharSequence line : lines) {
                    graphics.drawString(font, line, textX, lineY, color, false);
                    lineY += font.lineHeight;
                }
            } else {
                // Single-line legacy path: vertically center the label with the box.
                int textY = sy + (BOX_SIZE - font.lineHeight) / 2 + 1;
                graphics.drawString(font, label, textX, textY, color, false);
            }
        }

        // Hover-triggered tooltip — deferred to end-of-frame.
        Supplier<Component> tooltipSupplier = getTooltipSupplier();
        if (hovered && tooltipSupplier != null && ctx.hasMouseInput()) {
            Component ttText = tooltipSupplier.get();
            if (ttText != null) {
                MKTooltip.queue(graphics, ttText,
                        ctx.mouseX(), ctx.mouseY());
            }
        }
    }

    // ── Click Handling ─────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (isDisabled()) return false;
        if (!hovered) return false;

        // Flip state and fire callback.
        state = !state;
        onToggle.accept(state);
        return true;
    }

    // Phase 9 note: Checkbox.linked will be a subclass that overrides render()
    // and mouseClicked() to read from / write to a consumer-supplied
    // BooleanSupplier instead of the internal `state` field. Class and
    // methods are deliberately non-final. Refactoring into protected helpers
    // happens in Phase 9 when the linked variant is actually built.
}
