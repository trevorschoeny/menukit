package com.trevlar.menukit.api.element;

import com.trevlar.menukit.core.MKRenderPipelines;
import com.trevlar.menukit.core.PanelRendering;
import com.trevlar.menukit.api.panel.PanelStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * A two-state on/off control: MenuKit's general boolean primitive, a lens onto
 * the consumer's boolean and only a lens (§0026, §0066).
 *
 * <pre>{@code
 * Toggle.builder()
 *         .state(config::autoSort, config::setAutoSort)
 *         .label(Component.literal("Auto sort"))
 *         .build();
 * }</pre>
 *
 * <h3>State is the consumer's</h3>
 *
 * {@code state(get, set)} is required. The toggle reads {@code get} every frame
 * to draw itself and hands the flipped value to {@code set} on a click; it stores
 * nothing. If {@code set} does not change what {@code get} returns (a setting
 * that refuses, an exception swallowed), the next frame shows the unchanged
 * value: what is displayed is always what the consumer reports.
 *
 * <h3>Faces</h3>
 * <ul>
 *   <li><b>Switch</b> (default): raised when off, inset when on, dark when
 *       disabled, a highlight on hover. Needs a {@code size}.</li>
 *   <li><b>Labelled</b> ({@code label(...)}): the same bar with its label on it,
 *       as wide as the label needs; the label wraps and the bar grows when the
 *       panel is narrower.</li>
 *   <li><b>Sprite</b> ({@code sprite(...)}): the consumer's sprite as-is when
 *       off, with its lightness inverted when on, so one texture shows both
 *       states.</li>
 * </ul>
 * For the conventional box-and-check-mark, use {@link Checkbox}.
 *
 * <h3>Input</h3>
 *
 * Left click flips it, with vanilla's click sound; other buttons reach
 * {@code onSecondaryClick} when set, and never flip it. Tab focuses it, Enter or
 * Space flips it, and the narrator reads its label and on/off state (the shared
 * {@link ElementWidget}).
 */
public class Toggle extends AbstractPanelElement {

    private final BooleanSupplier stateGet;
    private final Consumer<Boolean> stateSet;
    private final @Nullable Supplier<Component> label;
    private final @Nullable Consumer<Click> onSecondaryClick;
    private final @Nullable IntSupplier tint;
    // The vanilla stand-in for sound, focus and narration (ElementWidget), made at
    // the first screen attach, not with the element: a Containers menu builds its
    // panels on the dedicated server too, where no screen class exists.
    private @Nullable ElementWidget widget;

    // The labelled bar's layout: the width cap and its label's wrap width (the one
    // wrap helper), recomputed every layout pass, so both are reversible. A bare or
    // sprite switch never touches them.
    private int widthCap = Integer.MAX_VALUE;
    private int wrapWidth = 0;

    protected Toggle(Builder b) {
        super(b);
        this.stateGet = b.stateGet;
        this.stateSet = b.stateSet;
        this.label = b.label;
        this.onSecondaryClick = b.onSecondaryClick;
        this.tint = b.tint;
    }

    public static Builder builder() {
        return new Builder();
    }

    // ── Size ───────────────────────────────────────────────────────────

    private @Nullable Component currentLabel() {
        return label == null ? null : label.get();
    }

    /** The labelled bar's natural width: its label plus padding, at least the authored width. */
    @Override
    public int naturalWidth() {
        Component text = currentLabel();
        if (text == null) return authoredWidth;
        return Math.max(authoredWidth, Minecraft.getInstance().font.width(text) + 2 * ElementConstants.LABEL_PAD);
    }

    @Override
    public int getWidth() {
        return currentLabel() == null ? authoredWidth : Math.min(naturalWidth(), widthCap);
    }

    @Override
    public int getHeight() {
        Component text = currentLabel();
        if (text == null || wrapWidth <= 0) return height;
        int lines = Text.lineCount(text, wrapWidth);
        return Math.max(height, lines * Minecraft.getInstance().font.lineHeight + 2 * ElementConstants.LABEL_VPAD);
    }

