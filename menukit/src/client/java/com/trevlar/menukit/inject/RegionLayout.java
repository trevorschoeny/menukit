package com.trevlar.menukit.inject;

import com.trevlar.menukit.api.element.ScrollContainer;
import com.trevlar.menukit.api.panel.Reference;
import com.trevlar.menukit.api.panel.RegionConstants;
import com.trevlar.menukit.core.Scroller;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/**
 * Where the container region's two panels go, pure (7.4.0; {@link ContainerRegions} is the
 * region itself): how the container list grows, centers and scrolls above the inventory
 * panel, how vanilla's own placements move with them, each section's band of the list, the
 * scroll an edge or a reveal asks for. Nothing here reads the game, so the checks ask it
 * directly. All in GUI pixels; the container list's numbers are from its own top-left.
 */
@ApiStatus.Internal
public final class RegionLayout {

    private RegionLayout() {}

    /** Room left above the container list at the screen's top, once it has grown up to it. */
    public static final int TOP_MARGIN = 4;
    /** Room left below the inventory panel at the screen's bottom, once the list has pushed it down to it. */
    public static final int BOTTOM_MARGIN = 4;
    /** From the list's left edge to its content: vanilla's grid frame (slot x 8, frame x 7). */
    public static final int INSET_X = 7;
    /** From the list's top edge to the viewport's: its frame's top border. */
    public static final int VIEW_TOP = 4;
    /** From the viewport's bottom to the list's bottom edge: its frame's bottom border, where scrolled content clips. */
    public static final int VIEW_BOTTOM = 4;
    /** The first section's top inside the content, so its title sits where vanilla's does (6 down). */
    public static final int CONTENT_TOP = 2;
    /** Below the last section, inside the content: with {@link #VIEW_BOTTOM}, vanilla's 7 px from a grid to the frame's edge. */
    public static final int CONTENT_BOTTOM = 3;
    /** Between sections: vanilla's room from a grid to the "Inventory" label below it. */
    public static final int GAP = 3;
    /** Between the two panels: MenuKit's gap between panels stacked on a menu. */
    public static final int PANEL_GAP = RegionConstants.MENU_STACK_GAP;
    /** From the inventory panel's top edge to vanilla's "Inventory" label: where a container's title sits in its frame. */
    public static final int INVENTORY_LABEL_TOP = 6;
    /** A section's width before the scrollbar: nine slots. */
    public static final int CONTENT_WIDTH = 9 * 18;
    /** The scrollbar lane at the right: the gutter and the track. */
    public static final int LANE = ScrollContainer.TRACK_WIDTH + ScrollContainer.SCROLLER_GUTTER;
    /** A slot's cell. */
    static final int CELL = 18;
    /**
     * How far a section's title goes down when the section is a block of its own: with its
     * band's top {@link #BAND_ABOVE} above the section, its title sits 3 px inside the block,
     * as vanilla's title sits 3 px inside its art's fill (fill from row 3, title at 6).
     */
    public static final int TITLE_INSET = 2;
    /**
     * How far above its content a section's band starts: the rest of the {@link #GAP} above
     * it is the band of the section above, so the grid above keeps 2 px of its own color
     * under it and this section's title sits inside its own.
     */
    public static final int BAND_ABOVE = 1;

    // Carrying an item to an edge.

    /** Inside the viewport, how far from an edge the scroll starts, slow, reaching full speed at the edge. */
    public static final int EDGE_IN = 8;
    /** Past an edge, how far it keeps scrolling at full speed (below: short of the inventory's first row). */
    public static final int EDGE_PAST = 12;
    /** Full speed, in pixels a second. */
    public static final double EDGE_SPEED = 120;

    // Tabs mode.

    /**
     * The tab row's height above the list's top edge in tabs mode: vanilla's creative top
     * tab stands 28 px above its frame, these 25, room for a full-size icon inside an unpicked
     * tab with two rows clear above and below it. A double chest with its tabs needs 245 px
     * (212 + 25 and the two margins), so in a 240 px window its grid scrolls by 5.
     */
    public static final int TAB_ROW = 25;

