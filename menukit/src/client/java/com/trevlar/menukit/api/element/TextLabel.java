package com.trevlar.menukit.api.element;

import com.trevlar.menukit.api.panel.Panel;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A line of text in a {@link Panel}, fixed or read every frame.
 *
 * <pre>{@code
 * TextLabel.builder().text(Component.literal("Restock")).at(0, 0).build();
 * TextLabel.builder().text(() -> Component.literal("Bees: " + bees())).color(TextLabel.COLOR_LIGHT).shadow(true).build();
 * }</pre>
 *
 * <h3>Wrap</h3>
 *
 * A label wider than the room its panel gives it wraps onto several lines
 * (vanilla's own line breaking) and the panel pushes what is below it down; a
 * wider pass puts it back on one line. A HUD label, which has no panel budget,
 * can be given a fixed {@code wrapWidth}.
 *
 * <h3>Disabled look</h3>
 *
 * While its own {@code disabledWhen} holds, or its panel or container is
 * disabled, the label draws in the disabled grey (§0066): the text of a greyed
 * settings group greys with its controls, with no colour swapping by hand.
 *
 * <h3>ARGB (26.2)</h3>
 *
 * A colour must carry an alpha byte ({@code 0xFF404040}, not {@code 0x404040}):
 * vanilla silently drops text whose alpha is zero.
 *
 * <h3>Supplier text and layout</h3>
 *
 * A label's width is its text's. Text that changes width every frame moves the
 * panel's layout with it; keep alternatives about the same width, or give the
 * panel a pinned width.
 */
public class TextLabel extends AbstractPanelElement {

    /** Dark grey, no shadow: vanilla's container-label colour on a light panel. */
    public static final int COLOR_DARK = ElementConstants.TEXT_DARK;

    /** White, drawn with a shadow: readable on a dark panel. */
    public static final int COLOR_LIGHT = ElementConstants.TEXT_LIGHT;

    private final Supplier<Component> text;
    private final int color;
    private final boolean shadow;
    private final float scale;
    private final boolean backdrop;
    private final @Nullable Runnable onRender;
    /** A fixed wrap width (a HUD label's), or 0 to wrap to the panel's budget. */
    private final int fixedWrapWidth;

    // The wrap width in FONT space (the one wrap helper): 0 = one line. Set by the
    // layout pass when the label is wider than its room, cleared by a wider pass.
    private int wrapWidth;

    protected TextLabel(Builder b) {
        super(b);
        this.text = b.text;
        this.color = b.color;
        this.shadow = b.shadow;
        this.scale = b.scale;
        this.backdrop = b.backdrop;
        this.onRender = b.onRender;
        this.fixedWrapWidth = b.wrapWidth;
        this.wrapWidth = b.wrapWidth;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The text the label would draw right now. */
    public Component getCurrentText() { return text.get(); }

    // ── Size (scaled screen pixels; the wrap width is font space) ──────

    @Override
    public int naturalWidth() {
        Component t = text.get();
        return t == null ? 0 : (int) (Minecraft.getInstance().font.width(t) * scale);
    }

    @Override
    public int getWidth() {
        Component t = text.get();
        if (t == null) return 0;
        int fontWidth = wrapWidth > 0 ? wrapWidth : Minecraft.getInstance().font.width(t);
        return (int) (fontWidth * scale);
    }

    @Override
    public int getHeight() {
        Component t = text.get();
        if (t == null) return 0;
        return (int) (Text.lineCount(t, wrapWidth) * Minecraft.getInstance().font.lineHeight * scale);
    }

    /** Wraps to the budget (converted to font space) when the text does not fit; a fixed wrap width stays. */
    @Override
    public void layoutWithin(int budget) {
        if (fixedWrapWidth > 0) return;
        wrapWidth = Text.wrapWidth(text.get(), (int) (budget / scale));
    }

    /** The extra height of the lines past the first, so the panel pushes what is below down. */
    @Override
    public int extraLayoutHeight() {
        if (wrapWidth <= 0) return 0;
        return Math.max(0, getHeight() - (int) (Minecraft.getInstance().font.lineHeight * scale));
    }

    @Override
    public void fillWidth(int width) {}

    // ── Rendering ──────────────────────────────────────────────────────

    @Override
    public void render(RenderContext ctx) {
        if (onRender != null) onRender.run();
        Component t = text.get();
        if (t == null) return;
        var font = Minecraft.getInstance().font;
        var g = ctx.graphics();
        int x = ctx.originX() + childX;
        int y = ctx.originY() + childY;
        int drawColor = disabled(ctx) ? ElementConstants.TEXT_DISABLED : color;

        // A scale draws through the pose matrix, in font space from (0, 0); at 1 the
        // matrix is left alone.
        boolean scaled = scale != 1.0f;
        if (scaled) {
            g.pose().pushMatrix();
            g.pose().translate((float) x, (float) y);
            g.pose().scale(scale, scale);
            x = 0;
            y = 0;
        }
        if (backdrop) {
            int tw = wrapWidth > 0 ? wrapWidth : font.width(t);
            int th = Text.lineCount(t, wrapWidth) * font.lineHeight;
            g.fill(x - 1, y - 1, x + tw + 1, y + th + 1, 0xBB000000);
        }
        Text.drawWrapped(g, t, wrapWidth, x, y, 0, drawColor, shadow);
        if (scaled) g.pose().popMatrix();
        queueTooltip(ctx);
    }

    // ── Builder ────────────────────────────────────────────────────────

    public static class Builder extends AbstractPanelElement.Builder<TextLabel, Builder> {
        private @Nullable Supplier<Component> text;
        private int color = COLOR_DARK;
        private boolean shadow = false;
        private float scale = 1.0f;
        private boolean backdrop = false;
        private @Nullable Runnable onRender;
        private int wrapWidth = 0;

        protected Builder() {}

        @Override protected Builder self() { return this; }

        /** Required: the text. */
        public Builder text(Component text) {
            Objects.requireNonNull(text, "text");
            return text(() -> text);
        }

        /** Required: the text, read every frame. A {@code null} result draws nothing. */
        public Builder text(Supplier<Component> text) {
            this.text = Objects.requireNonNull(text, "text");
            return this;
        }

        /** ARGB colour, alpha byte included. Default {@link #COLOR_DARK}. */
        public Builder color(int argb) {
            this.color = argb;
            return this;
        }

        /** Whether to draw a drop shadow. Default false (vanilla's label style). */
        public Builder shadow(boolean shadow) {
            this.shadow = shadow;
            return this;
        }

        /** A uniform scale (2 for a title). The label reports its scaled size. Default 1. */
        public Builder scale(float scale) {
            this.scale = Math.max(0.01f, scale);
            return this;
        }

        /** A translucent dark plate behind the text, for HUD text over the world. */
        public Builder backdrop(boolean backdrop) {
            this.backdrop = backdrop;
            return this;
        }

        /** Runs at the top of every render (a HUD counter or animation driver). */
        public Builder onRender(Runnable onRender) {
            this.onRender = Objects.requireNonNull(onRender, "onRender");
            return this;
        }

        /**
         * A fixed wrap width in font pixels, for a label with no panel budget (a HUD
         * label). Without it the label wraps to the room its panel gives it.
         */
        public Builder wrapWidth(int pixels) {
            this.wrapWidth = Math.max(0, pixels);
            return this;
        }

        @Override
        public TextLabel build() {
            require(text != null, "text(...) is required");
            return new TextLabel(this);
        }
    }
}
