package com.trevlar.menukit.api.hud;

import com.trevlar.menukit.api.Declarations;
import com.trevlar.menukit.core.CycleStrip;

import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * The cycle strip: a small hotbar drawn just right of the vanilla one, showing a ring
 * of stacks that cycles through the held hotbar slot, with a spring slide each time
 * the ring turns (since 7.1.0).
 *
 * <p>A mod that rotates stacks through a hotbar slot (a column of the inventory, a few
 * extra slots behind the hotbar, anything with a ring) registers a {@link Source}.
 * Every frame the HUD draws, MenuKit asks each source for its {@link View} of the
 * selected hotbar slot and draws what comes back. The sources never draw, and the
 * strip never knows which mod a ring belongs to, so one strip serves every mod.
 *
 * <h3>Rows and columns</h3>
 *
 * A source's {@link Source#axis()} says which way its ring reads. A {@link Axis#ROW}
 * ring is a mini hotbar at the hotbar's height. A {@link Axis#COLUMN} ring is one
 * cell at the hotbar's height holding the held stack, with the rest of the ring
 * rising above it. When a row and a column are both active on the selected slot they
 * draw as a cross that shares the held cell, so the held stack is drawn once. At most
 * one ring per axis draws: if two sources of the same axis are active on one slot, the
 * one registered first wins.
 *
 * <h3>The slide</h3>
 *
 * The strip slides only when told to. After a source's ring turns, call
 * {@link #slide}; the strip animates from where each stack was to where the view now
 * puts it, over {@link #SLIDE_MILLIS} on an ease-out-back spring. A ring that changes
 * because something else moved the held stack (a mod that rotates whole hotbar rows,
 * say) uses {@link #snapshot} before and {@link #slideChanged} after: only the rings
 * whose contents actually changed slide, and only their held cell moves.
 *
 * <p>Usage:
 * <pre>{@code
 * // client initializer
 * HudCycleStrip.Source source = hotbarSlot ->
 *         enabled() ? new HudCycleStrip.View(ringFor(hotbarSlot), 0) : null;
 * HudCycleStrip.register(source);
 *
 * // later, right after the ring turned one step toward the hand
 * HudCycleStrip.slide(source, hotbarSlot, HudCycleStrip.Direction.FORWARD);
 * }</pre>
 *
 * <p>The guide is {@code docs/cycle-strip.md}.
 */
public final class HudCycleStrip {

    private HudCycleStrip() {} // static API only

    /** How long one slide takes, in milliseconds. */
    public static final long SLIDE_MILLIS = CycleStrip.SLIDE_MILLIS;

    /** Which way a ring reads on screen. */
    public enum Axis {
        /** A mini hotbar beside the vanilla one, at the same height. */
        ROW,
        /** One cell beside the hotbar holding the held stack, the rest of the ring above it. */
        COLUMN
    }

    /**
     * Which way a ring turned.
     *
     * <p>{@code FORWARD} brings the next stack of the ring ({@code ring.get(current + 1)})
     * into the hand: a row's stacks slide right to left, a column's slide down toward the
     * hotbar. {@code BACKWARD} is the mirror.
     */
    public enum Direction {
        FORWARD,
        BACKWARD
    }

    /**
     * What a mod registers: the ring it would draw for a hotbar slot, if any.
     *
     * <p>The one method is also the source's on/off switch. Return {@code null} unless
     * the feature is enabled, its HUD option is on, and the slot really has a ring;
     * the strip draws nothing for a slot no source claims. A ring of fewer than two
     * stacks draws nothing either.
     */
    @FunctionalInterface
    public interface Source {

        /**
         * The ring to draw for {@code hotbarSlot} (0 to 8), or {@code null} for none.
         * Called every frame the HUD draws while the slot is selected, so keep it cheap.
         */
        @Nullable View viewFor(int hotbarSlot);

        /** Which way this source's ring reads, alone or in the cross. Default {@link Axis#ROW}. */
        default Axis axis() {
            return Axis.ROW;
        }
    }

    /**
     * One ring, as a source sees it now.
     *
     * <p>{@code ring} lists the stacks in the order a {@link Direction#FORWARD} turn
     * brings them to the hand, and {@code current} is the index of the held one: after
     * one forward turn, {@code ring.get(current + 1)} is in the hand. The ring wraps, so
     * any {@code current} is taken modulo the ring's size.
     *
     * <p>Drawn left to right, a row reads previous, held, next, the one after, and so
     * on (two stacks read held, other). A column reads top to bottom from the stack
     * furthest from the hand down to the held stack on the hotbar's row, so a column
     * of the inventory looks the way it does in the inventory.
     *
     * <p>A mod with its stacks in top-to-bottom order, held last, passes
     * {@code new View(topToBottom.reversed(), 0)}.
     *
     * @param ring    the stacks, never null; use {@link ItemStack#EMPTY} for an empty
     *                member. Copied.
     * @param current index of the held stack, wrapped into the ring
     */
    public record View(List<ItemStack> ring, int current) {
        public View {
            ring = List.copyOf(ring);
            current = ring.isEmpty() ? 0 : Math.floorMod(current, ring.size());
        }
    }

    /**
     * Registers a source. Registering the same instance twice does nothing.
     *
     * @throws IllegalStateException after MenuKit's declarations froze at client start
     */
    public static void register(Source source) {
        Declarations.requireOpen("HudCycleStrip.register");
        CycleStrip.register(source);
    }

    /**
     * Slides {@code source}'s ring on {@code hotbarSlot} one step in {@code direction}.
     * Call it right after the ring turned, once the source's view already shows the new
     * order. A slide started while one is running starts over from the new order.
     */
    public static void slide(Source source, int hotbarSlot, Direction direction) {
        CycleStrip.slide(source, hotbarSlot, direction);
    }

    /**
     * Every registered source's view of {@code hotbarSlot} right now, for a later
     * {@link #slideChanged}. A source with no ring there maps to {@code null}, so a
     * ring appearing or vanishing counts as a change.
     */
    public static Map<Source, @Nullable View> snapshot(int hotbarSlot) {
        return CycleStrip.snapshot(hotbarSlot);
    }

    /**
     * Slides the held cell of every ring on {@code hotbarSlot} whose contents differ
     * from {@code before} (a {@link #snapshot} taken before the change). The rest of
     * each strip stays still: this is for a change that moved the held stack, not the
     * ring. A ring whose contents did not change does not move.
     */
    public static void slideChanged(int hotbarSlot, Direction direction, Map<Source, @Nullable View> before) {
        CycleStrip.slideChanged(hotbarSlot, direction, before);
    }

    /**
     * A slide's start, from {@link #begin}. For a mod that draws its own slide (in the
     * vanilla hotbar, say) and wants it to move with the strip: hold one (a field is
     * fine) and pass it to {@link #slideProgress(SlideStart)} / {@link #slideDone(SlideStart)}.
     * It carries its own clock, so there is no raw millis value for a caller to stamp
     * with the wrong one, vanilla's {@code System.currentTimeMillis()} included.
     */
    public static final class SlideStart {
        private final long startMillis;

        private SlideStart(long startMillis) {
            this.startMillis = startMillis;
        }
    }

    /** Starts timing a slide now. */
    public static SlideStart begin() {
        return new SlideStart(Util.getMillis());
    }

    /**
     * The strip's eased progress since {@code slide} began: 0 at the start, exactly 1
     * at rest, with the spring's overshoot to about 1.1 just before it settles.
     */
    public static float slideProgress(SlideStart slide) {
        return CycleStrip.progress(slide.startMillis, Util.getMillis());
    }

    /** Whether {@code slide} has finished. */
    public static boolean slideDone(SlideStart slide) {
        return CycleStrip.done(slide.startMillis, Util.getMillis());
    }

    /**
     * @deprecated {@code startMillis} must be {@link Util#getMillis()}; a value from
     * another clock (vanilla's {@code System.currentTimeMillis()}, for one) silently
     * never reports done, since the two clocks measure from a different zero. Use
     * {@link #begin} and {@link #slideProgress(SlideStart)} instead, which cannot take
     * the wrong clock. Kept through MenuKit 7.x; removed in 8.0.0.
     */
    @Deprecated(since = "7.3.0", forRemoval = true)
    public static float slideProgress(long startMillis) {
        return CycleStrip.progress(startMillis, Util.getMillis());
    }

    /**
     * @deprecated see {@link #slideProgress(long)}; use {@link #begin} and
     * {@link #slideDone(SlideStart)} instead.
     */
    @Deprecated(since = "7.3.0", forRemoval = true)
    public static boolean slideDone(long startMillis) {
        return CycleStrip.done(startMillis, Util.getMillis());
    }
}
