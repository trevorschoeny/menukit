package com.trevorschoeny.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

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
public class Radio<T> implements PanelElement {

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

    private final int childX;
    private final int childY;
    private final T value;
    private final Supplier<Component> labelSupplier;
    private final RadioGroup<T> group;
    private final @Nullable BooleanSupplier disabledWhen;

    // Optional hover-triggered tooltip (post-construction configuration).
    private @Nullable Supplier<Component> tooltipSupplier;

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
     * Returns an {@link com.trevorschoeny.menukit.core.layout.ElementSpec}
     * for a Radio with static label. Width inferred from font metrics +
     * box + gap.
     */
    public static <T> com.trevorschoeny.menukit.core.layout.ElementSpec spec(
            T value, Component label, RadioGroup<T> group) {
        return spec(value, label, group, null);
    }

    /** Layout spec for static label with optional disabled-predicate. */
    public static <T> com.trevorschoeny.menukit.core.layout.ElementSpec spec(
            T value, Component label, RadioGroup<T> group,
            @Nullable BooleanSupplier disabledWhen) {
        int labelWidth = Minecraft.getInstance().font.width(label);
        int w = BOX_SIZE + LABEL_GAP + labelWidth;
        return new com.trevorschoeny.menukit.core.layout.ElementSpec() {
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

    @Override public int getChildX() { return childX; }
    @Override public int getChildY() { return childY; }

    @Override
    public int getWidth() {
        Component label = labelSupplier.get();
        int labelWidth = label != null ? Minecraft.getInstance().font.width(label) : 0;
        return BOX_SIZE + LABEL_GAP + labelWidth;
    }

    @Override
    public int getHeight() {
        return BOX_SIZE;
    }

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

    // ── Tooltip (optional post-construction configuration) ─────────────

    /** Attaches a hover-triggered tooltip with fixed text. Returns this for chaining. */
    public Radio<T> tooltip(Component text) {
        return tooltip(() -> text);
    }

    /** Attaches a hover-triggered tooltip with supplier-driven text. Returns this for chaining. */
    public Radio<T> tooltip(Supplier<Component> supplier) {
        this.tooltipSupplier = supplier;
        return this;
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

        // Label text, vertically centered with the box
        Component label = labelSupplier.get();
        if (label != null) {
            var font = Minecraft.getInstance().font;
            int textX = sx + BOX_SIZE + LABEL_GAP;
            int textY = sy + (BOX_SIZE - font.lineHeight) / 2 + 1;
            int color = disabled ? DISABLED_LABEL_COLOR : DEFAULT_LABEL_COLOR;
            graphics.drawString(font, label, textX, textY, color, false);
        }

        // Hover-triggered tooltip — deferred to end-of-frame.
        if (hovered && tooltipSupplier != null && ctx.hasMouseInput()) {
            Component ttText = tooltipSupplier.get();
            if (ttText != null) {
                graphics.setTooltipForNextFrame(
                        Minecraft.getInstance().font, ttText,
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
