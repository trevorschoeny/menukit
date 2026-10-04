package com.trevlar.menukit.core;

import com.trevlar.menukit.api.panel.ScreenKind;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;

/**
 * MenuKit's one scroll model and its one scrollbar: what a {@code ScrollContainer}
 * (the text body's scroller) and a {@code Tabs} strip (the sidebar column, and the
 * side-scroll row across the top) both scroll by. Before 6.1.0's rework the strip
 * had its own copy: it stepped a whole tab per wheel notch and drew vanilla's list
 * scrollbar with a thumb sized to the share in view, which on a column only a tab
 * or two too tall came out nearly as tall as the track (Trev, 2026-10-03: "the
 * scroll bar is super tall"). One model means one feel and one look.
 *
 * <h3>The model</h3>
 *
 * An offset in pixels from the start of the content, along whichever axis the
 * owner scrolls (down for a body or the sidebar, across for the side-scroll row).
 * Pure functions of the offset, the content's length and the viewport's length;
 * the owner keeps the offset (its own view state, or the consumer's through a
 * lens) and calls these to move it:
 * <ul>
 *   <li>{@link #clamp}: never before the start, never past the point where the
 *       content's end meets the viewport's end.</li>
 *   <li>{@link #wheel}: {@link #PIXELS_PER_NOTCH} pixels per notch, immediately,
 *       so the wheel moves in small even steps, never a whole tab.</li>
 *   <li>{@link #intoView}: the least movement that shows a span whole (a newly
 *       selected tab).</li>
 *   <li>{@link #glide}: one frame of easing from the offset shown toward a target
 *       offset, for a jump the player did not drag (an arrow, a tab selected off
 *       the strip), so it slides rather than snaps.</li>
 * </ul>
 *
 * <h3>The scrollbar</h3>
 *
 * Always vertical (the side-scroll row has arrows, not a bar). A track the
 * viewport's length and a fixed-size thumb, {@link #THUMB_LENGTH} long whatever
 * the content's length, as vanilla's creative inventory: small, easy to see, and
 * the same on every scroller. It travels the track less {@link #TRACK_PADDING} at
 * each end. A press on the thumb grabs it where pressed; a press on the track
 * jumps so the thumb centres on the press and keeps holding it, as vanilla's lists
 * and creative inventory do ({@link #grab}, {@link #dragTo}). {@link #draw} paints
 * it for the kind of screen: MenuKit's inset track and vanilla's creative scroller
 * on a container screen, vanilla's thin list scroller on an options screen.
 */
@ApiStatus.Internal
public final class Scroller {

    private Scroller() {}

    /** Width of the thumb sprite on a container screen: vanilla's creative-inventory scroller. */
    public static final int THUMB_WIDTH = 12;
    /** Length of the thumb, on both kinds of screen: vanilla's creative scroller's, fixed. */
    public static final int THUMB_LENGTH = 15;
    /** Room inside the track at each end (and side) of the thumb. */
    public static final int TRACK_PADDING = 1;
    /** Width of the track on a container screen: the thumb and its padding. */
    public static final int TRACK_WIDTH = THUMB_WIDTH + 2 * TRACK_PADDING;
    /** Width of the track on an options screen: vanilla's list scrollbar ({@code AbstractScrollArea.SCROLLBAR_WIDTH}). */
    public static final int OPTIONS_TRACK_WIDTH = 6;
    /** Pixels moved per wheel notch. */
    public static final int PIXELS_PER_NOTCH = 10;
    /**
     * The glide's time constant, in milliseconds: each 40 ms covers about 63% of
     * what is left, so a tab-sized jump settles in roughly a fifth of a second.
     */
    public static final double GLIDE_MILLIS = 40;

    // Container screen: the creative inventory's scroller in MenuKit's inset track.
    private static final Identifier THUMB = Identifier.withDefaultNamespace("container/creative_inventory/scroller");
    private static final Identifier THUMB_DISABLED =
            Identifier.withDefaultNamespace("container/creative_inventory/scroller_disabled");
    // Options screen: vanilla's list scrollbar (AbstractScrollArea, 26.2).
    private static final Identifier LIST_THUMB = Identifier.withDefaultNamespace("widget/scroller");
    private static final Identifier LIST_TRACK = Identifier.withDefaultNamespace("widget/scroller_background");

    // ── The model ──────────────────────────────────────────────────────────

    /** How far the offset can go: the content's overhang, or 0 when it fits. */
    public static int maxScroll(int content, int view) {
        return Math.max(0, content - view);
    }

    /** The offset kept inside {@code [0, maxScroll]}. */
    public static double clamp(double offset, int content, int view) {
        return Math.max(0.0, Math.min(maxScroll(content, view), offset));
    }

