package com.trevlar.menukit.api.element;

import com.trevlar.menukit.api.layout.CrossAlign;
import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.ScreenKind;
import com.trevlar.menukit.core.PackLook;
import com.trevlar.menukit.core.PanelRendering;

import net.minecraft.client.gui.screens.Screen;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;

/**
 * A run of elements laid out left to right at the panel's runtime width,
 * wrapping onto new rows when it runs out of room: the runtime row. Holds
 * heterogeneous children (buttons, checkboxes, labels, icons), the element
 * analog of {@code SlotFlowElement}.
 *
 * <pre>{@code
 * Flow.builder().gap(10, 4)
 *         .add(onOffToggle)
 *         .add(Flow.spacer())
 *         .add(resetButton)            // pinned to the right edge
 *         .build();
 * }</pre>
 *
 * <h3>Spacers: label left, control right</h3>
 *
 * {@link #spacer()} is a gap that takes whatever the row's other children and
 * gaps leave of the Flow's width, split evenly among the row's spacers (the odd
 * pixel to the last). The Flow's gap sits only between two pieces of content; a
 * spacer adds its share and never a gap of its own, so a row's natural width and
 * its layout count the same pixels wherever the spacer sits. A row with a spacer
 * therefore fills the Flow's width: a label, a spacer and a control is a settings
 * row with the control pinned to the
 * right edge, at whatever width the panel has, with no width handed to whoever
 * built it (a {@link Tabs} body factory never knows its width). A spacer at
 * each end of a one-row Flow centers what is between them. When the row is
 * too narrow and wraps, a spacer that ends up at the start or end of a row takes
 * nothing, and the control sits under its label.
 *
 * <h3>Vertical alignment in a row (7.3.0)</h3>
 *
 * A row top-aligns its children by default. {@link Builder#crossAlign} with
 * {@link CrossAlign#CENTER} centers each child on its row's line instead, and
 * {@link CrossAlign#END} sets it on the line's bottom, so a label beside a
 * 20 px control, or a mod's toggle beside a list's 16 px buttons, sit level with
 * no pixel offsets. The line is the tallest child's first line: a child that grew
 * past its baseline (an open {@link Section}, a wrapped label) aligns by its
 * baseline and grows downward, so its header stays on the line.
 *
 * <pre>{@code
 * Flow.builder().crossAlign(CrossAlign.CENTER)
 *         .add(nameLabel)                  // centered on the toggle's height
 *         .add(Flow.spacer())
 *         .add(toggle)
 *         .build();
 * }</pre>
 *
 * <h3>Reactive sizing</h3>
 *
 * {@link #naturalWidth()} is every child side by side, so the panel hands the
 * Flow the room it has; each layout pass lays the children out to that width
 * (a label too long for the row wraps, a button caps), wraps them into rows, and
 * reports the hug of the result ({@link #getWidth()}/{@link #getHeight()}; the
 * full width when a row has a spacer). A wrap reports its extra rows as
 * {@link #extraLayoutHeight()}, so the panel pushes what is below down.
 *
 * <h3>Vertical: a column that wraps into columns</h3>
 *
 * {@link Builder#vertical()} turns the Flow on its side: children fill top to
 * bottom and, when the next one would pass the wrap height, start a new column to
 * the right. The wrap height is the room from the Flow's top edge down to where the
 * frame beside its panel ends ({@link #wrapHeight}): the panel learns it from its
 * host, the way a filling element learns its height from its panel. So a column of
 * switches beside the player inventory wraps into a second column at the bottom of
 * the container screen's frame. With no frame beside the panel (a HUD panel, a
 * panel above or below the frame, a pixel-placed one) the Flow is one column. The
 * Flow's width is its columns side by side, so its panel grows with each column
 * and a {@code RIGHT_*} region still places it beside the frame.
 *
 * <pre>{@code
 * Flow.builder().vertical().gap(1)
 *         .add(settingsButton)
 *         .add(lockSwitch)
 *         .add(restockSwitch)           // ... a second column once the frame ends
 *         .build();
 * }</pre>
 *
 * In a vertical Flow {@code gap(horizontal, vertical)} is the gap between columns
 * and the gap between children in a column; a spacer takes no room.
 *
 * <h3>At most so many in a column (7.5.0)</h3>
 *
 * {@link Builder#wrapAfter} caps how many shown children a column holds (a row, in a
 * row Flow), whatever room is left: a column that would overlap something past two
 * slots wraps after two. The cap is read every frame, so it can follow the game mode
 * or a setting. A column fills from its first child, so the latest ones wrap out; with
 * {@link Builder#keepLast()} the last ones keep the first column and the earliest step
 * out into the next, to its right:
 *
 * <pre>{@code
 * Flow.builder().vertical().gap(0).wrapAfter(2).keepLast()
 *         .add(enderSlot)               // steps out, right of the elytra
 *         .add(elytraSlot)
 *         .add(totemSlot)               // elytra and totem keep the first column
 *         .build();
 * }</pre>
 *
 * With two shown, that is one column with the ender slot on top, as without the cap.
 *
 * <h3>A filling child (7.3.0)</h3>
 *
 * In a vertical Flow a child that fills ({@code ScrollContainer.builder().fillHeight()})
 * gets the height the Flow is given less its other children and gaps, so a header and a
 * "New" row sit above a list that takes the rest and scrolls by itself. Such a Flow
 * fills too, and does not wrap into columns.
 *
 * <h3>Hidden children</h3>
 *
 * Either way a hidden child takes no room: the next one moves up into its place, so
 * the Flow reflows when a child hides or shows.
 *
 * <h3>An interactive host</h3>
 *
 * The children live inside the Flow, which places them itself (in the panel's
 * content coordinates, so they share its origin). Render and input reach them
 * through {@link ChildDispatch}, exactly as the same elements directly on the
 * panel: clicks, the wheel, keys, releases, popovers, attach and detach. A
 * disabled Flow (its own {@code disabledWhen}) disables everything in it.
 *
 * <h3>Boxed: a card</h3>
 *
 * {@link Builder#boxed()} draws a card behind the children: a row of a list that
 * reads as one unit (a world in a world list). The
 * children sit {@link #BOX_PADDING} inside the card's edge, a boxed row Flow fills
 * the width its panel gives it, and its height hugs its rows plus the padding. The
 * card's look follows the kind of screen: in the Default look a dark translucent
 * box with a one-pixel light border that brightens while hovered, in the Panel
 * look the {@code INSET} panel style. An icon beside a stack of lines is a boxed
 * Flow holding the icon and a vertical Flow:
 *
 * <pre>{@code
 * Flow.builder().boxed().gap(6)
 *         .add(icon)
 *         .add(Flow.builder().vertical().gap(2)
 *                 .add(title).add(detail).add(buttonRow).build())
 *         .build();
 * }</pre>
 */
