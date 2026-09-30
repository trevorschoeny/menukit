package com.trevlar.menukit.core;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A sprite at a fixed size: the "show a picture" primitive. No input.
 *
 * <pre>{@code
 * Icon.builder().sprite(MY_SPRITE).size(16, 16).tooltip(Component.literal("Locked")).build();
 * }</pre>
 *
 * <p>The sprite may be read every frame ({@code sprite(Supplier)}), for a state
 * shown by picture or a flip-book animation. Intrinsic: an icon keeps its size
 * however narrow the panel. Disabled (its own {@code disabledWhen} or its
 * panel's), it draws at 40% alpha.
 *
 * @see Button  An icon that can be pressed ({@code Button.builder().icon(...)})
 */
public class Icon extends AbstractPanelElement {

    private static final float DISABLED_ALPHA = 0.4f;

    private final Supplier<Identifier> sprite;

    protected Icon(Builder b) {
        super(b);
        this.sprite = b.sprite;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The sprite the icon would draw right now. */
    public Identifier getCurrentSprite() {
        return sprite.get();
    }

    @Override public void layoutWithin(int budget) {}
    @Override public void fillWidth(int width) {}

    @Override
    public void render(RenderContext ctx) {
        Identifier id = sprite.get();
        if (id != null) {
            ctx.graphics().blitSprite(RenderPipelines.GUI_TEXTURED, id,
                    ctx.originX() + childX, ctx.originY() + childY, width, height,
                    disabled(ctx) ? DISABLED_ALPHA : 1.0f);
        }
        queueTooltip(ctx);
    }

    public static class Builder extends AbstractPanelElement.Builder<Icon, Builder> {
        private @Nullable Supplier<Identifier> sprite;

        protected Builder() {}

        @Override protected Builder self() { return this; }

        /** Required: the sprite. */
        public Builder sprite(Identifier sprite) {
            Objects.requireNonNull(sprite, "sprite");
            return sprite(() -> sprite);
        }

        /** Required: the sprite, read every frame. */
        public Builder sprite(Supplier<Identifier> sprite) {
            this.sprite = Objects.requireNonNull(sprite, "sprite");
            return this;
        }

        /** Required: size in pixels. */
        @Override
        public Builder size(int width, int height) {
            return super.size(width, height);
        }

        @Override
        public Icon build() {
            require(sprite != null, "sprite(...) is required");
            require(width > 0 && height > 0, "size(w, h) is required");
            return new Icon(this);
        }
    }
}
