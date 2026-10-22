package com.trevlar.menukit.inject;

import com.trevlar.menukit.api.element.ScrollContainer;
import com.trevlar.menukit.api.panel.Reference;
import com.trevlar.menukit.api.panel.RegionConstants;
import com.trevlar.menukit.core.Scroller;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/**
 * Where the container region's two panels go, pure (7.4.0; {@link ContainerRegions} is the
 * region itself): how the container list grows, centres and scrolls above the inventory
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
     * it is the band of the section above, so the grid above keeps 2 px of its own colour
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

    /**
     * Where the two panels are on a screen this frame.
     *
     * @param x          the container list's left edge (the screen's own {@code leftPos})
     * @param top        its top edge
     * @param width      its width: the screen's image width, and the scrollbar lane while it scrolls
     * @param height     its height, from {@code top} to {@code bottom}
     * @param bottom     its bottom edge, {@link #PANEL_GAP} above the inventory panel
     * @param viewTop    the viewport's top edge
     * @param viewHeight the viewport's height, down to {@link #VIEW_BOTTOM} above {@code bottom}
     * @param content    the height of everything in it
     * @param scrollable whether the content is taller than the viewport
     * @param inventory  the inventory panel: where vanilla put the inventory part, from
     *                   {@link #INVENTORY_LABEL_TOP} above its label to the image's bottom
     * @param bounds     the screen's bounds: the rectangle around both panels
     */
    public record Geometry(int x, int top, int width, int height, int bottom, int viewTop, int viewHeight,
                           int content, boolean scrollable, Reference inventory, Reference bounds) {

        /** The viewport's bottom edge: where scrolled content clips. */
        public int viewBottom() { return viewTop + viewHeight; }

        /** The container list's rectangle. */
        public Reference list() { return new Reference(x, top, width, height); }
    }

    /**
     * Grow first, then scroll, centred while it fits, from the screen's own bounds as vanilla
     * laid them out. The container list starts as the screen's own container part, its top at
     * vanilla's {@code topPos}, and grows with its content. The two panels are one block, and
     * the room left free around it is shared evenly above and below (to {@link #TOP_MARGIN}
     * and {@link #BOTTOM_MARGIN}), as far as each side has room:
     * <ul>
     *   <li>while the block fits centred, it is centred: each pixel the list grows moves
     *       its top up half a pixel and the inventory panel down half a pixel, rounded as
     *       vanilla rounds its own centring;</li>
     *   <li>where one side has less room than the other (a window too short for vanilla's
     *       own margins, a screen vanilla does not centre), that side's panel holds and the
     *       other moves until the block is centred: the list's top holds and the inventory
     *       panel moves down, or the other way round. A side with no room at all never
     *       moves, so the other takes all the growth up to its margin;</li>
     *   <li>only when both margins are reached does the list <b>scroll</b>.</li>
     * </ul>
     * Each pixel of growth moves each panel by at most one pixel and never back, so a box
     * opening never makes the block jump, at any window height. With vanilla's image
     * centred the block stays centred right up to both margins at once.
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
        int left = vanilla.leftPos(), imageWidth = vanilla.imageWidth();
        int imageBottom = vanilla.topPos() + vanilla.imageHeight();
        int vanillaInventoryTop = vanilla.topPos() + inventoryLabelY - INVENTORY_LABEL_TOP;
        int ownPart = vanillaInventoryTop - PANEL_GAP - vanilla.topPos();
        // The room each way, never negative: the inventory down to the bottom margin, the
        // list's top up to the top margin.
        int roomBelow = Math.max(0, screenHeight - BOTTOM_MARGIN - imageBottom);
        int roomAbove = Math.max(0, vanilla.topPos() - TOP_MARGIN);
        int grow = Math.max(0, Math.min(VIEW_TOP + content + VIEW_BOTTOM - ownPart, roomBelow + roomAbove));
        // The room still free once grown, split evenly: the list's top keeps the lower half
        // above it (floor, as vanilla centres), the inventory the rest below. Clamped so that
        // neither panel moves back, nor past its margin.
        int up = Math.max(0, Math.min(grow, roomAbove - Math.floorDiv(roomAbove + roomBelow - grow, 2)));
        int down = grow - up;
        int height = ownPart + grow;
        int top = vanilla.topPos() - up;
        int inventoryTop = vanillaInventoryTop + down;
        int bottom = inventoryTop - PANEL_GAP;
        int viewHeight = height - VIEW_TOP - VIEW_BOTTOM;
        boolean scrollable = content > viewHeight;
        int width = imageWidth + (scrollable ? LANE : 0);
        int inventoryBottom = imageBottom + down;
        return new Geometry(left, top, width, height, bottom, top + VIEW_TOP, viewHeight, content, scrollable,
                new Reference(left, inventoryTop, imageWidth, inventoryBottom - inventoryTop),
                new Reference(left, top, Math.max(width, imageWidth), inventoryBottom - top));
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
     *               {@link #CONTENT_TOP} down the content
     * @param bottom its bottom, likewise
     */
    public static @Nullable Reference sectionBox(Geometry g, int top, int bottom, double scroll) {
        if (top < 0) return null;
        // The column's top on screen this frame.
        int origin = g.viewTop() - (int) scroll + CONTENT_TOP;
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