public final class Flow extends AbstractPanelElement {

    /** Default gap between children, on both axes. */
    public static final int DEFAULT_GAP = 4;

    /** Space between a boxed Flow's card edge and its children ({@link Builder#boxed()}). */
    public static final int BOX_PADDING = 6;

    // The Default-look card: a dark translucent fill and a one-pixel light border,
    // brighter while hovered. The container-screen card is the INSET panel look.
    private static final int CARD_FILL = 0x40000000;
    private static final int CARD_BORDER = 0x33FFFFFF;
    private static final int CARD_BORDER_HOVER = 0x80FFFFFF;

    private final List<PanelElement> children;
    private final int gapX;
    private final int gapY;
    /** Top to bottom, wrapping into columns ({@link Builder#vertical()}), instead of rows. */
    private final boolean vertical;
    /** Draws a card behind the children and insets them ({@link Builder#boxed()}). */
    private final boolean boxed;
    /** Where a child sits on its row's line ({@link Builder#crossAlign}); START is today's top alignment. */
    private final CrossAlign crossAlign;
    /** The most shown children in a column or row, read every frame ({@link Builder#wrapAfter}); null for no cap. */
    private final @Nullable IntSupplier wrapAfter;
    /** Vertical only: the last children keep the first column, the earliest wrap out ({@link Builder#keepLast()}). */
    private final boolean keepLast;

    /** The width the panel gives the Flow; until the first pass, one row's worth. */
    private int budget = -1;

    /**
     * Vertical only: the height a column wraps at, from the panel ({@link #wrapHeight});
     * {@code -1} until the panel has a frame beside it, which means one column.
     */
    private int wrapAt = -1;

    /**
     * Vertical only: the height its container gives it while a child fills
     * ({@link #fillHeight}); {@code -1} for none, the filling children at their natural
     * height.
     */
    private int fillTo = -1;

