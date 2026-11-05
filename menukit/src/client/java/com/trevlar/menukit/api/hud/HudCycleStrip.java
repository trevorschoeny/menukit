package com.trevlar.menukit.api.hud;

import com.trevlar.menukit.api.Declarations;
import com.trevlar.menukit.api.element.Easing;
import com.trevlar.menukit.api.element.ItemAnimation;
import com.trevlar.menukit.core.CycleStrip;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

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
 * <h3>Arcs</h3>
 *
 * An {@link Axis#ARC} ring (7.6.0) is drawn over the hotbar slot itself: its members sit
 * as small icons in an arc just above the slot, on every hotbar slot that has a ring, not
 * only the selected one. A turn moves each stack in a straight line to the next point
 * (arc to arc, arc to slot, slot to arc), growing or shrinking as it goes. Screens draw
 * the same arc with {@link #drawArc}. A mod that offers both looks registers two sources,
 * one per axis, and slides both. How an arc looks and moves is the source's
 * {@link Style}: where its members rest, their size, their shadow and the slide.
 *
 * <h3>The slide</h3>
 *
 * The strip slides only when told to. After a source's ring turns, call
 * {@link #slide}; the strip animates from where each stack was to where the view now
 * puts it, over the source's {@link Style#slideMillis()} on its {@link Style#easing()}: by default
 * {@link #SLIDE_MILLIS} on a damped spring ({@link Easing#SPRING}). A ring that changes
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

    /** How long one slide takes by default ({@link Style#DEFAULT}), in milliseconds. */
    public static final long SLIDE_MILLIS = CycleStrip.SLIDE_MILLIS;

    /** Which way a ring reads on screen. */
    public enum Axis {
        /** A mini hotbar beside the vanilla one, at the same height. */
        ROW,
        /** One cell beside the hotbar holding the held stack, the rest of the ring above it. */
        COLUMN,
        /**
         * The ring's members as small icons, each over a faint shadow of its own shape, in
         * an arc just above the hotbar slot, member 0 (the next one a forward turn brings in)
         * on the left by default (7.6.0). Drawn over every hotbar slot with a ring, for a ring
         * whose member count the source's {@link Style} has points for (one to three by
         * default, so rings of two to four stacks); any other ring draws no arc. Not part of
         * the cross.
         */
        ARC
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
         * Called every frame the HUD draws while the slot is selected, and for an
         * {@link Axis#ARC} source for every hotbar slot, so keep it cheap.
         */
        @Nullable View viewFor(int hotbarSlot);

        /** Which way this source's ring reads, alone or in the cross. Default {@link Axis#ROW}. */
        default Axis axis() {
            return Axis.ROW;
        }

        /**
         * How this source's ring looks and moves (7.6.0). Default {@link Style#DEFAULT}. Read
         * when a slide starts (the slide keeps that timing to its end) and every frame its arc
         * draws, so return the same instance rather than building one per call.
         */
        default Style style() {
            return Style.DEFAULT;
        }
    }

    /**
     * How a source's ring looks and moves (7.6.0), from {@link Source#style()}. The slide's
     * length and easing time every slide of the source, row, column or arc; the rest shapes
     * its arc. Immutable. {@link #builder()} starts from {@link #DEFAULT}, so set only what
     * differs:
     * <pre>{@code
     * static final HudCycleStrip.Style LOOK = HudCycleStrip.Style.builder()
     *         .slide(400, Easing.EASE_OUT)
     *         .shadowOpacity(0.5f)
     *         .build();
     * }</pre>
     */
    public static final class Style {

        /**
         * MenuKit's look: icons at 2/5 size (6.4 px) in a shallow arc that stays inside the
         * slot's 20 px pitch, each over its own shape in black at 3/8 opacity, one icon pixel
         * down and right, fading out as the icon grows into the slot; 300 ms slides on
         * {@link Easing#SPRING}.
         */
        public static final Style DEFAULT = new Builder().build();

        private final Map<Integer, List<ItemAnimation.Pose>> points;
        private final float iconScale;
        private final boolean shadow;
        private final float shadowOffset;
        private final float shadowOpacity;
        private final boolean shadowFades;
        private final long slideMillis;
        private final Easing easing;

        private Style(Builder b) {
            this.iconScale = b.iconScale;
            // Resting poses are built here, once, at the final icon scale.
            Map<Integer, List<ItemAnimation.Pose>> built = new TreeMap<>();
            b.points.forEach((members, xy) -> {
                ItemAnimation.Pose[] poses = new ItemAnimation.Pose[members];
                for (int i = 0; i < members; i++) poses[i] = new ItemAnimation.Pose(iconScale, xy[2 * i], xy[2 * i + 1]);
                built.put(members, List.of(poses));
            });
            this.points = Map.copyOf(built);
            this.shadow = b.shadow;
            this.shadowOffset = b.shadowOffset;
            this.shadowOpacity = b.shadowOpacity;
            this.shadowFades = b.shadowFades;
            this.slideMillis = b.slideMillis;
            this.easing = b.easing;
        }

        /** A builder holding {@link #DEFAULT}'s values. */
        public static Builder builder() {
            return new Builder();
        }

        /** A builder holding this style's values. */
        public Builder toBuilder() {
            return new Builder(this);
        }

        /**
         * Where an arc of {@code members} members rests, member 0 first, at
         * {@link #iconScale()}; poses as in {@link ArcItem}. Empty for a count this style
         * has no points for: a ring of that many members plus the held one draws no arc.
         */
        public List<ItemAnimation.Pose> points(int members) {
            return points.getOrDefault(members, List.of());
        }

        /** How big an arc's icons draw at rest, as a fraction of the 16 px item. */
        public float iconScale() { return iconScale; }

        /** Whether an icon draws over a shadow of its own shape. */
        public boolean shadow() { return shadow; }

        /** How far the shadow sits down and right, in the icon's own pixels (16 to the item). */
        public float shadowOffset() { return shadowOffset; }

        /** The shadow's opacity, 0 to 1, at full strength. */
        public float shadowOpacity() { return shadowOpacity; }

        /** Whether the shadow fades out as an icon grows from {@link #iconScale()} to the slot's size. */
        public boolean shadowFades() { return shadowFades; }

        /** How long one slide takes, in milliseconds. */
        public long slideMillis() { return slideMillis; }

        /** The slide's easing curve. */
        public Easing easing() { return easing; }

        /** Builds a {@link Style}; each setter returns the builder. */
        public static final class Builder {
            private final Map<Integer, float[]> points = new TreeMap<>();
            private float iconScale;
            private boolean shadow;
            private float shadowOffset;
            private float shadowOpacity;
            private boolean shadowFades;
            private long slideMillis;
            private Easing easing;

            private Builder() {
                // MenuKit's defaults. The 6.4 px icons sit 6.4 or 7.2 px apart, so they touch
                // but never overlap, and a three-member arc stays inside the slot's 20 px pitch.
                // The hotbar's top edge is at y = -11 and the XP bar's bottom at y = -13.
                points.put(1, new float[] {0f, -9.5f});
                points.put(2, new float[] {-3.6f, -9f, 3.6f, -9f});
                points.put(3, new float[] {-6.4f, -8f, 0f, -9.5f, 6.4f, -8f});
                iconScale = 0.4f;
                shadow = true;
                shadowOffset = 1f;
                shadowOpacity = 0.375f;
                shadowFades = true;
                slideMillis = CycleStrip.SLIDE_MILLIS;
                easing = Easing.SPRING;
            }

            private Builder(Style s) {
                s.points.forEach((members, poses) -> {
                    float[] xy = new float[2 * members];
                    for (int i = 0; i < members; i++) {
                        xy[2 * i] = poses.get(i).offsetX();
                        xy[2 * i + 1] = poses.get(i).offsetY();
                    }
                    points.put(members, xy);
                });
                iconScale = s.iconScale;
                shadow = s.shadow;
                shadowOffset = s.shadowOffset;
                shadowOpacity = s.shadowOpacity;
                shadowFades = s.shadowFades;
                slideMillis = s.slideMillis;
                easing = s.easing;
            }

            /**
             * Where an arc of {@code members} members rests: {@code xy} is x, y for member 0,
             * then member 1, and so on, in GUI px from the held item's center (negative y is
             * up). Any count from 1 is allowed; a count with no points draws no arc. No
             * {@code xy} at all removes that count's points.
             *
             * @throws IllegalArgumentException if {@code members < 1}, or {@code xy} is
             *         neither empty nor two values per member
             */
            public Builder points(int members, float... xy) {
                if (members < 1) throw new IllegalArgumentException("members must be at least 1: " + members);
                if (xy.length == 0) {
                    points.remove(members);
                } else if (xy.length != 2 * members) {
                    throw new IllegalArgumentException(members + " members need " + 2 * members + " values, got " + xy.length);
                } else {
                    points.put(members, xy.clone());
                }
                return this;
            }

            /** @throws IllegalArgumentException unless {@code scale} is above 0 */
            public Builder iconScale(float scale) {
                if (!(scale > 0f)) throw new IllegalArgumentException("iconScale must be above 0: " + scale);
                this.iconScale = scale;
                return this;
            }

            public Builder shadow(boolean on) {
                this.shadow = on;
                return this;
            }

            public Builder shadowOffset(float iconPixels) {
                this.shadowOffset = iconPixels;
                return this;
            }

            /** @throws IllegalArgumentException unless {@code opacity} is 0 to 1 */
            public Builder shadowOpacity(float opacity) {
                if (!(opacity >= 0f && opacity <= 1f)) throw new IllegalArgumentException("shadowOpacity must be 0 to 1: " + opacity);
                this.shadowOpacity = opacity;
                return this;
            }

            public Builder shadowFades(boolean fades) {
                this.shadowFades = fades;
                return this;
            }

            /**
             * The slide's length and curve. Any {@link Easing} with {@code f(0) = 0} and
             * {@code f(1) = 1}; one that goes past 1 overshoots its point and comes back.
             *
             * @throws IllegalArgumentException unless {@code millis} is above 0
             */
            public Builder slide(long millis, Easing easing) {
                if (millis <= 0) throw new IllegalArgumentException("slide millis must be above 0: " + millis);
                this.slideMillis = millis;
                this.easing = Objects.requireNonNull(easing, "easing");
                return this;
            }

            public Style build() {
                return new Style(this);
            }
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
     * One stack of an arc as it draws this frame (7.6.0). {@code pose} is relative to the
     * center of the held item's 16 px box, in GUI px: {@link ItemAnimation.Pose#NONE} is
     * the stack in the slot, a member at rest is at its style's {@link Style#iconScale()}
     * above it.
     */
    public record ArcItem(ItemStack stack, ItemAnimation.Pose pose) {}

    /**
     * {@code source}'s arc on {@code hotbarSlot} this frame, the held stack first and then
     * member 0, 1, 2, each where it is in a running turn. Empty when the source has no
     * ring there that fits an arc. For a mod that moves the arc's stacks somewhere itself
     * (into a row of slots, say): start from these poses, and draw with {@link #drawArcItem}
     * and the source's {@link Source#style()}.
     */
    public static List<ArcItem> arc(Source source, int hotbarSlot) {
        View view = source.viewFor(hotbarSlot);
        return view == null ? List.of() : CycleStrip.arcItems(source, view, hotbarSlot);
    }

    /**
     * Draws {@code source}'s arc on {@code hotbarSlot} around a held item centered at
     * {@code (centerX, centerY)}, turn included: what the HUD draws over that hotbar slot,
     * for a screen to draw over the slot's place on it. For looking only; it takes no input.
     *
     * <p>It draws the held stack too, last, so it sits over the icons that reach into the
     * slot: at rest at its full size with its count and durability, over the slot's own
     * (which looks the same); during a turn, moving into and out of the slot. The HUD hides
     * the hotbar slot's own stack where an arc stands; a screen's slot keeps drawing its own.
     */
    public static void drawArc(GuiGraphicsExtractor graphics, Source source, int hotbarSlot,
                               float centerX, float centerY) {
        View view = source.viewFor(hotbarSlot);
        if (view != null) CycleStrip.renderArc(graphics, Minecraft.getInstance().font, source, view, hotbarSlot, centerX, centerY);
    }

    /**
     * Draws {@code stack} centered at {@code (centerX, centerY)}, {@code scale} times its
     * 16 px size, without its count, in {@code style}'s look: an arc's icon at
     * {@link Style#iconScale()}, or anywhere between that and 1 while it moves. Its shadow
     * comes with it (the stack's own shape, dark and faint, offset down and right, like a
     * text shadow), full at the icon scale and, if the style fades it, gone at 1, so a
     * stack flown into a slot lands with none.
     */
    public static void drawArcItem(GuiGraphicsExtractor graphics, Style style, ItemStack stack,
                                   float centerX, float centerY, float scale) {
        CycleStrip.renderArcItem(graphics, style, stack, centerX, centerY, scale);
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
     * The default style's eased progress since {@code slide} began: 0 at the start, exactly
     * 1 at rest, on {@link Easing#SPRING}: past 1 by about 9%, back under by about 3%, then
     * settled. For a source with its own {@link Style}, use {@link #slideProgress(SlideStart, Style)}.
     */
    public static float slideProgress(SlideStart slide) {
        return slideProgress(slide, Style.DEFAULT);
    }

    /**
     * {@code style}'s eased progress since {@code slide} began (7.6.0): 0 at the start,
     * exactly 1 once its {@link Style#slideMillis()} are up, on its {@link Style#easing()}.
     * Pass the source's own style to move with its strip.
     */
    public static float slideProgress(SlideStart slide, Style style) {
        return CycleStrip.progress(slide.startMillis, Util.getMillis(), style);
    }

    /** Whether {@code slide} has finished at the default style's length. */
    public static boolean slideDone(SlideStart slide) {
        return slideDone(slide, Style.DEFAULT);
    }

    /** Whether {@code slide} has finished at {@code style}'s length (7.6.0). */
    public static boolean slideDone(SlideStart slide, Style style) {
        return CycleStrip.done(slide.startMillis, Util.getMillis(), style);
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
        return CycleStrip.progress(startMillis, Util.getMillis(), Style.DEFAULT);
    }

    /**
     * @deprecated see {@link #slideProgress(long)}; use {@link #begin} and
     * {@link #slideDone(SlideStart)} instead.
     */
    @Deprecated(since = "7.3.0", forRemoval = true)
    public static boolean slideDone(long startMillis) {
        return CycleStrip.done(startMillis, Util.getMillis(), Style.DEFAULT);
    }
}