    /**
     * How the container list stands on its screen this frame: the numbers that differ
     * between list mode and tabs mode, as the inputs {@link #layout} takes, so the layout is
     * one rule for both.
     *
     * @param above             the tab row's height above the list's top edge, counted in the block (0: no row)
     * @param titled            whether the sections' titles show (the tab names the container instead)
     * @param contentTop        the room above the first section, inside the content
     * @param contentBottom     the room below the last section, inside the content
     * @param viewBottom        from the viewport's bottom to the list's bottom edge
     * @param panelGap          between the list's bottom edge and the inventory panel's top
     * @param inventoryLabelTop from the inventory panel's top edge to vanilla's "Inventory" label
     * @param shrink            how much shorter than the screen's own container part the list
     *                          may be: none in list mode, any in tabs mode
     * @param joined            whether the list and the inventory panel are one frame, one
     *                          surface as wide as the list
     */
    public record Shape(int above, boolean titled, int contentTop, int contentBottom, int viewBottom, int panelGap,
                        int inventoryLabelTop, int shrink, boolean joined) {

        /** List mode: every section shown, two panels a gap apart, never shorter than the screen's own part. */
        public static final Shape LIST = new Shape(0, true, CONTENT_TOP, CONTENT_BOTTOM, VIEW_BOTTOM, PANEL_GAP,
                INVENTORY_LABEL_TOP, 0, false);
        /**
         * Tabs mode with one container: no tab row, its title kept, the list and the inventory
         * one frame as tall as the container needs. Vanilla's own screen, to the pixel: the
         * grid ends where vanilla's does, {@link #GAP} above "Inventory", and the frame's line
         * between the two parts runs there.
         */
        public static final Shape TABS = new Shape(0, true, CONTENT_TOP, 0, 0, 0, GAP, Integer.MAX_VALUE, true);
        /**
         * Tabs mode with a tab row: the tab names the container, so there is no title row,
         * and the grid starts as far below the frame's top as it is in from its sides.
         */
        public static final Shape TAB_ROW = new Shape(RegionLayout.TAB_ROW, false, INSET_X - VIEW_TOP, 0, 0, 0, GAP,
                Integer.MAX_VALUE, true);
    }

    /**
     * Where the two panels are on a screen this frame.
     *
     * @param x          the container list's left edge (the screen's own {@code leftPos})
     * @param top        its top edge
     * @param width      its width: the screen's image width, as much more as the shown content
     *                   is wider than nine slots, and the scrollbar lane while it scrolls
     * @param height     its height, from {@code top} to {@code bottom}
     * @param bottom     its bottom edge, {@link #PANEL_GAP} above the inventory panel
     * @param viewTop    the viewport's top edge
     * @param viewHeight the viewport's height, down to {@link #VIEW_BOTTOM} above {@code bottom}
     * @param content    the height of everything in it
     * @param scrollable whether the content is taller than the viewport
     * @param inventory  the inventory panel: where vanilla put the inventory part, from
     *                   {@link #INVENTORY_LABEL_TOP} above its label to the image's bottom
     * @param bounds     the screen's bounds: the rectangle around both panels, and the tab row above them
     * @param shape      how the list stands this frame (list mode or tabs mode)
     */
    public record Geometry(int x, int top, int width, int height, int bottom, int viewTop, int viewHeight,
                           int content, boolean scrollable, Reference inventory, Reference bounds, Shape shape) {

        /** A list-mode geometry. */
        public Geometry(int x, int top, int width, int height, int bottom, int viewTop, int viewHeight,
                        int content, boolean scrollable, Reference inventory, Reference bounds) {
            this(x, top, width, height, bottom, viewTop, viewHeight, content, scrollable, inventory, bounds, Shape.LIST);
        }

        /** The viewport's bottom edge: where scrolled content clips. */
        public int viewBottom() { return viewTop + viewHeight; }

        /** The container list's rectangle. */
        public Reference list() { return new Reference(x, top, width, height); }

        /** The tab row's top edge: the list's top in list mode, which has none. */
        public int tabTop() { return top - shape.above(); }

        /** From the list's top edge to the inventory panel's bottom: the one frame's height when the two are {@link Shape#joined}. */
        public int frameHeight() { return inventory.topPos() + inventory.imageHeight() - top; }
    }