    /**
     * Room kept free at the content's trailing edge for a list row's controls
     * ({@link #reserveTrailing}), or 0; and the one child that keeps it instead, or null.
     */
    private int trailing = 0;
    private @Nullable AbstractPanelElement trailingChild;

    // The last layout's hug size and the inputs it was computed from, so the
    // several geometry calls a frame makes do not each walk the children.
    private int lastWidth = 0;
    private int lastHeight = 0;
    private int lastSignature = 0;
    private boolean laidOut = false;

    private Flow(Builder b) {
        super(b);
        this.children = List.copyOf(b.children);
        this.gapX = b.gapX;
        this.gapY = b.gapY;
        this.vertical = b.vertical;
        this.boxed = b.boxed;
        this.crossAlign = b.crossAlign;
        this.wrapAfter = b.wrapAfter;
        this.keepLast = b.keepLast;
    }

    /** The cap on shown children per column or row now; 0 for none. */
    private int perLine() {
        return wrapAfter == null ? 0 : Math.max(0, wrapAfter.getAsInt());
    }

    /** The card's padding on each side: {@link #BOX_PADDING} when boxed, else none. */
    private int pad() {
        return boxed ? BOX_PADDING : 0;
    }

    /** Where the children's area starts, in the panel's content coordinates. */
    private int innerX() { return childX + pad(); }
    private int innerY() { return childY + pad(); }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * A flexible gap: takes the row's leftover width, shared with the row's other
     * spacers. See the class notes.
     */
    public static PanelElement spacer() {
        return new Spacer();
    }

    /** The spacer: nothing to draw, no input, a width the Flow sets per layout. */
    private static final class Spacer implements PanelElement {
        int x, y, w;
        @Override public int getChildX() { return x; }
        @Override public int getChildY() { return y; }
        @Override public int getWidth() { return w; }
        @Override public int getHeight() { return 0; }
        @Override public int naturalWidth() { return 0; }
        @Override public void render(RenderContext ctx) {}
    }

    // Layout

    /** Stores the room the panel gives the Flow: the width its rows wrap at. */
    @Override
    public void layoutWithin(int budget) {
        this.budget = Math.max(1, budget);
    }

    /**
     * The room the panel hands down from this Flow's top edge to where the frame
     * beside it ends ({@code -1}: no frame). A vertical Flow wraps its columns there;
     * a row ignores it.
     */
    @Override
    public void wrapHeight(int height) {
        this.wrapAt = height;
    }

    /**
     * Every child side by side (spacers take nothing), whatever is visible: a budget
     * stable across reveals. A vertical Flow asks for its widest child, one column:
     * the columns it adds are its own to report through {@link #getWidth()}.
     */
    @Override
    public int naturalWidth() {
        if (vertical) {
            int widest = 0;
            for (PanelElement c : children) {
                if (!(c instanceof Spacer)) widest = Math.max(widest, c.naturalWidth());
            }
            return Math.max(1, widest + 2 * pad());
        }
        int total = 0, count = 0;
        for (PanelElement c : children) {
            if (c instanceof Spacer) continue;
            total += c.naturalWidth();
            count++;
        }
        return Math.max(1, total + Math.max(0, count - 1) * gapX + 2 * pad() + trailing);
    }

    private int cap() {
        return budget > 0 ? budget : naturalWidth();
    }

    /** One row of children, and the spacers in it. */
    private record RowOf(List<PanelElement> members) {}

