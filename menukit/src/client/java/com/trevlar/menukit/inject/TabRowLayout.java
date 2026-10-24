package com.trevlar.menukit.inject;

import com.trevlar.menukit.inject.TabRegistry.Group;
import com.trevlar.menukit.inject.TabRegistry.Tab;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A tab row laid out, pure (7.4.0): how a row fits its frame and pages, where each tab draws as
 * groups slide and tabs pin, what a point is on, and how a tab's sprite stretches. Nothing here
 * keeps state or reads the game; {@link ContainerTabRows} hands it this frame's row.
 *
 * <p><b>Groups</b> (Fluorite): the row is the header and, while the group is expanded, its
 * sub-tabs; that row is what pages, takes clicks and keeps a selected tab in view, as if every
 * group stood still. Only the drawing moves: each group's sub-tabs, one strip, are drawn as far
 * out from under the header as the group's slide has got ({@link #place}), clipped at the
 * header's left edge and drawn before it, so they come out from behind it; the tabs after the
 * strip follow its edge.
 *
 * <p><b>Pinned tabs</b> (Fluorite): a tab pinned this frame stops at whichever end of the row
 * paging would take it past, and the tabs on the far side of it go on behind it, until the next
 * group's header (at the left end) or the previous group's pinned tabs (at the right end) push
 * it off ({@link #pin}). Pinning moves only the drawing, as a slide does.
 */
@ApiStatus.Internal
public final class TabRowLayout {

    private TabRowLayout() {}

    /** The paging arrows: vanilla's recipe book page arrows, at the row's two ends. */
    public static final int ARROW_WIDTH = 12, ARROW_HEIGHT = 17;
    /** {@link #hit} answers: nothing, the back arrow, the forward arrow (a tab is its place in the row, 0 up). */
    public static final int NONE = -1, BACK = -2, FORWARD = -3;

    /**
     * A tab as this frame shows it: its callbacks read once, as the rows are chosen, so the
     * layout, the hit test and the drawing all see one answer ({@link ContainerTabRows#choose}).
     */
    public record Shown(Tab tab, boolean selected, boolean disabled, boolean pinned) {
        int width() { return tab.width(); }
        int height() { return tab.height(); }
        boolean sub() { return tab.sub(); }
        @Nullable Group group() { return tab.group(); }
    }

    /**
     * The frame edge a row stands on: from {@code x}, {@code width} across, at {@code y}, whether
     * the tabs stand up from it ({@code top}) or hang down, and how far its tallest tab stands
     * out ({@code height}).
     */
    public record Line(int x, int width, int y, boolean top, int height) {

        /** The row's top: a row above its edge ends there, one below starts there. */
        public int rowTop() { return top ? y - height : y; }
    }

    /**
     * How the row lays out: the first tab in view, how many are, and whether they are paged
     * (the arrows at the row's ends take room then).
     */
    public record Fit(int first, int shown, boolean paged) {}

    /**
     * One tab where it draws this frame: its left edge, its size, its place in the row ({@code -1}
     * for a closing group's sub-tab, which takes no click), for a sub-tab the left edge it shows
     * from, its header's, so it goes under the header as it slides back, and whether it is
     * {@link #pin pinned} at either end of the row, drawn over the tabs going on behind it.
     */
    public record Placed(Shown shown, int x, int width, int height, int index, boolean sub, int clipLeft, boolean stuck) {

        /** The tab placed. */
        public Tab tab() { return shown.tab(); }
    }

    /** How wide tabs {@code from} to {@code to} (exclusive) of a row of {@code widths} are, side by side. */
    public static int span(int[] widths, int from, int to) {
        int w = 0;
        for (int i = from; i < to; i++) w += widths[i];
        return w;
    }

    /** The room between the arrows on a {@code width} wide frame. */
    private static int between(int width) {
        return width - 2 * (ARROW_WIDTH + 1);
    }

    /**
     * The row of tabs {@code widths} wide on a {@code width} wide frame, from {@code first} on:
     * all of them where they fit, else paged, as many whole tabs as fit between the arrows (one
     * at least), {@code first} clamped so the last page is full.
     */
    public static Fit fit(int width, int[] widths, int first) {
        int n = widths.length;
        if (span(widths, 0, n) <= width) return new Fit(0, n, false);
        int room = between(width);
        // The last page full: the row starts no later than where its last tabs just fit.
        int last = n - 1;
        while (last > 0 && span(widths, last - 1, n) <= room) last--;
        int from = Math.max(0, Math.min(first, last));
        int shown = 1;
        while (from + shown < n && span(widths, from, from + shown + 1) <= room) shown++;
        return new Fit(from, shown, true);
    }

    /** The first tab in view that puts tab {@code at} last in view, on a {@code width} wide frame that pages. */
    static int endingAt(int width, int[] widths, int at) {
        int from = at;
        while (from > 0 && span(widths, from - 1, at + 1) <= between(width)) from--;
        return from;
    }

    /** The left and right ends of the room the tabs draw in on line {@code l}, {@code inset} in from each end: the arrows' room. */
    static int roomLeft(Line l, int inset) { return l.x() + inset; }
    static int roomRight(Line l, int inset) { return l.x() + l.width() - inset; }

    /** The room the tabs of a row laid out by {@code f} draw in on a {@code width} wide frame, settled. */
    static int room(int width, Fit f) {
        return f.paged() ? between(width) : width;
    }

    static int[] widths(Shown[] row) {
        int[] w = new int[row.length];
        for (int i = 0; i < row.length; i++) w[i] = row[i].width();
        return w;
    }

    /**
     * Where the tabs {@code drew} draw on line {@code l} this frame, the row {@code row} laid
     * out by {@code f} between ends {@code inset} in, each group open {@code open} of the way (by
     * its id; 1 where absent): the row's tabs in view, the sub-tabs of a group sliding, and the
     * tabs pinned at the room's ends. A group's sub-tabs move as one, out from under the
     * header to its right as it opens, and every tab after it moves with their edge. The row's
     * first tab in view stands at the room's left end however far the strips before it are out,
     * so a group opening or closing before the page doesn't move it. Settled, each tab not pinned
     * is where the row's fit puts it. A tab not pinned shows whole or not at all, as paging
     * shows them: one that a slide has pushed past the room's right end is left out until it is
     * back inside.
     */
    static List<Placed> place(Line l, Shown[] drew, Shown[] row, Fit f, Map<String, Float> open, int inset) {
        int n = drew.length;
        // Each tab's left as the row lays it out from 0, its place in the row, and whether it pins now.
        double[] x = new double[n];
        int[] index = new int[n];
        boolean[] pins = new boolean[n];
        double cursor = 0;
        int r = 0;
        for (int i = 0; i < n; ) {
            Shown t = drew[i];
            if (!t.sub()) {
                index[i] = r < row.length && row[r] == t ? r++ : -1;
                x[i] = cursor;
                pins[i] = index[i] >= 0 && t.pinned();
                cursor += t.width();
                i++;
                continue;
            }
            // A group's sub-tabs together: as wide as they are open, slid under the header by the rest.
            Group g = t.group();
            int end = i;
            double span = 0;
            while (end < n && drew[end].sub() && drew[end].group() == g) span += drew[end++].width();
            float p = open.getOrDefault(g.id(), 1f);
            double sx = cursor - (1f - p) * span;
            for (; i < end; i++) {
                Shown s = drew[i];
                index[i] = r < row.length && row[r] == s ? r++ : -1;
                x[i] = sx;
                // ponytail: a sliding group's sub-tabs move as one strip and pin once it settles, so one
                // that should pin then steps into its place at the slide's end; pin within the slide if that shows.
                pins[i] = index[i] >= 0 && p >= 1f && s.pinned();
                sx += s.width();
            }
            cursor += p * span;
        }
        int left = roomLeft(l, inset), right = roomRight(l, inset);
        // The row's first tab in view at the room's left end: what draws before it, strips only as far
        // out as they are, is off to the left, so a group opening or closing there leaves the page be.
        double shift = left;
        for (int i = 0; i < n; i++) {
            if (index[i] == f.first()) {
                shift = left - x[i];
                break;
            }
        }
        for (int i = 0; i < n; i++) x[i] += shift;
        cursor += shift;
        boolean[] stuck = pin(drew, x, pins, left, right, cursor);
        List<Placed> out = new ArrayList<>();
        int headerX = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            Shown t = drew[i];
            int at = (int) Math.round(x[i]);
            if (!t.sub()) headerX = at;
            // Pinned, drawn while any of it is in the room; in the row, drawn while in view;
            // closing, drawn until it is back under the header.
            // A tab shows whole or not at all: one a slide has pushed past the room's end is left out.
            boolean whole = at + t.width() <= right && (t.sub() || at >= left);
            boolean shown = stuck[i] ? at + t.width() > left && at < right
                    : whole && (index[i] >= 0 ? index[i] >= f.first() && index[i] < f.first() + f.shown()
                    : open.getOrDefault(t.group().id(), 1f) > 0f);
            // A pinned sub-tab isn't clipped at its header: at the right end its header can be behind it.
            int clip = t.sub() && !stuck[i] ? headerX : Integer.MIN_VALUE;
            if (shown) out.add(new Placed(t, at, t.width(), t.height(), index[i], t.sub(), clip, stuck[i]));
        }
        return out;
    }

    /**
     * Pins tabs: of {@code tabs}, laid out from lefts {@code x}, those that {@code pins} stop at
     * {@code left} or {@code right} as they reach it, and the tabs beyond them go on behind. A
     * run is a tab and the sub-tabs of its group after it (a tab outside any group is a run of
     * its own). At the left end a run's pinned tabs stack from {@code left} in its order, and
     * its end, the next run's left or {@code end} for the last, pushes the stack off to the left
     * as it arrives, so a pinned sub-tab leaves with its group's header. At the right end a
     * group's pinned tabs stack up to {@code right}, still in row order, and the tabs before
     * them go on behind, other groups' too: a header leads its group, so the previous group's
     * last tab is beside it and can't be what pushes it. The previous group's pinned tabs push
     * the stack off to the right instead, as they reach the right end to stand there in its
     * place. A tab outside any group doesn't pin at the right end, as at the left. Moves each
     * pinned tab's {@code x} to where it stands; whether each one is stuck there, its own place
     * past where the stack has it.
     */
    static boolean[] pin(Shown[] tabs, double[] x, boolean[] pins, double left, double right, double end) {
        boolean[] stuck = new boolean[tabs.length];
        // The right edge of the last pinned tab so far, where the row lays it out: what pushes
        // the next group's stack off the right end.
        double pusher = Double.NEGATIVE_INFINITY;
        for (int from = 0; from < tabs.length; ) {
            int to = from + 1;
            while (to < tabs.length && tabs[to].sub() && tabs[to].group() == tabs[from].group()) to++;
            double runEnd = to < tabs.length ? x[to] : end;
            boolean grouped = tabs[from].group() != null;
            // How wide the run's pinned tabs before this one are, and this one and those after it.
            double before = 0, rest = 0, lastEnd = pusher;
            for (int i = from; i < to; i++) if (pins[i]) rest += tabs[i].width();
            for (int i = from; i < to; i++) {
                if (!pins[i]) continue;
                // Its place in each end's stack, and where what pushes the stack off has it.
                double atLeft = left + before, leftPushed = runEnd - rest;
                double atRight = right - rest, rightPushed = pusher + before;
                lastEnd = x[i] + tabs[i].width();
                before += tabs[i].width();
                rest -= tabs[i].width();
                if (x[i] < atLeft) {
                    stuck[i] = true;
                    x[i] = Math.min(atLeft, leftPushed);
                } else if (grouped && x[i] > atRight) {
                    stuck[i] = true;
                    x[i] = Math.max(atRight, rightPushed);
                }
            }
            pusher = lastEnd;
            from = to;
        }
        return stuck;
    }

    /**
     * Whether tab {@code at} of {@code row}, laid out from {@code first} in a room {@code room}
     * wide, is behind a pinned tab, wholly or in part: -1 behind one at the left end, 1 at the
     * right, 0 not. A tab pinned itself is never behind one.
     */
    static int covered(Shown[] row, int first, int at, int room) {
        int[] widths = widths(row);
        double[] x = new double[row.length];
        boolean[] pins = new boolean[row.length];
        // From the room's left end, 0; tabs before the first are left of it.
        double cursor = -span(widths, 0, first);
        for (int i = 0; i < row.length; i++) {
            x[i] = cursor;
            cursor += widths[i];
            pins[i] = row[i].pinned();
        }
        double from = x[at], to = from + widths[at];
        boolean[] stuck = pin(row, x, pins, 0, room, cursor);
        if (stuck[at]) return 0;
        for (int i = 0; i < row.length; i++) {
            if (stuck[i] && x[i] < to && x[i] + widths[i] > from) return i < at ? -1 : 1;
        }
        return 0;
    }

    /**
     * What is at {@code (mx, my)} on line {@code l}, the row of {@code count} tabs laid out by
     * {@code f} and drawn at {@code placed}: a tab (its place in the row), an arrow there is one
     * for, or {@link #NONE}. A tab runs from the frame to its height out, and shows only
     * between the arrows; a pinned tab is over the rest, a header over its sub-tabs, which show
     * only right of it. Tabs touch, each from its left edge up to the next one's, so a point is
     * on one tab only.
     */
    static int hit(Line l, int count, Fit f, int inset, List<Placed> placed, double mx, double my) {
        if (mx < l.x() || mx >= l.x() + l.width()) return NONE;
        if (f.paged() && my >= l.rowTop() && my < l.rowTop() + l.height()) {
            if (mx < l.x() + ARROW_WIDTH) return f.first() > 0 ? BACK : NONE;
            if (mx >= l.x() + l.width() - ARROW_WIDTH) return f.first() + f.shown() < count ? FORWARD : NONE;
        }
        int left = roomLeft(l, inset), right = roomRight(l, inset);
        for (int layer = 2; layer >= 0; layer--) {
            for (Placed p : placed) {
                if (layer(p) != layer || p.index() < 0) continue;
                int from = Math.max(left, Math.max(p.x(), p.clipLeft())), to = Math.min(right, p.x() + p.width());
                int near = l.top() ? l.y() - p.height() : l.y();
                if (mx >= from && mx < to && my >= near && my < near + p.height()) return p.index();
            }
        }
        return NONE;
    }

    /** Which tabs draw over which, from the bottom: sub-tabs, then the other tabs, then pinned ones. */
    static int layer(Placed p) {
        return p.stuck() ? 2 : p.sub() ? 0 : 1;
    }

    /**
     * The sprite runs that draw source span {@code [from, to)} {@code length} long: its first
     * {@code head} and last {@code tail} kept whole at the two ends, the plain run between them
     * repeated or cut to fill. Each is {source start, offset in the length, run}; runs can be
     * empty.
     */
    public static List<int[]> runs(int from, int to, int head, int tail, int length) {
        List<int[]> out = new ArrayList<>();
        out.add(new int[]{from, 0, head});
        int plain = to - from - head - tail, fill = length - head - tail;
        for (int d = 0; d < fill; d += plain) out.add(new int[]{from + head, head + d, Math.min(plain, fill - d)});
        out.add(new int[]{to - tail, length - tail, tail});
        return out;
    }
}