    /**
     * Grow first, then scroll, centered while it fits, from the screen's own bounds as vanilla
     * laid them out. The container list starts as the screen's own container part, its top at
     * vanilla's {@code topPos}, and grows with its content. The two panels are one block, and
     * the room left free around it is shared evenly above and below (to {@link #TOP_MARGIN}
     * and {@link #BOTTOM_MARGIN}), as far as each side has room:
     * <ul>
     *   <li>while the block fits centered, it is centered: each pixel the list grows moves
     *       its top up half a pixel and the inventory panel down half a pixel, rounded as
     *       vanilla rounds its own centering;</li>
     *   <li>where one side has less room than the other (a window too short for vanilla's
     *       own margins, a screen vanilla does not center), that side's panel holds and the
     *       other moves until the block is centered: the list's top holds and the inventory
     *       panel moves down, or the other way round. A side with no room at all never
     *       moves, so the other takes all the growth up to its margin;</li>
     *   <li>only when both margins are reached does the list <b>scroll</b>.</li>
     * </ul>
     * Each pixel of growth moves each panel by at most one pixel and never back, so a box
     * opening never makes the block jump, at any window height. With vanilla's image
     * centered the block stays centered right up to both margins at once.
     * Never shorter than the screen's own container part, and neither panel ever moves the
     * other way from where vanilla put it: on a window with no room (a tiny one) the list
     * keeps vanilla's height and scrolls.
     *
     * @param vanilla         the screen's bounds as vanilla computed them
     * @param inventoryLabelY vanilla's "Inventory" label, from vanilla's {@code topPos}
     * @param content         the content's height
     * @param screenHeight    the screen's height, for the room below the inventory panel
     */
    public static Geometry layout(Reference vanilla, int inventoryLabelY, int content, int screenHeight) {
        return layout(vanilla, inventoryLabelY, content, screenHeight, Shape.LIST);
    }

    /**
     * {@link #layout(Reference, int, int, int)} for a list standing as {@code shape} says. The
     * block is the tab row, the list and the inventory panel, and it grows or shrinks from
     * vanilla's image by as much as it is taller or shorter: in tabs mode a 3-row box on a
     * double chest's screen makes it shorter, and then the list's top moves down and the
     * inventory panel up, half each, as it grows. The rule is the one above either way; in
     * list mode ({@link Shape#LIST}) it never shrinks, as the list is never shorter than the
     * screen's own container part.
     */
    public static Geometry layout(Reference vanilla, int inventoryLabelY, int content, int screenHeight, Shape shape) {
        return layout(vanilla, inventoryLabelY, content, screenHeight, shape, 0);
    }

