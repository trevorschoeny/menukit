package com.trevlar.menukit.api.element;

import com.trevlar.menukit.core.MKRenderPipelines;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.core.PanelRendering;
import com.trevlar.menukit.api.panel.PanelStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * An interactive button within a {@link Panel}: a raised background with a
 * centred label, an icon, or a consumer sprite as its whole face.
 *
 * <pre>{@code
 * Button.builder()
 *         .label(Component.literal("Sort"))
 *         .at(0, 0).size(60, 16)
 *         .onClick(this::sort)
 *         .tooltip(Component.literal("Sort this container"))
 *         .build();
 * }</pre>
 *
 * <h3>Three faces, one builder</h3>
 * <ul>
 *   <li><b>Label</b> (default): {@code label(text)}. A width of 0 sizes the
 *       button to its label. When the panel narrows it below the label, the label
 *       wraps and the button grows taller.</li>
 *   <li><b>Icon</b>: {@code icon(sprite)}, a square button with the sprite inset
 *       2px so the bevel shows; dimmed while disabled. Pair it with a tooltip.</li>
 *   <li><b>Sprite</b>: {@code sprite(sprite)}, the sprite is the whole button
 *       (frame included); pressed draws it with its lightness inverted, hovered
 *       adds a highlight, disabled a dark overlay. A label, if given, sits on
 *       top.</li>
 * </ul>
 *
 * <h3>Input</h3>
 *
 * Left click runs {@code onClick} and plays vanilla's click sound. Other buttons
 * reach {@code onSecondaryClick} when one is set (it gets a {@link Click} with
 * the modifier keys), else fall through to vanilla. Keyboard: Tab focuses the
 * button and Enter or Space presses it, through the shared {@link ElementWidget},
 * which also gives it narration. Disabled (its own {@code disabledWhen}, or its
 * panel's) it draws greyed and takes nothing.
 *
 * <h3>Extension points for consumer subclasses</h3>
 *
 * A subclass (Keybindery's chord face is one) builds through
 * {@link #Button(Builder)} and overrides {@link #renderBackground} and
 * {@link #renderContent}, which receive this frame's {@link Look}. The
 * orchestrating {@link #render} is final.
 *
 * @see PanelElement  The interface this implements
 * @see Toggle        A button that stays pressed
 */
public class Button extends AbstractPanelElement {

    /** The look this frame, handed to the render hooks. */
    public record Look(boolean hovered, boolean pressed, boolean disabled) {}

    /** Height when the builder is given no size: vanilla's button height. */
    public static final int DEFAULT_HEIGHT = 20;

    private final Component label;
    private final Runnable onClick;
    private final @Nullable Consumer<Click> onSecondaryClick;
    private final @Nullable IntSupplier tint;
    private final ControlStyle style;
    // The vanilla stand-in for sound, focus and narration (ElementWidget), made at
    // the first screen attach, not with the element: a Containers menu builds its
    // panels on the dedicated server too, where no screen class exists.
    private @Nullable ElementWidget widget;

    // The label's wrap width (the one wrap helper, Text.wrapWidth): 0 = one
    // line; else the INNER text width the label breaks at. Set by layoutWithin
    // only when the panel narrowed the button below its label, cleared by a wider
    // pass, so the wrap is reversible.
    private int wrapWidth = 0;

    // The press affordance: true from a left press on this button until the
    // release. Interaction state, not a frame cache: it is set by the press and
    // cleared by the release (and by the poll in render, for a release that went
    // to another screen after this button navigated away).
    private boolean pressed = false;

    /** Builds from {@code b}. Protected so a consumer subclass can build through a {@link Builder}. */
    protected Button(Builder b) {
        super(b);
        this.label = b.label;
        this.onClick = b.onClick;
        this.onSecondaryClick = b.onSecondaryClick;
        this.tint = b.tint;
        this.style = b.style;
    }

    /** Starts a button. Give it a {@code label}, an {@code icon} or a {@code sprite}, and an {@code onClick}. */
    public static Builder builder() {
        return new Builder();
    }

    // ── Size ───────────────────────────────────────────────────────────

    /** The label's single-line width plus padding: a width-0 button's natural size. */
    private int labelWidth() {
        return Minecraft.getInstance().font.width(label) + ElementConstants.LABEL_PAD * 2;
    }

    @Override
    public int getWidth() {
        // A width-0 button reports its label's width until the first layout pass.
        return width > 0 ? width : labelWidth();
    }

    @Override
    public int getHeight() {
        if (wrapWidth <= 0) return height;
        int lines = Text.lineCount(label, wrapWidth);
        return Math.max(height, lines * Minecraft.getInstance().font.lineHeight + ElementConstants.LABEL_VPAD * 2);
    }

    @Override
    public int naturalWidth() {
        return authoredWidth > 0 ? authoredWidth : labelWidth();
    }

    /** Caps the button to the budget, then wraps its label when the capped box can't hold it on one line. */
    @Override
    public void layoutWithin(int budget) {
        this.width = Math.min(naturalWidth(), budget);
        this.wrapWidth = Text.wrapWidth(label, width - ElementConstants.LABEL_PAD * 2);
    }

    /** The extra height a wrapped label adds, so the panel pushes what is below down. */
    @Override
    public int extraLayoutHeight() {
        return wrapWidth > 0 ? getHeight() - height : 0;
    }

    @Override public boolean isInteractive() { return true; }

    /** The button's label. */
    public Component getLabel() { return label; }

    // ── Rendering ──────────────────────────────────────────────────────

    /**
     * Resolves this frame's look (hovered, pressed, disabled), paints background,
     * tint and content through the hooks, keeps the vanilla widget current, and
     * queues the tooltip. Final: the extension surface is the two hooks.
     */
    @Override
    public final void render(RenderContext ctx) {
        int sx = ctx.originX() + childX;
        int sy = ctx.originY() + childY;
        boolean disabled = disabled(ctx);

        // A press whose release went to another screen (the button navigated
        // away) never reaches this button; the mouse state is the truth.
        if (pressed && Minecraft.getInstance() != null && Minecraft.getInstance().getWindow() != null
                && GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().handle(),
                        GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_RELEASE) {
            pressed = false;
        }

        boolean hovered = isHovered(ctx);
        if (widget != null) widget.track(sx, sy, getWidth(), getHeight(), hovered, !disabled, narration());
        Look look = new Look(hovered || (widget != null && widget.focused()), pressed && !disabled, disabled);

        renderBackground(ctx, sx, sy, look);
        // The consumer tint: over the background, under the label, inside the
        // border. Here, not in the hook, so a subclass that repaints the
        // background keeps it.
        if (tint != null) {
            int argb = tint.getAsInt();
            if (argb != 0) ctx.graphics().fill(sx + 1, sy + 1, sx + getWidth() - 1, sy + getHeight() - 1, argb);
        }
        renderContent(ctx, sx, sy, look);
        if (hovered) queueTooltip(ctx);
    }

    /** What the narrator reads: the label, or the tooltip for an icon or sprite with none. */
    private Component narration() {
        if (!label.getString().isEmpty()) return label;
        Supplier<Component> t = tooltipSupplier();
        Component text = t != null ? t.get() : null;
        return text != null ? text : Component.empty();
    }

    /**
     * Paints the background: raised, inset while pressed, dark while disabled,
     * with the hover highlight; or vanilla's button sprite under
     * {@link ControlStyle#VANILLA}. Covers the grown height when the label wraps.
     *
     * <p><b>Stable extension point.</b> {@code sx}/{@code sy} are the button's
     * screen-space top-left; runs before {@link #renderContent}; must not mutate
     * the button.
     */
    protected void renderBackground(RenderContext ctx, int sx, int sy, Look look) {
        int w = getWidth(), h = getHeight();
        if (style == ControlStyle.VANILLA) {
            ControlStyle.renderVanillaButton(ctx.graphics(), sx, sy, w, h, !look.disabled(),
                    look.hovered() || look.pressed());
            if (look.pressed()) ControlStyle.renderVanillaPressedOverlay(ctx.graphics(), sx, sy, w, h);
            return;
        }
        if (look.disabled()) {
            PanelRendering.renderPanel(ctx.graphics(), sx, sy, w, h, PanelStyle.DARK);
        } else if (look.pressed()) {
            PanelRendering.renderPanel(ctx.graphics(), sx, sy, w, h, PanelStyle.INSET);
        } else {
            PanelRendering.renderPanel(ctx.graphics(), sx, sy, w, h, PanelStyle.RAISED);
            if (look.hovered()) {
                ctx.graphics().fill(sx + 1, sy + 1, sx + w - 1, sy + h - 1, ElementConstants.HOVER_OVERLAY);
            }
        }
    }

    /**
     * Paints the content: by default the label, centred; wrapped onto several
     * centred lines when the panel narrowed the button below it, else on one line
     * that scrolls if it still overflows.
     *
     * <p><b>Stable extension point.</b> {@code sx}/{@code sy} are the button's
     * screen-space top-left; runs after {@link #renderBackground}; must not mutate
     * the button.
     */
    protected void renderContent(RenderContext ctx, int sx, int sy, Look look) {
        int color = look.disabled() ? ElementConstants.TEXT_DISABLED : ElementConstants.TEXT_LIGHT;
        if (wrapWidth > 0) {
            int lines = Text.lineCount(label, wrapWidth);
            Text.drawWrapped(ctx.graphics(), label, wrapWidth, sx, Text.centeredBlockY(sy, sy + getHeight(), lines),
                    getWidth(), color, true);
            return;
        }
        Text.renderCentered(ctx.graphics(), label, sx, sy, getWidth(), height, color, true);
    }

    // ── Input ──────────────────────────────────────────────────────────

    /** Left click presses (sound, then {@code onClick}); another button reaches {@code onSecondaryClick}, if set. */
    @Override
    public boolean mouseClicked(InputContext in, int button) {
        if (disabled(in)) return false;
        if (button != Click.LEFT) {
            if (onSecondaryClick == null) return false;
            onSecondaryClick.accept(Click.of(button));
            return true;
        }
        // The press affordance is set before onClick, so a handler that reads the
        // button's look sees it pressed.
        pressed = true;
        ElementWidget.playClickSound();
        onClick.run();
        return true;
    }

    /** Releases the press affordance, wherever the cursor now is. */
    @Override
    public boolean mouseReleased(InputContext in, int button) {
        if (button == Click.LEFT) pressed = false;
        return false;
    }

    /** Enter or Space on the focused button (vanilla played the sound). */
    private void press() {
        if (!ownDisabled()) onClick.run();
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

    public static class Builder extends AbstractPanelElement.Builder<Button, Builder> {
        private Component label = Component.empty();
        private Runnable onClick = () -> {};
        private @Nullable Consumer<Click> onSecondaryClick;
        private @Nullable IntSupplier tint;
        private ControlStyle style = ControlStyle.MK;
        private @Nullable Supplier<Identifier> icon;
        private @Nullable Supplier<Identifier> sprite;

        protected Builder() {
            this.width = 0;
            this.height = DEFAULT_HEIGHT;
        }

        @Override protected Builder self() { return this; }

        /** The text on the button. */
        public Builder label(Component label) {
            this.label = Objects.requireNonNull(label, "label");
            return this;
        }

        /** Size in pixels. A width of 0 sizes the button to its label. Default {@code 0 x 20}. */
        @Override
        public Builder size(int width, int height) {
            return super.size(width, height);
        }

        /** What a left click (or Enter on the focused button) does. */
        public Builder onClick(Runnable onClick) {
            this.onClick = Objects.requireNonNull(onClick, "onClick");
            return this;
        }

        /**
         * Handles every non-left mouse button (right, middle, shift+right, via the
         * {@link Click}); those clicks are consumed instead of reaching vanilla.
         */
        public Builder onSecondaryClick(Consumer<Click> handler) {
            this.onSecondaryClick = Objects.requireNonNull(handler, "handler");
            return this;
        }

        /**
         * An ARGB fill over the background and under the label, read every frame; 0
         * for none. Shows a state the button does not own: {@code () -> pinned ? 0x50FFC000 : 0}.
         */
        public Builder tint(IntSupplier tint) {
            this.tint = Objects.requireNonNull(tint, "tint");
            return this;
        }

        /** {@link ControlStyle#MK} (default) or {@link ControlStyle#VANILLA}'s button sprite. */
        public Builder style(ControlStyle style) {
            this.style = Objects.requireNonNull(style, "style");
            return this;
        }

        /** An icon button: {@code sprite} centred, inset 2px. Square: size it {@code (n, n)}. */
        public Builder icon(Identifier sprite) {
            Objects.requireNonNull(sprite, "sprite");
            return icon(() -> sprite);
        }

        /** An icon button whose sprite is read every frame (a state shown by icon). */
        public Builder icon(Supplier<Identifier> sprite) {
            this.icon = Objects.requireNonNull(sprite, "sprite");
            return this;
        }

        /** A sprite button: {@code sprite} is the whole button, frame included. */
        public Builder sprite(Identifier sprite) {
            Objects.requireNonNull(sprite, "sprite");
            return sprite(() -> sprite);
        }

        /** A sprite button whose sprite is read every frame. */
        public Builder sprite(Supplier<Identifier> sprite) {
            this.sprite = Objects.requireNonNull(sprite, "sprite");
            return this;
        }

        @Override
        public Button build() {
            require(icon == null || sprite == null, "icon(...) and sprite(...) are two different faces; pick one");
            if (icon != null) {
                require(width > 0 && height > 0, "an icon button needs size(n, n)");
                return new IconButton(this, icon);
            }
            if (sprite != null) {
                require(width > 0 && height > 0, "a sprite button needs size(w, h), the sprite's own size");
                return new SpriteButton(this, sprite);
            }
            return new Button(this);
        }
    }

    // ── Icon face ──────────────────────────────────────────────────────

    /** A square button with a centred sprite. Intrinsic: never stretched or shrunk. */
    static final class IconButton extends Button {
        private static final int INSET = 2;
        private static final float DISABLED_ALPHA = 0.4f;
        private final Supplier<Identifier> sprite;

        IconButton(Builder b, Supplier<Identifier> sprite) {
            super(b);
            this.sprite = sprite;
        }

        @Override public void fillWidth(int width) {}
        @Override public void layoutWithin(int budget) {}

        @Override
        protected void renderContent(RenderContext ctx, int sx, int sy, Look look) {
            Identifier id = sprite.get();
            if (id == null) return;
            int size = getWidth() - INSET * 2;
            ctx.graphics().blitSprite(RenderPipelines.GUI_TEXTURED, id, sx + INSET, sy + INSET, size, size,
                    look.disabled() ? DISABLED_ALPHA : 1.0f);
        }
    }

    // ── Sprite face ────────────────────────────────────────────────────

    /** A button whose whole face is a consumer sprite. Intrinsic: never stretched or shrunk. */
    static final class SpriteButton extends Button {
        private final Supplier<Identifier> sprite;

        SpriteButton(Builder b, Supplier<Identifier> sprite) {
            super(b);
            this.sprite = sprite;
        }

        @Override public void fillWidth(int width) {}
        @Override public void layoutWithin(int budget) {}

        @Override
        protected void renderBackground(RenderContext ctx, int sx, int sy, Look look) {
            Identifier id = sprite.get();
            if (id == null) return;
            int w = getWidth(), h = getHeight();
            var g = ctx.graphics();
            if (look.disabled()) {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, id, sx, sy, w, h);
                g.fill(sx, sy, sx + w, sy + h, ElementConstants.DISABLED_OVERLAY);
            } else if (look.pressed()) {
                // The lightness-inverted sprite is the pressed affordance.
                g.blitSprite(MKRenderPipelines.GUI_BRIGHTNESS_INVERTED, id, sx, sy, w, h);
            } else {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, id, sx, sy, w, h);
                if (look.hovered()) g.fill(sx + 1, sy + 1, sx + w - 1, sy + h - 1, ElementConstants.HOVER_OVERLAY);
            }
        }
    }
}