    /**
     * Lays the shown children out: each to the Flow's width (a long label wraps),
     * then into rows that break before a child that would pass the width (a
     * spacer never breaks a row), then each row's leftover shared among its
     * spacers. Positions are written in the panel's content coordinates. Skipped
     * when nothing that affects it changed.
     */
    private void relayout() {
        int sig = signature();
        if (laidOut && sig == lastSignature) return;
        // Inside a card the children wrap at the width less the padding on both sides.
        int cap = Math.max(1, cap() - 2 * pad() - trailing);
        for (PanelElement c : children) {
            if (c.isVisible() && !(c instanceof Spacer)) c.layoutWithin(cap);
        }
        if (vertical) {
            layoutColumns();
            laidOut = true;
            lastSignature = signature();
            return;
        }

        List<RowOf> rows = new ArrayList<>();
        List<PanelElement> row = new ArrayList<>();
        int x = 0, inRow = 0, most = perLine();
        for (PanelElement c : children) {
            if (!c.isVisible()) continue;
            boolean spacer = c instanceof Spacer;
            int w = spacer ? 0 : c.getWidth();
            boolean hasContent = inRow > 0;
            // A row breaks before a child that would pass the width, or one past the cap.
            if (!spacer && hasContent && (x + gapX + w > cap || (most > 0 && inRow >= most))) {
                rows.add(new RowOf(row));
                row = new ArrayList<>();
                x = 0;
                inRow = 0;
            }
            if (!spacer) inRow++;
            // The gap sits between two pieces of content only. A spacer spends no gap
            // of its own (it is the stretchable part of one), so this pass counts the
            // same pixels naturalWidth() does and a row that fits by the natural width
            // never wraps one gap early. When a leading spacer cost a gap here but not
            // there, a button pushed right by a spacer wrapped to a second row.
            if (!spacer && hasContent) x += gapX;
            row.add(c);
            x += w;
        }
        if (!row.isEmpty()) rows.add(new RowOf(row));

        // Each row starts below the one before, as tall as what it holds once aligned
        // on its line (top-aligned, that is its tallest child).
        int widest = 0, top = 0, bottom = 0;
        for (RowOf r : rows) {
            int[] placed = place(r, top, cap, rows.size() > 1);
            widest = Math.max(widest, placed[0]);
            bottom = top + placed[1];
            top = bottom + gapY;
        }
        // A boxed row fills the width it was given (each card is a full row of a
        // list); otherwise the Flow hugs its widest row. Either way the card's
        // padding wraps the rows.
        lastWidth = boxed ? Math.max(cap(), widest + 2 * pad() + trailing) : widest;
        lastHeight = bottom + 2 * pad();
        laidOut = true;
        lastSignature = signature(); // after layout: a settled layout does not relayout
    }

    /**
     * The vertical layout: shown children top to bottom, each column breaking before
     * a child that would pass the wrap height or the cap (a column always takes its first
     * child, however tall, so nothing is lost and nothing loops), the next column to the
     * right of the widest child in the last. With {@link #keepLast} the columns are cut
     * from the last child back, so the last ones keep the first column; each column still
     * reads top to bottom in the Flow's order. Hidden children and spacers take no room.
     * Records the hug of the columns as the Flow's size.
     */
    private void layoutColumns() {
        // A filling child takes what the others leave of the height the Flow is given,
        // and the column does not wrap (it is the list under a header, not a column of
        // switches beside a frame).
        boolean filling = ChildDispatch.anyFillsHeight(children);
        if (filling) giveFill();
        int limit = !filling && wrapAt > 0 ? Math.max(1, wrapAt - 2 * pad()) : Integer.MAX_VALUE;
        int most = filling ? 0 : perLine();
        List<PanelElement> shown = new ArrayList<>();
        for (PanelElement c : children) {
            if (c instanceof Spacer s) {
                // No room in a column: parked at the origin with nothing to draw.
                s.w = 0;
                s.x = innerX();
                s.y = innerY();
            } else if (c.isVisible()) {
                shown.add(c);
            }
        }

        // Cut into columns, from the first child or (keepLast) from the last.
        List<List<PanelElement>> columns = new ArrayList<>();
        List<PanelElement> column = new ArrayList<>();
        int y = 0;
        for (int i = 0; i < shown.size(); i++) {
            PanelElement c = shown.get(keepLast ? shown.size() - 1 - i : i);
            int h = c.getHeight();
            if (!column.isEmpty() && (y + gapY + h > limit || (most > 0 && column.size() >= most))) {
                columns.add(column);
                column = new ArrayList<>();
                y = 0;
            }
            if (!column.isEmpty()) y += gapY;
            y += h;
            if (keepLast) column.add(0, c);
            else column.add(c);
        }
        if (!column.isEmpty()) columns.add(column);

        // Columns left to right, each top to bottom.
        int x = 0, colW = 0, bottom = 0;
        for (List<PanelElement> col : columns) {
            if (col != columns.get(0)) x += colW + gapX;
            colW = 0;
            y = 0;
            for (PanelElement c : col) {
                if (c != col.get(0)) y += gapY;
                if (c instanceof AbstractPanelElement ape) ape.setChildPosition(innerX() + x, innerY() + y);
                y += c.getHeight();
                colW = Math.max(colW, c.getWidth());
            }
            bottom = Math.max(bottom, y);
        }
        lastWidth = x + colW + 2 * pad();
        lastHeight = bottom + 2 * pad();
    }

