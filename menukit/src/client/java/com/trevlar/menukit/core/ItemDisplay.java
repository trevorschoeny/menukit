package com.trevlar.menukit.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * An item drawn at a fixed size, with its count and durability bar, and
 * optionally a coloured outline. No input, no storage: it only draws.
 *
 * <pre>{@code
 * ItemDisplay.builder().item(() -> player.getMainHandItem()).size(16, 16)
 *         .outline(() -> locked ? 0xFFFF5555 : 0)
 *         .build();
 * }</pre>
 *
 * <p>Count and durability overlays show by default, as vanilla draws a slot;
 * {@code hideCount()} and {@code hideDurability()} turn them off. A size other
 * than 16 scales the item through the pose matrix (items are square: the smaller
 * side is used).
 *
 * <h3>Outline</h3>
 *
 * {@code outline(argb)} draws a one-pixel line of that colour around the item's
 * own silhouette, read every frame (0 for none). It is
 * {@link SlotRendering#drawItemOutline}, the same primitive a slot decoration
 * uses.
 */
public class ItemDisplay extends AbstractPanelElement {

    /** Vanilla's item size. */
    public static final int DEFAULT_SIZE = 16;

    private final Supplier<ItemStack> item;
    private final boolean showCount;
    private final boolean showDurability;
    private final @Nullable IntSupplier outline;

    protected ItemDisplay(Builder b) {
        super(b);
        this.item = b.item;
        this.showCount = b.showCount;
        this.showDurability = b.showDurability;
        this.outline = b.outline;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The stack the display would draw right now. */
    public ItemStack getCurrentStack() { return item.get(); }

    @Override public void layoutWithin(int budget) {}
    @Override public void fillWidth(int width) {}

    private void decorations(GuiGraphicsExtractor g, Font font, ItemStack stack, int x, int y) {
        if (showCount && showDurability) {
            g.itemDecorations(font, stack, x, y);
        } else if (showDurability) {
            g.itemDecorations(font, stack, x, y, "");        // an empty count text hides the count
        } else if (showCount) {
            ItemStack noBar = stack.copy();                  // no damage, no bar
            noBar.remove(DataComponents.DAMAGE);
            g.itemDecorations(font, noBar, x, y);
        }
    }

    @Override
    public void render(RenderContext ctx) {
        ItemStack stack = item.get();
        if (stack != null && !stack.isEmpty()) {
            var g = ctx.graphics();
            Font font = Minecraft.getInstance().font;
            int x = ctx.originX() + childX;
            int y = ctx.originY() + childY;
            int argb = outline != null ? outline.getAsInt() : 0;
            int size = Math.min(width, height);
            boolean scaled = size != DEFAULT_SIZE;
            if (scaled) {
                float s = size / (float) DEFAULT_SIZE;
                g.pose().pushMatrix();
                g.pose().translate((float) x, (float) y);
                g.pose().scale(s, s);
                x = 0;
                y = 0;
            }
            if (argb != 0) SlotRendering.drawItemOutline(g, stack, x, y, argb);
            else g.item(stack, x, y);
            decorations(g, font, stack, x, y);
            if (scaled) g.pose().popMatrix();
        }
        queueTooltip(ctx);
    }

    public static class Builder extends AbstractPanelElement.Builder<ItemDisplay, Builder> {
        private @Nullable Supplier<ItemStack> item;
        private boolean showCount = true;
        private boolean showDurability = true;
        private @Nullable IntSupplier outline;

        protected Builder() {
            this.width = DEFAULT_SIZE;
            this.height = DEFAULT_SIZE;
        }

        @Override protected Builder self() { return this; }

        /** Required: the stack. */
        public Builder item(ItemStack stack) {
            Objects.requireNonNull(stack, "stack");
            return item(() -> stack);
        }

        /** Required: the stack, read every frame. An empty stack draws nothing. */
        public Builder item(Supplier<ItemStack> stack) {
            this.item = Objects.requireNonNull(stack, "stack");
            return this;
        }

        /** Size in pixels; the item scales to the smaller side. Default 16 x 16. */
        @Override
        public Builder size(int width, int height) {
            return super.size(width, height);
        }

        /** Hides the count overlay. */
        public Builder hideCount() {
            this.showCount = false;
            return this;
        }

        /** Hides the durability bar. */
        public Builder hideDurability() {
            this.showDurability = false;
            return this;
        }

        /** A one-pixel outline of this ARGB colour around the item's silhouette. */
        public Builder outline(int argb) {
            return outline(() -> argb);
        }

        /** An outline colour read every frame; 0 draws none. */
        public Builder outline(IntSupplier argb) {
            this.outline = Objects.requireNonNull(argb, "argb");
            return this;
        }

        @Override
        public ItemDisplay build() {
            require(item != null, "item(...) is required");
            return new ItemDisplay(this);
        }
    }
}
