package com.trevlar.menukit.api.element;

import com.trevlar.menukit.api.panel.PanelStyle;
import com.trevlar.menukit.api.panel.ScreenKind;
import com.trevlar.menukit.core.PanelRendering;

import net.minecraft.client.gui.screens.Screen;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A run of elements laid out left to right at the panel's runtime width,
 * wrapping onto new rows when it runs out of room: the runtime row. Holds
 * heterogeneous children (buttons, checkboxes, labels, icons), the element
 * analogue of {@code SlotFlowElement}.
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
 * each end of a one-row Flow centres what is between them. When the row is
 * too narrow and wraps, a spacer that ends up at the start or end of a row takes
 * nothing, and the control sits under its label.
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
 * card's look follows the kind of screen: on an options screen a dark translucent
 * box with a one-pixel light border that brightens while hovered, on a container
 * screen the {@code INSET} panel look. An icon beside a stack of lines is a boxed
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

    // The options-screen card: a dark translucent fill and a one-pixel light border,
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

    /** The width the panel gives the Flow; until the first pass, one row's worth. */
    private int budget = -1;

    /**
     * Vertical only: the height a column wraps at, from the panel ({@link #wrapHeight});
     * {@code -1} until the panel has a frame beside it, which means one column.
     */
    private int wrapAt = -1;

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
        return Math.max(1, total + Math.max(0, count - 1) * gapX + 2 * pad());
    }

    private int cap() {
        return budget > 0 ? budget : naturalWidth();
    }

    /** One row of children, and the spacers in it. */
    private record RowOf(List<PanelElement> members, int top) {}

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
        int cap = Math.max(1, cap() - 2 * pad());
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
        int x = 0, top = 0, rowH = 0;
        for (PanelElement c : children) {
            if (!c.isVisible()) continue;
            boolean spacer = c instanceof Spacer;
            int w = spacer ? 0 : c.getWidth();
            boolean hasContent = row.stream().anyMatch(e -> !(e instanceof Spacer));
            if (!spacer && hasContent && x + gapX + w > cap) {
                rows.add(new RowOf(row, top));
                top += rowH + gapY;
                row = new ArrayList<>();
                x = 0;
                rowH = 0;
            }
            // The gap sits between two pieces of content only. A spacer spends no gap
            // of its own (it is the stretchable part of one), so this pass counts the
            // same pixels naturalWidth() does and a row that fits by the natural width
            // never wraps one gap early. When a leading spacer cost a gap here but not
            // there, a button pushed right by a spacer wrapped to a second row.
            if (!spacer && hasContent) x += gapX;
            row.add(c);
            x += w;
            if (!spacer) rowH = Math.max(rowH, c.getHeight());
        }
        if (!row.isEmpty()) rows.add(new RowOf(row, top));

        int widest = 0, bottom = 0;
        for (RowOf r : rows) {
            int w = place(r, cap, rows.size() > 1);
            widest = Math.max(widest, w);
            int h = 0;
            for (PanelElement c : r.members()) h = Math.max(h, c.getHeight());
            bottom = Math.max(bottom, r.top() + h);
        }
        // A boxed row fills the width it was given (each card is a full row of a
        // list); otherwise the Flow hugs its widest row. Either way the card's
        // padding wraps the rows.
        lastWidth = boxed ? Math.max(cap(), widest + 2 * pad()) : widest;
        lastHeight = bottom + 2 * pad();
        laidOut = true;
        lastSignature = signature(); // after layout: a settled layout does not relayout
    }

    /**
     * The vertical layout: shown children top to bottom, each column breaking before
     * a child that would pass the wrap height (a column always takes its first child,
     * however tall, so nothing is lost and nothing loops), the next column to the
     * right of the widest child in the last. Hidden children and spacers take no
     * room. Records the hug of the columns as the Flow's size.
     */
    private void layoutColumns() {
        int limit = wrapAt > 0 ? Math.max(1, wrapAt - 2 * pad()) : Integer.MAX_VALUE;
        int x = 0, y = 0, colW = 0, bottom = 0;
        boolean columnHasChild = false;
        for (PanelElement c : children) {
            if (c instanceof Spacer s) {
                // No room in a column: parked at the origin with nothing to draw.
                s.w = 0;
                s.x = innerX();
                s.y = innerY();
                continue;
            }
            if (!c.isVisible()) continue;
            int h = c.getHeight();
            if (columnHasChild && y + gapY + h > limit) {
                // Full: the next column starts right of this one's widest child.
                x += colW + gapX;
                y = 0;
                colW = 0;
                columnHasChild = false;
            }
            if (columnHasChild) y += gapY;
            if (c instanceof AbstractPanelElement ape) ape.setChildPosition(innerX() + x, innerY() + y);
            y += h;
            colW = Math.max(colW, c.getWidth());
            bottom = Math.max(bottom, y);
            columnHasChild = true;
        }
        lastWidth = x + colW + 2 * pad();
        lastHeight = bottom + 2 * pad();
    }

    /**
     * Places one row: children left to right with the gap between them, each
     * spacer taking its share of the leftover (a spacer at either end of a row
     * that wrapped takes nothing). Returns the row's width.
     */
    private int place(RowOf r, int cap, boolean wrapped) {
        List<PanelElement> m = r.members();
        // Spacers between content count. One leading or trailing counts only while
        // the Flow is one row, so spacer, label, spacer centres the label; once it
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
        int x = 0, seen = 0;
        contentBefore = false;
        for (int i = 0; i < m.size(); i++) {
            PanelElement c = m.get(i);
            if (c instanceof Spacer s) {
                boolean live = live(i, firstContent, lastContent, wrapped);
                if (live) seen++;
                s.w = live ? share + (seen == spacers ? odd : 0) : 0;
                s.x = innerX() + x;
                s.y = innerY() + r.top();
                x += s.w;
            } else {
                if (contentBefore) x += gapX;
                if (c instanceof AbstractPanelElement ape) ape.setChildPosition(innerX() + x, innerY() + r.top());
                x += c.getWidth();
                contentBefore = true;
            }
        }
        return x;
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

    /** The extra height of the rows past the first: what a wrap adds. */
    @Override
    public int extraLayoutHeight() {
        relayout();
        int oneRow = 0;
        for (PanelElement c : children) if (c.isVisible()) oneRow = Math.max(oneRow, c.getHeight());
        return Math.max(0, lastHeight - (oneRow + 2 * pad()));
    }

    @Override
    public void fillWidth(int width) {}

    // Render and input. The children share the Flow's origin.

    @Override
    public void render(RenderContext ctx) {
        relayout();
        if (boxed) drawCard(ctx);
        ChildDispatch.render(children, childContext(ctx));
    }

    /** The card behind a boxed Flow's children, in the look of the screen's kind. */
    private void drawCard(RenderContext ctx) {
        int x = ctx.originX() + childX, y = ctx.originY() + childY;
        int w = lastWidth, h = lastHeight;
        var g = ctx.graphics();
        if (ctx.kind() == ScreenKind.OPTIONS) {
            g.fill(x, y, x + w, y + h, CARD_FILL);
            int border = ctx.isHovered(childX, childY, w, h) ? CARD_BORDER_HOVER : CARD_BORDER;
            g.fill(x, y, x + w, y + 1, border);
            g.fill(x, y + h - 1, x + w, y + h, border);
            g.fill(x, y + 1, x + 1, y + h - 1, border);
            g.fill(x + w - 1, y + 1, x + w, y + h - 1, border);
        } else {
            PanelRendering.renderPanel(g, x, y, w, h, PanelStyle.INSET);
        }
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

        @Override
        public Flow build() {
            return new Flow(this);
        }
    }
}
