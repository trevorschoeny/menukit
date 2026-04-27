package com.trevorschoeny.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

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
public class Checkbox implements PanelElement {

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

    private final int childX;
    private final int childY;
    private final Supplier<Component> labelSupplier;
    private final Consumer<Boolean> onToggle;
    private final @Nullable BooleanSupplier disabledWhen;

    // Mutable state — same narrow exception as Toggle (see Toggle javadoc).
    private boolean state;

    // Optional hover-triggered tooltip (post-construction configuration).
    private @Nullable Supplier<Component> tooltipSupplier;

    // Render-frame state.
    private boolean hovered = false;

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

    // ── Tooltip (optional post-construction configuration) ─────────────

    /** Attaches a hover-triggered tooltip with fixed text. Returns this for chaining. */
    public Checkbox tooltip(Component text) {
        return tooltip(() -> text);
    }

    /** Attaches a hover-triggered tooltip with supplier-driven text. Returns this for chaining. */
    public Checkbox tooltip(Supplier<Component> supplier) {
        this.tooltipSupplier = supplier;
        return this;
    }

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
