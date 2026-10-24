package com.trevlar.menukit.api.element;

import com.trevlar.menukit.core.ItemOpenness;
import com.trevlar.menukit.core.ItemTurn;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * How an item draws at a point in an animation (Fluorite): given the item, its 16 px box and
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

    /** The item as vanilla draws it, at every {@code t}: the start for a {@link #posed} transform alone. */
    ItemAnimation PLAIN = (g, stack, x, y, t) -> g.item(stack, x, y);

    /**
     * A box opening, its lid {@code t} open: the openness vanilla's own shulker box and chest
     * renderers take, so a shulker box's lid lifts and twists open and a chest's (an ender
     * chest's, a copper one's, a trapped one's) swings up, in whatever model and texture the
     * resource pack gives them. Every other item draws as it is. An open lid stands past the
     * 16 px box, and draws there; to keep it inside, shrink the item with {@link #posed}.
     */
    ItemAnimation OPEN_LID = ItemOpenness::draw;

    /**
     * This animation moved, scaled and turned as it plays: at each {@code t}, {@code pose} says
     * how big the item draws (scaled about its box's center), how far it moves, in GUI pixels,
     * and how far its model turns ({@link Pose#pitch}, {@link Pose#yaw}, {@link Pose#roll}).
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
            g.pose().translate(x + 8f + p.offsetX(), y + 8f + p.offsetY());
            g.pose().scale(p.scale(), p.scale());
            g.pose().translate(-(x + 8f), -(y + 8f));
            if (p.pitch() == 0f && p.yaw() == 0f && p.roll() == 0f) inner.draw(g, stack, x, y, t);
            else ItemTurn.draw(p.pitch(), p.yaw(), p.roll(), () -> inner.draw(g, stack, x, y, t));
            g.pose().popMatrix();
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

    /**
     * How big an item draws, about its box's center (1 is as it is), how far it moves, in GUI
     * pixels, and how far its model turns, in degrees about its center. The turn adds to the
     * rotation the item's model draws with in the GUI (its {@code display.gui.rotation}, x, y
     * and z, as a resource pack writes it): {@code pitch} to x, {@code yaw} to y, {@code roll}
     * to z. A block's is {@code [30, 225, 0]} and a head's {@code [30, 45, 0]}; a head faces the
     * viewer at {@code [0, 0, 0]}, so a pitch of -30 and a yaw of -45 turn it to face front:
     *
     * <pre>{@code
     * ItemAnimation FACE_FRONT = ItemAnimation.PLAIN.posed(t -> new ItemAnimation.Pose(1f, 0f, 0f, -30f * t, -45f * t, 0f));
     * }</pre>
     *
     * A flat item (a sword, a stick) has no depth to show: a yaw or pitch turns it edge on, like
     * a card, and a roll spins it in the screen's plane. Angles round to whole degrees.
     */
    record Pose(float scale, float offsetX, float offsetY, float pitch, float yaw, float roll) {
        /** As it is. */
        public static final Pose NONE = new Pose(1f, 0f, 0f);

        /** Scaled and moved, not turned. */
        public Pose(float scale, float offsetX, float offsetY) {
            this(scale, offsetX, offsetY, 0f, 0f, 0f);
        }
    }

    /** The {@link Pose} at each point of an animation. */
    @FunctionalInterface
    interface Posing {
        Pose at(float t);
    }
}