    /**
     * {@link #layout(Reference, int, int, int, Shape)} for content {@code wider} pixels wider
     * than {@link #CONTENT_WIDTH}: the list is that much wider, so a section with its own
     * scrollbar beside its grid ({@code ScrollContainer.Builder.laneOutside}) keeps it inside
     * the frame, while it shows.
     */
    public static Geometry layout(Reference vanilla, int inventoryLabelY, int content, int screenHeight, Shape shape,
                                  int wider) {
        int left = vanilla.leftPos(), imageWidth = vanilla.imageWidth();
        int imageBottom = vanilla.topPos() + vanilla.imageHeight();
        int vanillaInventoryTop = vanilla.topPos() + inventoryLabelY - shape.inventoryLabelTop();
        int ownPart = vanillaInventoryTop - shape.panelGap() - vanilla.topPos();
        // The room each way, never negative: the inventory down to the bottom margin, the
        // block's top up to the top margin.
        int roomBelow = Math.max(0, screenHeight - BOTTOM_MARGIN - imageBottom);
        int roomAbove = Math.max(0, vanilla.topPos() - TOP_MARGIN);
        // The list's height: its content and frame, as far as the room goes, the tab row
        // above it counted; never less than the screen's own part less what the shape lets
        // it shrink by. The block grows from vanilla's image by that and the tab row.
        long least = (long) ownPart - shape.shrink();
        int height = (int) Math.max(least, Math.min(VIEW_TOP + content + shape.viewBottom(),
                ownPart + roomAbove + roomBelow - shape.above()));
        int grow = shape.above() + height - ownPart;
        // The room still free once grown, split evenly: the block's top keeps the lower half
        // above it (floor, as vanilla centers), the inventory the rest below. Clamped so that
        // neither panel moves the other way from the block's growth (back, when it grows),
        // nor past its margin.
        int up = Math.max(Math.min(0, grow), Math.min(Math.max(0, grow), roomAbove - Math.floorDiv(roomAbove + roomBelow - grow, 2)));
        int down = grow - up;
        int blockTop = vanilla.topPos() - up;
        int top = blockTop + shape.above();
        int inventoryTop = vanillaInventoryTop + down;
        int bottom = inventoryTop - shape.panelGap();
        int viewHeight = height - VIEW_TOP - shape.viewBottom();
        boolean scrollable = content > viewHeight;
        int width = imageWidth + Math.max(0, wider) + (scrollable ? LANE : 0);
        int inventoryBottom = imageBottom + down;
        // One frame is one width: the inventory part takes the scrollbar lane's room too.
        int inventoryWidth = shape.joined() ? width : imageWidth;
        return new Geometry(left, top, width, height, bottom, top + VIEW_TOP, viewHeight, content, scrollable,
                new Reference(left, inventoryTop, inventoryWidth, inventoryBottom - inventoryTop),
                new Reference(left, blockTop, Math.max(width, imageWidth), inventoryBottom - blockTop), shape);
    }

    /**
     * How far vanilla's own placements move, from the screen's origin, when it goes from
     * {@code vanilla}'s to {@code bounds}'s: back by as much as the origin moved, so on screen
     * the title stays where vanilla put it ({@code [dx, dy]}); the inventory's slots and its
     * label also go down with the inventory panel, by as much as the bounds' bottom (the
     * panel's) moved down from vanilla's ({@code inventoryDy}). {@code [dx, dy, inventoryDy]}.
     */
    public static int[] counterShift(Reference vanilla, Reference bounds) {
        int dy = vanilla.topPos() - bounds.topPos();
        int down = (bounds.topPos() + bounds.imageHeight()) - (vanilla.topPos() + vanilla.imageHeight());
        return new int[]{vanilla.leftPos() - bounds.leftPos(), dy, dy + down};
    }

    /**
     * How fast carrying an item at {@code (mouseX, mouseY)} scrolls the region, in pixels a
     * second, negative upward: nothing over the region's middle, then faster nearer an edge
     * ({@link #EDGE_IN} inside it), full speed at the edge and {@link #EDGE_PAST} beyond it.
     * Below, that stops short of the inventory's first row. Nothing outside the region's
     * columns or when nothing scrolls.
     */
    public static double edgeSpeed(Geometry g, double mouseX, double mouseY) {
        if (!g.scrollable() || mouseX < g.x() || mouseX >= g.x() + g.width()) return 0;
        double intoTop = g.viewTop() + EDGE_IN - mouseY;              // > 0 near and past the top edge
        if (intoTop > 0 && intoTop <= EDGE_IN + EDGE_PAST) return -EDGE_SPEED * Math.min(1, intoTop / EDGE_IN);
        double intoBottom = mouseY - (g.viewBottom() - EDGE_IN);      // > 0 near and past the bottom edge
        if (intoBottom > 0 && intoBottom <= EDGE_IN + EDGE_PAST) return EDGE_SPEED * Math.min(1, intoBottom / EDGE_IN);
        return 0;
    }