    /**
     * Hands each filling child its share of what the others leave: the height the Flow
     * was given, less its padding, the other shown children and the gaps; or {@code -1}
     * (its natural height) when the Flow was given none.
     */
    private void giveFill() {
        int fixed = 0, shown = 0, fills = 0;
        for (PanelElement c : children) {
            if (c instanceof Spacer || !c.isVisible()) continue;
            shown++;
            if (c.fillsHeight()) fills++;
            else fixed += c.getHeight();
        }
        int left = fillTo - 2 * pad() - fixed - Math.max(0, shown - 1) * gapY;
        for (PanelElement c : children) {
            if (c instanceof Spacer || !c.isVisible() || !c.fillsHeight()) continue;
            c.fillHeight(fillTo > 0 ? Math.max(1, left / fills) : -1);
        }
    }

    /**
     * A child's first line: its height, less what it grew by past it (an open
     * {@link Section}, a wrapped label). A vertical Flow counts whole: what it reports
     * is its stack, not growth.
     */
    private static int baseline(PanelElement c) {
        if (c instanceof Flow f && f.vertical) return c.getHeight();
        return c.getHeight() - Math.max(0, c.extraLayoutHeight());
    }

    /** How far below the row's top a child sits: by {@link #crossAlign}, on a line {@code line} tall. */
    private int alignOffset(PanelElement c, int line) {
        return switch (crossAlign) {
            case START, FILL -> 0;   // FILL is a Column's; a row treats it as START, as Row does
            case CENTER -> (line - baseline(c)) / 2;
            case END -> line - baseline(c);
        };
    }

    /**
     * Places one row at {@code top}: children left to right with the gap between them,
     * each spacer taking its share of the leftover (a spacer at either end of a row
     * that wrapped takes nothing), each child set on the row's line by
     * {@link #crossAlign}. Returns the row's width and height.
     */
    private int[] place(RowOf r, int top, int cap, boolean wrapped) {
        List<PanelElement> m = r.members();
        // The row's line: its tallest child's first line.
        int line = 0;
        for (PanelElement c : m) if (!(c instanceof Spacer)) line = Math.max(line, baseline(c));
        // Spacers between content count. One leading or trailing counts only while
        // the Flow is one row, so spacer, label, spacer centers the label; once it
        // wraps, the ends take nothing and a wrapped control sits at the left.
        int firstContent = -1, lastContent = -1;
        for (int i = 0; i < m.size(); i++) {
            if (!(m.get(i) instanceof Spacer)) {
                if (firstContent < 0) firstContent = i;
                lastContent = i;
            }
        }
        // One gap between each two pieces of content, as in relayout(); spacers add
        // only their share of what is left.
        int fixed = 0, spacers = 0;
        boolean contentBefore = false;
        for (int i = 0; i < m.size(); i++) {
            PanelElement c = m.get(i);
            if (c instanceof Spacer) {
                if (live(i, firstContent, lastContent, wrapped)) spacers++;
            } else {
                if (contentBefore) fixed += gapX;
                fixed += c.getWidth();
                contentBefore = true;
            }
        }
        int leftover = Math.max(0, cap - fixed);
        int share = spacers > 0 ? leftover / spacers : 0;
        int odd = spacers > 0 ? leftover % spacers : 0;
        int x = 0, seen = 0, height = 0;
        contentBefore = false;
        for (int i = 0; i < m.size(); i++) {
            PanelElement c = m.get(i);
            if (c instanceof Spacer s) {
                boolean live = live(i, firstContent, lastContent, wrapped);
                if (live) seen++;
                s.w = live ? share + (seen == spacers ? odd : 0) : 0;
                s.x = innerX() + x;
                s.y = innerY() + top;
                x += s.w;
            } else {
                if (contentBefore) x += gapX;
                int dy = alignOffset(c, line);
                if (c instanceof AbstractPanelElement ape) ape.setChildPosition(innerX() + x, innerY() + top + dy);
                x += c.getWidth();
                height = Math.max(height, dy + c.getHeight());
                contentBefore = true;
            }
        }
        return new int[]{x, height};
    }