    /** A labelled bar caps to the budget and wraps its label inside; a switch keeps its size. */
    @Override
    public void layoutWithin(int budget) {
        Component text = currentLabel();
        if (text == null) { wrapWidth = 0; return; }
        widthCap = budget;
        wrapWidth = Text.wrapWidth(text, Math.min(naturalWidth(), budget) - 2 * ElementConstants.LABEL_PAD);
    }

    @Override
    public int extraLayoutHeight() {
        return wrapWidth > 0 ? Math.max(0, getHeight() - height) : 0;
    }

    /** Column fill stretches a labelled bar; a switch or sprite would distort, so it keeps its size. */
    @Override
    public void fillWidth(int width) {
        if (label != null) authoredWidth = width;
    }

    @Override public boolean isInteractive() { return true; }

    // ── Rendering ──────────────────────────────────────────────────────

    @Override
    public final void render(RenderContext ctx) {
        int sx = ctx.originX() + childX;
        int sy = ctx.originY() + childY;
        boolean disabled = disabled(ctx);
        // Read the lens once per frame, so the frame is consistent with itself.
        boolean on = stateGet.getAsBoolean();
        boolean hovered = isHovered(ctx);
        Component text = currentLabel();
        if (widget != null) widget.track(sx, sy, getWidth(), getHeight(), hovered, !disabled, CommonComponents.optionNameValue(
                narrationName(text), CommonComponents.optionStatus(on)));

        renderBackground(ctx, sx, sy, on, disabled, hovered || (widget != null && widget.focused()));
        if (tint != null) {
            int argb = tint.getAsInt();
            if (argb != 0) ctx.graphics().fill(sx + 1, sy + 1, sx + getWidth() - 1, sy + getHeight() - 1, argb);
        }
        if (text != null) {
            int color = disabled ? ElementConstants.TEXT_DISABLED : ElementConstants.TEXT_LIGHT;
            if (wrapWidth > 0) {
                int lines = Text.lineCount(text, wrapWidth);
                Text.drawWrapped(ctx.graphics(), text, wrapWidth, sx,
                        Text.centeredBlockY(sy, sy + getHeight(), lines), getWidth(), color, true);
            } else {
                Text.renderCentered(ctx.graphics(), text, sx, sy, getWidth(), height, color, true);
            }
        }
        if (hovered) queueTooltip(ctx);
    }

    /** The narrator's name for this toggle: its label, else its tooltip. */
    private Component narrationName(@Nullable Component text) {
        if (text != null) return text;
        Supplier<Component> t = tooltipSupplier();
        Component tip = t != null ? t.get() : null;
        return tip != null ? tip : Component.empty();
    }

    /**
     * Paints the toggle for this frame: raised off, inset on, dark disabled, and
     * the hover highlight. Covers the grown height when a label wrapped.
     *
     * <p><b>Stable extension point.</b> {@code sx}/{@code sy} are the toggle's
     * screen-space top-left; this hook owns all state-dependent painting.
     */
    protected void renderBackground(RenderContext ctx, int sx, int sy,
                                    boolean on, boolean disabled, boolean hovered) {
        int w = getWidth(), h = getHeight();
        PanelStyle bg = disabled ? PanelStyle.DARK : on ? PanelStyle.INSET : PanelStyle.RAISED;
        PanelRendering.renderPanel(ctx.graphics(), sx, sy, w, h, bg);
        if (!disabled && hovered) {
            ctx.graphics().fill(sx + 1, sy + 1, sx + w - 1, sy + h - 1, ElementConstants.HOVER_OVERLAY);
        }
    }