    /**
     * The scroll that shows a slot whose cell starts {@code cellTop} pixels down the
     * content, whole, with the least movement: the scroll as it is when it shows already.
     */
    public static double revealScroll(double scroll, int cellTop, Geometry g) {
        return Scroller.intoView(scroll, cellTop, CELL, g.content(), g.viewHeight());
    }

    /**
     * Keeps what is on screen in place when sections above it change: the topmost section
     * that reached into the view before (and is still shown) moves the scroll by as much as
     * it moved. Tops of {@code -1} are hidden sections. At the top it stays at the top.
     */
    public static double keepInPlace(double scroll, int[] oldTop, int[] oldBottom, int[] newTop) {
        if (scroll <= 0) return scroll;
        for (int i = 0; i < oldTop.length; i++) {
            if (oldTop[i] < 0 || newTop[i] < 0 || oldBottom[i] <= scroll) continue;
            return scroll + (newTop[i] - oldTop[i]);
        }
        return scroll;
    }

    /**
     * Where a slot placed at {@code slotY} (vanilla's, from {@code topPos}) sits in the content:
     * its cell's top. The content is drawn {@code (int) scroll} up, as the scroll area moves it.
     */
    public static int cellTop(int topPos, int slotY, Geometry g, double scroll) {
        return topPos + slotY - 1 - g.viewTop() + (int) scroll;
    }

    /**
     * Each section's band on screen this frame, {@code [x0, y0, x1, y1]}, or {@code null}
     * while it is hidden or scrolled out of the list: its slice of the list goes there. The
     * bands tile the container list with no gap: across, its whole width (the scrollbar
     * draws over it); down, each from {@link #BAND_ABOVE} above its section to where the
     * next shown one's starts, the first from the list's top and the last to its bottom.
     * Moved by the scroll and cut at the list's edges.
     *
     * @param tops each section's top in the content, {@code -1} while hidden, in stacking order
     */
    public static int[][] bands(Geometry g, int[] tops, double scroll) {
        int n = tops.length;
        int[][] out = new int[n][];
        int x0 = g.x(), x1 = g.x() + g.width();
        int innerTop = g.top(), innerBottom = g.top() + g.height();
        // The content's top on screen this frame.
        int origin = g.viewTop() - (int) scroll;
        boolean first = true;
        for (int i = 0; i < n; i++) {
            if (tops[i] < 0) continue;
            int next = -1;
            for (int j = i + 1; j < n && next < 0; j++) if (tops[j] >= 0) next = tops[j];
            int y0 = first ? innerTop : Math.max(innerTop, origin + tops[i] - BAND_ABOVE);
            int y1 = next < 0 ? innerBottom : Math.min(innerBottom, origin + next - BAND_ABOVE);
            first = false;
            if (y0 < y1) out[i] = new int[]{x0, y0, x1, y1};
        }
        return out;
    }

    /**
     * One section's rectangle on screen, for its anchor: across the whole list, from its top
     * to its bottom (title included), moved by the scroll and cut at the viewport, where
     * scrolled content clips. {@code null} while it is hidden ({@code top < 0}) or scrolled
     * wholly out of view, so a panel on it hides with it.
     *
     * @param top    the section's top in the column the sections stack in, which starts
     *               the shape's {@link Shape#contentTop} down the content
     * @param bottom its bottom, likewise
     */
    public static @Nullable Reference sectionBox(Geometry g, int top, int bottom, double scroll) {
        if (top < 0) return null;
        // The column's top on screen this frame.
        int origin = g.viewTop() - (int) scroll + g.shape().contentTop();
        int y0 = Math.max(g.viewTop(), origin + top), y1 = Math.min(g.viewBottom(), origin + bottom);
        return y0 < y1 ? new Reference(g.x(), y0, g.width(), y1 - y0) : null;
    }

    /** Which band holds {@code (x, y)}, or {@code -1}. */
    public static int bandAt(int[][] bands, int x, int y) {
        for (int i = 0; i < bands.length; i++) {
            int[] r = bands[i];
            if (r != null && x >= r[0] && x < r[2] && y >= r[1] && y < r[3]) return i;
        }
        return -1;
    }
}