    /**
     * Whether a spacer takes room: in a row with content, anywhere while the Flow
     * is one row, else only between two children of its row. A spacer left at the
     * end of a row (its control wrapped to the next row) or at the start of one
     * takes nothing, so the wrapped control sits at the left.
     */
    private static boolean live(int i, int firstContent, int lastContent, boolean wrapped) {
        return firstContent >= 0 && (!wrapped || (i > firstContent && i < lastContent));
    }

    private int signature() {
        int sig = 31 + cap();
        sig = sig * 31 + wrapAt;
        sig = sig * 31 + fillTo;
        sig = sig * 31 + perLine();
        sig = sig * 31 + childX;
        sig = sig * 31 + childY;
        for (PanelElement c : children) {
            boolean vis = c.isVisible();
            sig = sig * 31 + (vis ? 1 : 0);
            if (vis && !(c instanceof Spacer)) {
                sig = sig * 31 + c.getWidth();
                sig = sig * 31 + c.getHeight();
            }
        }
        return sig;
    }

    @Override
    public int getWidth() {
        relayout();
        return lastWidth;
    }

    @Override
    public int getHeight() {
        relayout();
        return lastHeight;
    }

    /**
     * The extra height of the rows past the first: what a wrap adds. A child that grew
     * past its own baseline (an open {@link Section}, a wrapped label, a {@link ListView},
     * whose baseline is nothing) counts at its baseline (7.3.0), so its growth is the
     * Flow's too and what is below is pushed down by it; a list row's controls stay
     * centered on the row's first line. In a row Flow a vertical Flow child counts whole:
     * what it reports is its stack, not growth. A vertical Flow's own extra is its
     * stack past its tallest child's baseline, which stays put as its children grow,
     * so a list in a column pushes what is below the column.
     */
    @Override
    public int extraLayoutHeight() {
        relayout();
        int oneRow = 0;
        for (PanelElement c : children) {
            if (!c.isVisible()) continue;
            // In a column a child counts at its baseline too: counted whole, a list or an open
            // section that is the tallest child would grow the baseline with it, and the
            // column's extra would not change, so nothing below the column would move.
            oneRow = Math.max(oneRow, vertical ? c.getHeight() - Math.max(0, c.extraLayoutHeight()) : baseline(c));
        }
        return Math.max(0, lastHeight - (oneRow + 2 * pad()));
    }

    @Override
    public void fillWidth(int width) {}

    /**
     * A list row's controls on a row Flow's first row, at its trailing edge, inside the
     * card when boxed: a Flow around one element that keeps the room itself (a
     * {@link Section}, whose open content then keeps the whole width) hands it on;
     * otherwise the Flow's content is that much narrower. A vertical Flow is a column
     * with no line of its own to share, so it is narrowed whole beside the controls.
     */
    @Override
    boolean reserveTrailing(int px) {
        if (vertical) return false;
        PanelElement only = null;
        int count = 0;
        for (PanelElement c : children) {
            if (c instanceof Spacer) continue;
            only = c;
            count++;
        }
        if (count == 1 && only instanceof AbstractPanelElement a && a.reserveTrailing(px)) {
            trailingChild = a;
        } else {
            trailing = Math.max(0, px);
        }
        laidOut = false;
        return true;
    }

    /** The first row, inside the padding; or the line of the child that kept the room, where it sits. */
    @Override
    int[] trailingLine() {
        relayout();
        if (trailingChild != null) {
            int[] line = trailingChild.trailingLine();
            int right = cap() - (trailingChild.getChildX() - childX + trailingChild.getWidth());
            return new int[]{trailingChild.getChildY() - childY + line[0], line[1], right + line[2]};
        }
        return new int[]{pad(), lastHeight - 2 * pad() - extraLayoutHeight(), pad()};
    }

    /** A vertical Flow fills when a child of it does (a header over a filling list). */
    @Override
    public boolean fillsHeight() {
        return vertical && ChildDispatch.anyFillsHeight(children);
    }

    /** The height to share out to the filling children ({@link #giveFill}); {@code -1} for none. */
    @Override
    public void fillHeight(int height) {
        this.fillTo = height;
    }

    // Render and input. The children share the Flow's origin.

