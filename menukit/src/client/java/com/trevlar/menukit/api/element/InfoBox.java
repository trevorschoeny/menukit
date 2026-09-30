package com.trevlar.menukit.api.element;

import com.trevlar.menukit.core.PanelRendering;
import com.trevlar.menukit.api.panel.PanelStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A block of text on a raised background: an explanation or a notice inside a
 * panel. No input.
 *
 * <pre>{@code
 * InfoBox.builder().text(Component.literal("Locks apply to every container.")).build();
 * }</pre>
 *
 * <p>Sizes itself from its text, with {@link #PADDING} around it; wraps when its
 * panel is narrower, growing taller. Draws its text in the disabled grey while
 * disabled (its own {@code disabledWhen}, or its panel's or container's).
 */
public class InfoBox extends AbstractPanelElement {

    /** Space between the background's edge and the text. */
    public static final int PADDING = 4;

    private final Supplier<Component> text;
    private int wrapWidth = 0;

    protected InfoBox(Builder b) {
        super(b);
        this.text = b.text;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The text the box would draw right now. */
    public Component getCurrentText() { return text.get(); }

    @Override
    public int naturalWidth() {
        Component t = text.get();
        return (t != null ? Minecraft.getInstance().font.width(t) : 0) + 2 * PADDING;
    }

    @Override
    public int getWidth() {
        return wrapWidth > 0 ? wrapWidth + 2 * PADDING : naturalWidth();
    }

    @Override
    public int getHeight() {
        Component t = text.get();
        int lines = t == null ? 1 : Text.lineCount(t, wrapWidth);
        return lines * Minecraft.getInstance().font.lineHeight + 2 * PADDING;
    }

    @Override
    public void layoutWithin(int budget) {
        wrapWidth = Text.wrapWidth(text.get(), budget - 2 * PADDING);
    }

    @Override
    public int extraLayoutHeight() {
        return wrapWidth > 0 ? Math.max(0, getHeight() - (Minecraft.getInstance().font.lineHeight + 2 * PADDING)) : 0;
    }

    @Override
    public void fillWidth(int width) {}

    @Override
    public void render(RenderContext ctx) {
        Component t = text.get();
        if (t == null) return;
        int sx = ctx.originX() + childX;
        int sy = ctx.originY() + childY;
        PanelRendering.renderPanel(ctx.graphics(), sx, sy, getWidth(), getHeight(), PanelStyle.RAISED);
        int color = disabled(ctx) ? ElementConstants.TEXT_DISABLED : ElementConstants.TEXT_DARK;
        Text.drawWrapped(ctx.graphics(), t, wrapWidth, sx + PADDING, sy + PADDING, 0, color, false);
        queueTooltip(ctx);
    }

    public static class Builder extends AbstractPanelElement.Builder<InfoBox, Builder> {
        private @Nullable Supplier<Component> text;

        protected Builder() {}

        @Override protected Builder self() { return this; }

        /** Required: the text. */
        public Builder text(Component text) {
            Objects.requireNonNull(text, "text");
            return text(() -> text);
        }

        /** Required: the text, read every frame. */
        public Builder text(Supplier<Component> text) {
            this.text = Objects.requireNonNull(text, "text");
            return this;
        }

        @Override
        public InfoBox build() {
            require(text != null, "text(...) is required");
            return new InfoBox(this);
        }
    }
}
