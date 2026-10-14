package com.trevlar.menukit.api.element;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A picture at a fixed size: the "show a picture" primitive. No input.
 *
 * <pre>{@code
 * Icon.builder().sprite(MY_SPRITE).size(16, 16).tooltip(Component.literal("Locked")).build();
 * Icon.builder().texture(modIcon, 64).size(16, 16).build();   // a whole texture, scaled
 * }</pre>
 *
 * <p>Two sources, exactly one per icon. A <b>sprite</b> is a GUI-atlas sprite
 * (a {@code textures/gui/sprites} entry, nine-slicing and all). A <b>texture</b>
 * is a standalone square texture by its own {@link Identifier}: one registered
 * at runtime (a {@code DynamicTexture}, a mod's icon read from its jar) or any
 * {@code textures/...} file. The whole texture is drawn scaled into the icon's
 * size, so {@code textureSize} is its side in texels (64 for a 64 x 64 image).
 *
 * <p>Either may be read every frame ({@code sprite(Supplier)},
 * {@code texture(Supplier, int)}), for a state shown by picture or a flip-book
 * animation; null draws nothing. Intrinsic: an icon keeps its size however
 * narrow the panel. Disabled (its own {@code disabledWhen} or its panel's), it
 * draws at 40% alpha.
 *
 * @see Button  An icon that can be pressed ({@code Button.builder().icon(...)})
 */
public class Icon extends AbstractPanelElement {

    private static final float DISABLED_ALPHA = 0.4f;

    /** The sprite, or the texture when {@link #textureSize} is positive. */
    private final Supplier<Identifier> source;
    /** The texture's side in texels; 0 for an atlas sprite. */
    private final int textureSize;

    protected Icon(Builder b) {
        super(b);
        this.source = b.sprite != null ? b.sprite : b.texture;
        this.textureSize = b.sprite != null ? 0 : b.textureSize;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The sprite or texture the icon would draw right now. */
    public Identifier getCurrentSprite() {
        return source.get();
    }

    @Override public void layoutWithin(int budget) {}
    @Override public void fillWidth(int width) {}

    @Override
    public void render(RenderContext ctx) {
        draw(ctx.graphics(), source.get(), textureSize, ctx.originX() + childX, ctx.originY() + childY,
                width, height, disabled(ctx) ? DISABLED_ALPHA : 1.0f);
        queueTooltip(ctx);
    }

    /**
     * The one place that draws an icon's picture, here and wherever MenuKit puts
     * one beside something else (a {@link Tabs} tab): an atlas sprite when
     * {@code textureSize} is 0, else the whole {@code textureSize}-square texture
     * scaled into {@code w x h}. Null draws nothing.
     */
    static void draw(GuiGraphicsExtractor g, @Nullable Identifier id, int textureSize, int x, int y, int w, int h,
                     float alpha) {
        if (id == null) return;
        if (textureSize <= 0) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, id, x, y, w, h, alpha);
            return;
        }
        // The full-region overload: the source rectangle is the whole texture
        // (textureSize texels each way), drawn into w x h. The shorter overload
        // would read only the first w x h texels, a corner of a 64 px icon.
        g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0, 0, w, h, textureSize, textureSize,
                textureSize, textureSize, ARGB.white(alpha));
    }

    public static class Builder extends AbstractPanelElement.Builder<Icon, Builder> {
        private @Nullable Supplier<Identifier> sprite;
        private @Nullable Supplier<Identifier> texture;
        private int textureSize;

        protected Builder() {}

        @Override protected Builder self() { return this; }

        /** The picture, from the GUI sprite atlas. This or {@link #texture}. */
        public Builder sprite(Identifier sprite) {
            Objects.requireNonNull(sprite, "sprite");
            return sprite(() -> sprite);
        }

        /** The sprite, read every frame. */
        public Builder sprite(Supplier<Identifier> sprite) {
            this.sprite = Objects.requireNonNull(sprite, "sprite");
            return this;
        }

        /**
         * The picture, a standalone square texture {@code textureSize} texels on a
         * side (a {@code DynamicTexture} registered at runtime, say), drawn whole,
         * scaled to the icon's size. This or {@link #sprite}.
         */
        public Builder texture(Identifier texture, int textureSize) {
            Objects.requireNonNull(texture, "texture");
            return texture(() -> texture, textureSize);
        }

        /** The texture, read every frame; null draws nothing (a texture not loaded yet). */
        public Builder texture(Supplier<Identifier> texture, int textureSize) {
            if (textureSize <= 0) throw new IllegalArgumentException("Icon: textureSize must be positive");
            this.texture = Objects.requireNonNull(texture, "texture");
            this.textureSize = textureSize;
            return this;
        }

        /** Required: size in pixels. */
        @Override
        public Builder size(int width, int height) {
            return super.size(width, height);
        }

        @Override
        public Icon build() {
            require(sprite != null || texture != null, "sprite(...) or texture(...) is required");
            require(sprite == null || texture == null, "sprite(...) and texture(...) are two pictures; pick one");
            require(width > 0 && height > 0, "size(w, h) is required");
            return new Icon(this);
        }
    }
}
