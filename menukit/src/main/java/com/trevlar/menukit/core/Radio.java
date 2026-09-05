package com.trevlar.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A single-selection control. One of a set of Radios coordinated by a
 * shared {@link RadioGroup}. Clicking selects this Radio's value in the
 * group; Radios render their own checked state by comparing their value
 * against the group's current selection.
 *
 * <p>Auto-sizes from label content: total width is
 * {@code BOX_SIZE + LABEL_GAP + fontWidth(label)}; height is
 * {@link #BOX_SIZE}. Consumers do not pass width or height at construction.
 *
 * <p>Clicking anywhere within the element bounds (square OR label area)
 * selects this Radio's value in the group.
 *
 * <h3>Mutable state (via coordinator)</h3>
 *
 * Radio's checked state is derived from the {@link RadioGroup}'s currently
 * selected value. The group holds the mutable state; each Radio reads it
 * at render time. See {@link RadioGroup} for the architectural exception
 * and its scope.
 *
 * <h3>Dynamic-width limitation with supplier labels</h3>
 *
 * Auto-sizing elements with supplier-based variable content cannot
 * guarantee layout stability — if the supplier returns different-length
 * text each frame, the element's width changes per frame but panel layout
 * is not re-resolved per frame. Consumers needing stable layout should
 * use fixed-content variants or ensure the supplier returns same-width
 * content across all evaluations.
 *
 * @param <T> the value type of this Radio and its group (typically an enum)
 * @see RadioGroup The coordinator
 * @see Checkbox   Multi-select sibling
 */
public class Radio<T> extends AbstractPanelElement<Radio<T>> {

    @Override protected Radio<T> self() { return this; }

    /** Size of the radio square, in pixels. */
    public static final int BOX_SIZE = 10;

    /** Gap between the radio square and the label. */
    public static final int LABEL_GAP = 4;

    /** Default label color — vanilla inventory-label dark gray. */
    public static final int DEFAULT_LABEL_COLOR = 0xFF404040;

    /** Muted label color when disabled. */
    public static final int DISABLED_LABEL_COLOR = 0xFF808080;

    /** Size of the selection indicator inside the radio box. */
    public static final int INDICATOR_SIZE = 4;

    /** Color of the selection indicator (visible against INSET dark interior). */
    public static final int INDICATOR_COLOR = 0xFF606060;

    private final T value;
    private final Supplier<Component> labelSupplier;
    private final RadioGroup<T> group;
    private final @Nullable BooleanSupplier disabledWhen;

    // tooltipSupplier hoisted to AbstractPanelElement (Phase 18r-2).

    // ── Reactive label wrap ────────────────────────────────────────────
    // When labelWrapWidth > 0, the label renders multi-line — the text is
    // split via font.split(label, labelWrapWidth) and each
    // FormattedCharSequence is drawn at successive lineHeight offsets, with
    // the radio box top-aligned to the first line. getHeight() then grows to
    // lineCount × lineHeight (clamped to never shrink below BOX_SIZE). Set
    // reactively by the owning Panel via layoutWithin() during layout: it
    // engages ONLY when the label's natural single-line width exceeds the
    // label area the panel budgeted, and clears back to 0 (single-line) when
    // a later, roomier budget arrives — so wrapping is fully reversible.
    //
    // Mutable rather than final because the wrap budget is a panel-layout
    // decision the element can't know at construction; baking it into the
    // constructor would invert that dependency. Mirrors TextLabel.wrapWidth.
    private int labelWrapWidth = 0;

    // Render-frame state.
    private boolean hovered = false;

    // ── Constructors: fixed label ─────────────────────────────────────

    public Radio(int childX, int childY, T value,
                 Component label, RadioGroup<T> group) {
        this(childX, childY, value, wrap(label), group, null);
    }

    public Radio(int childX, int childY, T value,
                 Component label, RadioGroup<T> group,
                 @Nullable BooleanSupplier disabledWhen) {
        this(childX, childY, value, wrap(label), group, disabledWhen);
    }

    // ── Constructors: supplier label ──────────────────────────────────

    public Radio(int childX, int childY, T value,
                 Supplier<Component> label, RadioGroup<T> group) {
        this(childX, childY, value, label, group, null);
    }

    public Radio(int childX, int childY, T value,
                 Supplier<Component> label, RadioGroup<T> group,
                 @Nullable BooleanSupplier disabledWhen) {
        this.childX = childX;
        this.childY = childY;
        this.value = value;
        this.labelSupplier = label;
        this.group = group;
        this.disabledWhen = disabledWhen;
    }

    // ── M8 Layout Spec ─────────────────────────────────────────────────

    /**
     * Returns an {@link com.trevlar.menukit.core.layout.ElementSpec}
     * for a Radio with static label. Width inferred from font metrics +
     * box + gap.
     */
    public static <T> com.trevlar.menukit.core.layout.ElementSpec spec(
            T value, Component label, RadioGroup<T> group) {
        return spec(value, label, group, null);
    }

    /** Layout spec for static label with optional disabled-predicate. */
    public static <T> com.trevlar.menukit.core.layout.ElementSpec spec(
            T value, Component label, RadioGroup<T> group,
            @Nullable BooleanSupplier disabledWhen) {
        int labelWidth = Minecraft.getInstance().font.width(label);
        int w = BOX_SIZE + LABEL_GAP + labelWidth;
        return new com.trevlar.menukit.core.layout.ElementSpec() {
            @Override public int width()  { return w; }
            @Override public int height() { return BOX_SIZE; }
            @Override public PanelElement at(int x, int y) {
                return new Radio<>(x, y, value, label, group, disabledWhen);
            }
        };
    }

    /** Wraps a fixed label into a one-shot supplier, unifying the render path. */
    private static Supplier<Component> wrap(Component label) {
        return () -> label;
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
        if (labelWrapWidth > 0) {
            // Multi-line label: ask the vanilla font splitter how many wrapped
            // lines this label produces at the current budget, then multiply by
            // lineHeight. font.split() is the same call vanilla uses for chat /
            // tooltips / book pages, so wrap semantics match player expectations.
            // Math.max with BOX_SIZE guarantees we only ever GROW past the
            // authored box height, never shrink below it (a one-line wrap that
            // produces a sub-box height must still reserve the full box).
            Component label = labelSupplier.get();
            if (label != null) {
                var font = Minecraft.getInstance().font;
                List<FormattedCharSequence> lines = font.split(label, labelWrapWidth);
                int lineCount = Math.max(1, lines.size());
                return Math.max(BOX_SIZE, lineCount * font.lineHeight);
            }
        }
        return BOX_SIZE;
    }

    /** Interactive — handles clicks, so it claims (blocks vanilla behind) on a non-opaque panel. */
    @Override public boolean isInteractive() { return true; }

    // ── Queries ────────────────────────────────────────────────────────

    /** Returns this Radio's value (the identifier passed at construction). */
    public T getValue() { return value; }

    /** Returns whether this Radio is currently the group's selected value. */
    public boolean isSelected() {
        return Objects.equals(group.getSelected(), value);
    }

    /** Returns whether this Radio is currently disabled. */
    public boolean isDisabled() {
        return disabledWhen != null && disabledWhen.getAsBoolean();
    }

    /** Returns whether the mouse is over this Radio (updated each frame). */
    public boolean isHovered() { return hovered; }

    // ── Chainable configuration ────────────────────────────────────────
    //
    // showWhen + tooltip + at return Radio<T> for free via the SELF self-type.

    // ── Reactive label wrap ────────────────────────────────────────────
    //
    // naturalWidth() is left at the PanelElement default (full single-line
    // getWidth()): the un-wrapped intent the panel maxes across siblings to
    // pick its content width. Wrapping then flows DOWN from that decision via
    // layoutWithin(), mirroring TextLabel.

    /**
     * Wrap the label to the panel-assigned budget. The box + gap are fixed
     * furniture, so only the label area can flex: we subtract them off the
     * budget and engage wrapping ONLY when that remaining label area is
     * narrower than the label's natural single-line width. At a roomy budget
     * the wrap is cleared (set to 0) so the Radio reports its intrinsic
     * single-line size again — a later wider pass un-wraps it, reversibly
     * (this is the same reversible engage/clear TextLabel uses).
     */
    @Override
    public void layoutWithin(int budget) {
        // Pixels left for the label once the box and gap are accounted for.
        int labelBudget = budget - BOX_SIZE - LABEL_GAP;
        Component label = labelSupplier.get();
        int natural = label != null ? Minecraft.getInstance().font.width(label) : 0;
        // Engage only when the label genuinely overflows its area; otherwise
        // clear to single-line. Guard labelBudget >= 1 so a degenerate (≤0)
        // budget never feeds font.split a non-positive width.
        labelWrapWidth = (labelBudget >= 1 && labelBudget < natural) ? labelBudget : 0;
    }

    /**
     * Extra vertical pixels this Radio occupies BEYOND its authored box height
     * because the label wrapped — i.e. {@code wrappedHeight - BOX_SIZE}, or
     * {@code 0} when not wrapped. The owning {@link Panel} reflows the elements
     * below this one downward by exactly this amount, so a label that grows to
     * multiple lines pushes — never paints over — what sits beneath it.
     */
    @Override
    public int extraLayoutHeight() {
        return Math.max(0, getHeight() - BOX_SIZE);
    }

    // ── Rendering ──────────────────────────────────────────────────────

    @Override
    public void render(RenderContext ctx) {
        int sx = ctx.originX() + childX;
        int sy = ctx.originY() + childY;

        hovered = isHovered(ctx);
        boolean disabled = isDisabled();
        var graphics = ctx.graphics();

        // Radio square background
        PanelStyle bg = disabled ? PanelStyle.DARK : PanelStyle.INSET;
        PanelRendering.renderPanel(graphics, sx, sy, BOX_SIZE, BOX_SIZE, bg);

        // Hover highlight on the square
        if (!disabled && hovered) {
            graphics.fill(sx + 1, sy + 1, sx + BOX_SIZE - 1, sy + BOX_SIZE - 1,
                    0x30FFFFFF);
        }

        // Selection indicator — 4×4 filled square, centered
        if (isSelected()) {
            int indX = sx + (BOX_SIZE - INDICATOR_SIZE) / 2;
            int indY = sy + (BOX_SIZE - INDICATOR_SIZE) / 2;
            graphics.fill(indX, indY,
                    indX + INDICATOR_SIZE, indY + INDICATOR_SIZE,
                    INDICATOR_COLOR);
        }

        // Label text
        Component label = labelSupplier.get();
        if (label != null) {
            var font = Minecraft.getInstance().font;
            int textX = sx + BOX_SIZE + LABEL_GAP;
            int color = disabled ? DISABLED_LABEL_COLOR : DEFAULT_LABEL_COLOR;
            if (labelWrapWidth > 0) {
                // Wrapped: split into FormattedCharSequence lines and draw each
                // at successive lineHeight offsets. The box (drawn above at sy)
                // is already top-aligned, so line 1 starts at sy too — no
                // vertical-centering offset here, because a centered single
                // line would put the multi-line block's TOP above the box.
                // drawString accepts FormattedCharSequence directly (same path
                // tooltips + book pages use), so wrap rendering rides the
                // existing vanilla pipeline.
                List<FormattedCharSequence> lines = font.split(label, labelWrapWidth);
                int lineY = sy;
                for (FormattedCharSequence line : lines) {
                    graphics.text(font, line, textX, lineY, color, false);
                    lineY += font.lineHeight;
                }
            } else {
                // Single-line: vertically centered with the box (legacy path).
                int textY = sy + (BOX_SIZE - font.lineHeight) / 2 + 1;
                graphics.text(font, label, textX, textY, color, false);
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

        // Set this Radio's value as the group's selection.
        // The group fires onSelect if the value actually changed.
        group.setSelected(value);
        return true;
    }
}