    // ── Input ──────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(InputContext in, int button) {
        if (disabled(in)) return false;
        if (button != Click.LEFT) {
            if (onSecondaryClick == null) return false;
            onSecondaryClick.accept(Click.of(button));
            return true;
        }
        ElementWidget.playClickSound();
        flip();
        return true;
    }

    /** Hands the flipped value to the consumer; stores nothing. */
    private void flip() {
        stateSet.accept(!stateGet.getAsBoolean());
    }

    /** Enter or Space on the focused toggle. */
    private void press() {
        if (!ownDisabled()) flip();
    }

    @Override
    public void onAttach(net.minecraft.client.gui.screens.Screen screen) {
        if (widget == null) widget = new ElementWidget(this::press);
        widget.attach(screen);
    }

    @Override
    public void onDetach(net.minecraft.client.gui.screens.Screen screen) {
        if (widget != null) widget.detach(screen);
    }

    // ── Builder ────────────────────────────────────────────────────────

    public static class Builder extends AbstractPanelElement.Builder<Toggle, Builder> {
        private @Nullable BooleanSupplier stateGet;
        private @Nullable Consumer<Boolean> stateSet;
        private @Nullable Supplier<Component> label;
        private @Nullable Consumer<Click> onSecondaryClick;
        private @Nullable IntSupplier tint;
        private @Nullable Supplier<Identifier> sprite;

        protected Builder() {
            this.width = 0;
            this.height = Button.DEFAULT_HEIGHT;
        }

        @Override protected Builder self() { return this; }

        /**
         * Required: the lens. {@code get} is read every frame; {@code set} receives
         * the new value when the player flips it. The toggle stores nothing.
         */
        public Builder state(BooleanSupplier get, Consumer<Boolean> set) {
            this.stateGet = Objects.requireNonNull(get, "get");
            this.stateSet = Objects.requireNonNull(set, "set");
            return this;
        }

        /**
         * Size in pixels. A switch or sprite needs one; a labelled toggle grows to
         * fit its label and treats this width as its minimum. Default height 20.
         */
        @Override
        public Builder size(int width, int height) {
            return super.size(width, height);
        }

        /** A label on the bar: the toggle becomes a labelled bar, sized to it. */
        public Builder label(Component label) {
            Objects.requireNonNull(label, "label");
            return label(() -> label);
        }

        /** A label read every frame ("On" / "Off", say). */
        public Builder label(Supplier<Component> label) {
            this.label = Objects.requireNonNull(label, "label");
            return this;
        }

        /** A sprite face: the sprite as-is when off, lightness-inverted when on. */
        public Builder sprite(Identifier sprite) {
            Objects.requireNonNull(sprite, "sprite");
            return sprite(() -> sprite);
        }

        /** A sprite face read every frame (different art per state, say). */
        public Builder sprite(Supplier<Identifier> sprite) {
            this.sprite = Objects.requireNonNull(sprite, "sprite");
            return this;
        }

        /** Handles every non-left mouse button; those clicks never flip the toggle. See {@link Button.Builder#onSecondaryClick}. */
        public Builder onSecondaryClick(Consumer<Click> handler) {
            this.onSecondaryClick = Objects.requireNonNull(handler, "handler");
            return this;
        }

        /** An ARGB fill over the face, read every frame; 0 for none. See {@link Button.Builder#tint}. */
        public Builder tint(IntSupplier tint) {
            this.tint = Objects.requireNonNull(tint, "tint");
            return this;
        }

        @Override
        public Toggle build() {
            require(stateGet != null, "state(get, set) is required; a Toggle shows the consumer's value");
            require(label != null || width > 0, "a toggle without a label needs size(w, h)");
            return sprite != null ? new SpriteToggle(this, sprite) : new Toggle(this);
        }
    }

    /** The sprite face: the consumer's sprite, lightness-inverted when on. */
    static final class SpriteToggle extends Toggle {
        private final Supplier<Identifier> sprite;

        SpriteToggle(Builder b, Supplier<Identifier> sprite) {
            super(b);
            this.sprite = sprite;
        }

        @Override
        protected void renderBackground(RenderContext ctx, int sx, int sy,
                                        boolean on, boolean disabled, boolean hovered) {
            Identifier id = sprite.get();
            if (id == null) return;
            int w = getWidth(), h = getHeight();
            var g = ctx.graphics();
            if (disabled) {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, id, sx, sy, w, h);
                g.fill(sx, sy, sx + w, sy + h, ElementConstants.DISABLED_OVERLAY);
            } else if (on) {
                g.blitSprite(MKRenderPipelines.GUI_BRIGHTNESS_INVERTED, id, sx, sy, w, h);
            } else {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, id, sx, sy, w, h);
            }
            if (!disabled && hovered) {
                g.fill(sx + 1, sy + 1, sx + w - 1, sy + h - 1, ElementConstants.HOVER_OVERLAY);
            }
        }
    }
}