    @Override
    public void render(RenderContext ctx) {
        relayout();
        if (!boxed) {
            ChildDispatch.render(children, childContext(ctx));
            return;
        }
        drawCard(ctx);
        // On the card, text and slot frames are the card's, not the panel surface's under it:
        // vanilla's gray on the card's gray, whatever a resource pack made of the panel.
        PackLook.SurfaceLooks before = ctx.kind() == ScreenKind.PANEL ? PackLook.beginSurface(null) : null;
        try {
            ChildDispatch.render(children, childContext(ctx));
        } finally {
            if (ctx.kind() == ScreenKind.PANEL) PackLook.endSurface(before);
        }
    }

    /**
     * Set by a {@link ListView} every frame on each of its rows that is a card: whether
     * it is the selected row. In the Panel look the selected card is washed lighter
     * inside its border (an outline is lost on the Panel card's own dark border); the
     * Default look's list outlines it instead.
     */
    boolean listSelected = false;
    /** The selected card's wash: a step brighter than a dropdown's selected row (0x60), so a whole card reads as picked. */
    private static final int SELECTED_WASH = 0x80FFFFFF;

    /** Whether this Flow draws a card ({@link Builder#boxed()}). */
    boolean isBoxed() {
        return boxed;
    }

    /** The card behind a boxed Flow's children, in the look of the screen's kind; washed while a list's selected row in the Panel look. */
    private void drawCard(RenderContext ctx) {
        int x = ctx.originX() + childX, y = ctx.originY() + childY;
        int w = lastWidth, h = lastHeight;
        var g = ctx.graphics();
        if (ctx.kind() == ScreenKind.DEFAULT) {
            g.fill(x, y, x + w, y + h, CARD_FILL);
            int border = ctx.isHovered(childX, childY, w, h) ? CARD_BORDER_HOVER : CARD_BORDER;
            g.fill(x, y, x + w, y + 1, border);
            g.fill(x, y + h - 1, x + w, y + h, border);
            g.fill(x, y + 1, x + 1, y + h - 1, border);
            g.fill(x + w - 1, y + 1, x + w, y + h - 1, border);
        } else {
            PanelRendering.renderPanel(g, x, y, w, h, PanelStyle.INSET);
            if (listSelected) g.fill(x + 2, y + 2, x + w - 2, y + h - 2, SELECTED_WASH);
        }
    }

    @Override
    public void renderCulled(RenderContext ctx) {
        ChildDispatch.renderCulled(children, childContext(ctx));
    }

    @Override
    public void renderOverlay(RenderContext ctx) {
        ChildDispatch.renderOverlay(children, childContext(ctx));
    }

    /** Interactive where a child is, so on a transparent panel it claims only its children. */
    @Override
    public boolean isInteractive() {
        for (PanelElement c : children) if (c.isVisible() && c.isInteractive()) return true;
        return false;
    }

    /** Wants an event only over a child, so the gaps between children claim nothing. */
    @Override
    public boolean hitTest(InputContext in) {
        relayout();
        return ChildDispatch.hitTest(children, in);
    }

    @Override
    public boolean mouseClicked(InputContext in, int button) {
        relayout();
        return ChildDispatch.mouseClicked(children, in.disabledIf(ownDisabled()), button);
    }

    @Override
    public boolean presentsSlotAt(InputContext in) {
        relayout();
        return ChildDispatch.presentsSlotAt(children, in);
    }

    @Override
    public boolean mouseScrolled(InputContext in, double scrollX, double scrollY) {
        relayout();
        return ChildDispatch.mouseScrolled(children, in.disabledIf(ownDisabled()), scrollX, scrollY);
    }

    @Override
    public boolean mouseReleased(InputContext in, int button) {
        ChildDispatch.mouseReleased(children, in, button);
        return false;
    }

    @Override
    public boolean keyPressed(InputContext in, int keyCode, int scanCode, int modifiers) {
        return ChildDispatch.keyPressed(children, in.disabledIf(ownDisabled()), keyCode, scanCode, modifiers);
    }

    @Override
    public int @Nullable [] getActiveOverlayBounds(InputContext in) {
        return ChildDispatch.activeOverlay(children, in);
    }

    @Override
    public void notifyClickOutsideOverlay(InputContext in) {
        ChildDispatch.notifyClickOutside(children, in);
    }

    /** A child's request to be scrolled into view, passed on (the children share the Flow's origin). */
    @Override
    public int @Nullable [] takeRevealRequest() {
        relayout();
        return ChildDispatch.takeRevealRequest(children);
    }

    boolean contains(PanelElement e) {
        return children.contains(e);
    }

    @Override
    public void screenKind(ScreenKind kind) {
        ChildDispatch.screenKind(children, kind);
    }