    /**
     * After {@code notches} of the wheel (positive = up, toward the start, as the
     * game reports it): {@link #PIXELS_PER_NOTCH} a notch, clamped.
     */
    public static double wheel(double offset, double notches, int content, int view) {
        return clamp(offset - notches * PIXELS_PER_NOTCH, content, view);
    }

    /**
     * The offset that shows the span {@code [start, start + length)} whole with
     * the least movement from {@code offset}: unchanged if it already shows,
     * else its near edge brought to the viewport's near edge or its far edge to
     * the far edge.
     */
    public static double intoView(double offset, int start, int length, int content, int view) {
        double to = offset;
        if (start < offset) to = start;
        else if (start + length > offset + view) to = start + length - view;
        return clamp(to, content, view);
    }

    /**
     * One frame of the glide: {@code shown} eased toward {@code target} over
     * {@code elapsedMillis}, exponentially (so the speed does not depend on the
     * frame rate), landing exactly once within half a pixel. Never overshoots.
     */
    public static double glide(double shown, double target, double elapsedMillis) {
        double gap = target - shown;
        if (Math.abs(gap) < 0.5) return target;      // close enough: land
        if (elapsedMillis <= 0) return shown;          // no time passed (two draws in one frame)
        return shown + gap * (1 - Math.exp(-elapsedMillis / GLIDE_MILLIS));
    }

    // ── The scrollbar's geometry, along a track {@code track} pixels long ────

    /** How far the thumb's top can move: the track less the thumb and the padding at both ends. */
    public static int thumbTravel(int track) {
        return Math.max(0, track - THUMB_LENGTH - 2 * TRACK_PADDING);
    }

    /**
     * The thumb's top from the track's top, exact (draw it at the floor): at the
     * padding when nothing scrolls, else as far down its travel as the offset is
     * down its range.
     */
    public static double thumbTop(double offset, int content, int view, int track) {
        int max = maxScroll(content, view);
        int travel = thumbTravel(track);
        if (max == 0 || travel == 0) return TRACK_PADDING;
        return TRACK_PADDING + clamp(offset, content, view) / max * travel;
    }

    /**
     * A press {@code along} pixels down the track: where on the thumb it holds.
     * On the thumb, where it was pressed, so the thumb does not move; on the
     * track, the thumb's middle, so the thumb jumps to centre on the press.
     */
    public static double grab(double along, double thumbTop) {
        boolean onThumb = along >= thumbTop && along < thumbTop + THUMB_LENGTH;
        return onThumb ? along - thumbTop : THUMB_LENGTH / 2.0;
    }

    /** The offset that puts the thumb, held {@code grab} below its top, under the mouse {@code along} the track. */
    public static double dragTo(double along, double grab, int content, int view, int track) {
        int travel = thumbTravel(track);
        if (travel == 0) return 0;
        double fraction = Math.max(0.0, Math.min(1.0, (along - grab - TRACK_PADDING) / travel));
        return fraction * maxScroll(content, view);
    }

    // ── Drawing ────────────────────────────────────────────────────────────

    /** The track's width on a screen of {@code kind}. */
    public static int trackWidth(ScreenKind kind) {
        return kind == ScreenKind.OPTIONS ? OPTIONS_TRACK_WIDTH : TRACK_WIDTH;
    }

    /**
     * The scrollbar, its track's top-left at {@code (x, y)}, {@link #trackWidth}
     * wide and {@code track} long, the thumb {@code thumbTop} down it. On a
     * container screen MenuKit's inset track always shows, the thumb greyed when
     * nothing scrolls or the owner is disabled; on an options screen vanilla's
     * list scrollbar shows only while something scrolls, as vanilla's does.
     */
    public static void draw(GuiGraphicsExtractor g, ScreenKind kind, int x, int y, int track, double thumbTop,
                            boolean scrollable, boolean disabled) {
        int top = y + (int) thumbTop;
        if (kind == ScreenKind.OPTIONS) {
            if (!scrollable) return;
            g.blitSprite(RenderPipelines.GUI_TEXTURED, LIST_TRACK, x, y, OPTIONS_TRACK_WIDTH, track);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, LIST_THUMB, x, top, OPTIONS_TRACK_WIDTH, THUMB_LENGTH);
            return;
        }
        PanelRendering.renderInsetRect(g, x, y, TRACK_WIDTH, track);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, scrollable && !disabled ? THUMB : THUMB_DISABLED,
                x + TRACK_PADDING, top, THUMB_WIDTH, THUMB_LENGTH);
    }
}
