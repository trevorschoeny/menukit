package com.trevlar.menukit.api.element;

import com.trevlar.menukit.core.ItemOpenness;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * How an item draws at a point in an animation (7.4.0): given the item, its 16 px box and
 * the animation's progress {@code t}, from 0 to 1, it draws the item. The mod picks an
 * animation, runs its progress with a {@link Transition} (duration, easing, forward or back),
 * and hands both where an item is drawn, a container screen's slot through
 * {@link com.trevlar.menukit.api.panel.SlotItemLooks}:
 *
 * <pre>{@code
 * ItemAnimation look = ItemAnimation.OPEN_LID.posed(t -> new ItemAnimation.Pose(1f - 0.3f * t, 0f, 2f * t));
 * }</pre>
 *
 * <p>A mod writes its own as a lambda: draw whatever the item should look like at {@code t}
 * (another stack, a sprite, the item with something over it). MenuKit draws the item's count
 * and durability bar after it, where vanilla would, unmoved by the animation.
 */
@FunctionalInterface
public interface ItemAnimation {

    /** Draws {@code stack} in the 16 px box at {@code (x, y)}, {@code t} of the way through. */
    void draw(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, float t);

    /**
     * A box opening, its lid {@code t} open: the openness vanilla's own shulker box and chest
     * renderers take, so a shulker box's lid lifts and twists open and a chest's (an ender
     * chest's, a copper one's, a trapped one's) swings up, in whatever model and texture the
     * resource pack gives them. Every other item draws as it is. An open lid stands past the
     * 16 px box, and draws there; to keep it inside, shrink the item with {@link #posed}.
     */
    ItemAnimation OPEN_LID = ItemOpenness::draw;

    /**
     * This animation moved and scaled as it plays: at each {@code t}, {@code pose} says how big
     * the item draws (scaled about its box's center) and how far it moves, in GUI pixels.
     */
    default ItemAnimation posed(Posing pose) {
        Objects.requireNonNull(pose, "pose");
        ItemAnimation inner = this;
        return (g, stack, x, y, t) -> {
            Pose p = pose.at(t);
            if (p.equals(Pose.NONE)) {
                inner.draw(g, stack, x, y, t);
                return;
            }
            g.pose().pushMatrix();
            try {
                g.pose().translate(x + 8f + p.offsetX(), y + 8f + p.offsetY());
                g.pose().scale(p.scale(), p.scale());
                g.pose().translate(-(x + 8f), -(y + 8f));
                inner.draw(g, stack, x, y, t);
            } finally {
                g.pose().popMatrix();
            }
        };
    }

    /** This animation at {@code t}: what a slot's item draws as this frame. */
    default Frame at(float t) {
        return new Frame(this, t);
    }

    /** An animation at one point: {@code animation} drawn {@code t} of the way through. */
    record Frame(ItemAnimation animation, float t) {
        public Frame {
            Objects.requireNonNull(animation, "animation");
        }
    }

    /** How big an item draws, about its box's center (1 is as it is), and how far it moves, in GUI pixels. */
    record Pose(float scale, float offsetX, float offsetY) {
        /** As it is. */
        public static final Pose NONE = new Pose(1f, 0f, 0f);
    }

    /** The {@link Pose} at each point of an animation. */
    @FunctionalInterface
    interface Posing {
        Pose at(float t);
    }
}