    @Override
    public void inDialog(boolean inDialog) {
        ChildDispatch.inDialog(children, inDialog);
    }

    @Override
    public void inBand(boolean inBand) {
        ChildDispatch.inBand(children, inBand);
    }

    /** A vertical Flow is as much shorter as its children give; a row is as tall as its tallest, so it gives nothing. */
    @Override
    public int yieldHeight(int overflow) {
        return vertical ? ChildDispatch.yieldHeight(children, overflow) : 0;
    }

    @Override
    public void onAttach(Screen screen) {
        ChildDispatch.attach(children, screen);
    }

    @Override
    public void onDetach(Screen screen) {
        ChildDispatch.detach(children, screen);
    }

    public static final class Builder extends AbstractPanelElement.Builder<Flow, Builder> {
        private final List<PanelElement> children = new ArrayList<>();
        private int gapX = DEFAULT_GAP;
        private int gapY = DEFAULT_GAP;
        private boolean vertical = false;
        private boolean boxed = false;
        private CrossAlign crossAlign = CrossAlign.START;
        private @Nullable IntSupplier wrapAfter;
        private boolean keepLast = false;

        private Builder() {}

        @Override protected Builder self() { return this; }

        /** Adds a child (or a {@link Flow#spacer()}). Declaration order is flow order. */
        public Builder add(PanelElement child) {
            children.add(Objects.requireNonNull(child, "child"));
            return this;
        }

        /** Adds children, in order. */
        public Builder addAll(List<? extends PanelElement> children) {
            for (PanelElement c : children) add(c);
            return this;
        }

        /** The gap between children, both axes. Default 4. */
        public Builder gap(int px) {
            return gap(px, px);
        }

        /**
         * The gap between children in a row and between rows; in a vertical Flow,
         * between columns and between children in a column.
         */
        public Builder gap(int horizontal, int vertical) {
            this.gapX = Math.max(0, horizontal);
            this.gapY = Math.max(0, vertical);
            return this;
        }

        /**
         * Top to bottom, wrapping into a new column to the right where the frame
         * beside the panel ends. Default: left to right, wrapping into rows. See the
         * class notes.
         */
        public Builder vertical() {
            this.vertical = true;
            return this;
        }

        /**
         * Draws a card behind the children: they sit {@link Flow#BOX_PADDING} inside
         * it, a row Flow fills its width, the height hugs the content. See the class
         * notes.
         */
        public Builder boxed() {
            this.boxed = true;
            return this;
        }

        /**
         * Where each child sits on its row's line, in a row Flow. The default,
         * {@link CrossAlign#START}, top-aligns them; {@link CrossAlign#CENTER} centers
         * each on the line (a label level with the middle of its control);
         * {@link CrossAlign#END} sets each on the line's bottom. {@code FILL} is a
         * {@code Column}'s and reads as {@code START}. The line is the tallest child's
         * first line; see the class notes. A vertical Flow throws at build on anything
         * but {@code START}.
         */
        public Builder crossAlign(CrossAlign align) {
            this.crossAlign = Objects.requireNonNull(align, "align");
            return this;
        }

        /**
         * At most {@code n} shown children in a column (in a row, in a row Flow), whatever
         * room is left. See the class notes.
         */
        public Builder wrapAfter(int n) {
            if (n < 1) throw new IllegalArgumentException("wrapAfter must be >= 1, was " + n);
            return wrapAfter(() -> n);
        }

        /**
         * At most as many shown children in a column (a row) as {@code n} gives, read every
         * frame; 0 or less for no cap. For a cap that follows the game mode or a setting.
         */
        public Builder wrapAfter(IntSupplier n) {
            this.wrapAfter = Objects.requireNonNull(n, "n");
            return this;
        }

        /**
         * In a vertical Flow that wraps, the last children keep the first column and the
         * earliest step out into the next, to its right. Default: the first ones keep it.
         */
        public Builder keepLast() {
            this.keepLast = true;
            return this;
        }

        @Override
        public Flow build() {
            if (keepLast && !vertical) throw new IllegalStateException("Flow: keepLast() is for a vertical() Flow");
            if (vertical && crossAlign != CrossAlign.START) {
                throw new IllegalStateException("Flow: crossAlign(" + crossAlign + ") is for a row Flow, not vertical()");
            }
            return new Flow(this);
        }
    }
}
