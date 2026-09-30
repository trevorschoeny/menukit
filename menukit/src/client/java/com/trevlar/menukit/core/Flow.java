package com.trevlar.menukit.core;

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
 * <h3>Spacers: label left, control right (§0066)</h3>
 *
 * {@link #spacer()} is a gap that takes whatever the row's other children and
 * gaps leave of the Flow's width, split evenly among the row's spacers (the odd
 * pixel to the last). A row with a spacer therefore fills the Flow's width: a
 * label, a spacer and a control is a settings row with the control pinned to the
 * right edge, at whatever width the panel has, with no width handed to whoever
 * built it (a {@link Tabs} body factory never knows its width). When the row is
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
 * <h3>An interactive host</h3>
 *
 * The children live inside the Flow, which places them itself (in the panel's
 * content coordinates, so they share its origin). Render and input reach them
 * through {@link ChildDispatch}, exactly as the same elements directly on the
 * panel: clicks, the wheel, keys, releases, popovers, attach and detach. A
 * disabled Flow (its own {@code disabledWhen}) disables everything in it.
 */
public final class Flow extends AbstractPanelElement {

    /** Default gap between children, on both axes. */
    public static final int DEFAULT_GAP = 4;

    private final List<PanelElement> children;
    private final int gapX;
    private final int gapY;

    /** The width the panel gives the Flow; until the first pass, one row's worth. */
    private int budget = -1;

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
    }

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

    // ── Layout ─────────────────────────────────────────────────────────

    /** Stores the room the panel gives the Flow: the width its rows wrap at. */
    @Override
    public void layoutWithin(int budget) {
        this.budget = Math.max(1, budget);
    }

    /** Every child side by side (spacers take nothing), whatever is visible: a budget stable across reveals. */
    @Override
    public int naturalWidth() {
        int total = 0, count = 0;
        for (PanelElement c : children) {
            if (c instanceof Spacer) continue;
            total += c.naturalWidth();
            count++;
        }
        return Math.max(1, total + Math.max(0, count - 1) * gapX);
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
        int cap = cap();
        for (PanelElement c : children) {
            if (c.isVisible() && !(c instanceof Spacer)) c.layoutWithin(cap);
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
            if (!row.isEmpty()) x += gapX;
            row.add(c);
            x += w;
            if (!spacer) rowH = Math.max(rowH, c.getHeight());
        }
        if (!row.isEmpty()) rows.add(new RowOf(row, top));

        int widest = 0, bottom = 0;
        for (RowOf r : rows) {
            int w = place(r, cap);
            widest = Math.max(widest, w);
            int h = 0;
            for (PanelElement c : r.members()) h = Math.max(h, c.getHeight());
            bottom = Math.max(bottom, r.top() + h);
        }
        lastWidth = widest;
        lastHeight = bottom;
        laidOut = true;
        lastSignature = signature(); // after layout: a settled layout does not relayout
    }

    /**
     * Places one row: children left to right with the gap between them, each
     * spacer taking its share of the leftover (a spacer at either end of a row
     * that wrapped takes nothing). Returns the row's width.
     */
    private int place(RowOf r, int cap) {
        List<PanelElement> m = r.members();
        // Spacers between content count; one leading or trailing does not.
        int firstContent = -1, lastContent = -1;
        for (int i = 0; i < m.size(); i++) {
            if (!(m.get(i) instanceof Spacer)) {
                if (firstContent < 0) firstContent = i;
                lastContent = i;
            }
        }
        int fixed = 0, spacers = 0;
        for (int i = 0; i < m.size(); i++) {
            PanelElement c = m.get(i);
            if (i > 0) fixed += gapX;
            if (c instanceof Spacer) {
                if (live(i, firstContent, lastContent)) spacers++;
            } else {
                fixed += c.getWidth();
            }
        }
        int leftover = Math.max(0, cap - fixed);
        int share = spacers > 0 ? leftover / spacers : 0;
        int odd = spacers > 0 ? leftover % spacers : 0;
        int x = 0, seen = 0;
        for (int i = 0; i < m.size(); i++) {
            PanelElement c = m.get(i);
            if (i > 0) x += gapX;
            if (c instanceof Spacer s) {
                boolean live = live(i, firstContent, lastContent);
                if (live) seen++;
                s.w = live ? share + (seen == spacers ? odd : 0) : 0;
                s.x = childX + x;
                s.y = childY + r.top();
                x += s.w;
            } else {
                if (c instanceof AbstractPanelElement ape) ape.setChildPosition(childX + x, childY + r.top());
                x += c.getWidth();
            }
        }
        return x;
    }

    /**
     * Whether a spacer takes room: only between two children of its row. A
     * spacer left at the end of a row (its control wrapped to the next row) or at
     * the start of one takes nothing, so the wrapped control sits at the left.
     */
    private static boolean live(int i, int firstContent, int lastContent) {
        return firstContent >= 0 && i > firstContent && i < lastContent;
    }

    private int signature() {
        int sig = 31 + cap();
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
        return Math.max(0, lastHeight - oneRow);
    }

    @Override
    public void fillWidth(int width) {}

    // ── Render and input (the children share the Flow's origin) ────────

    @Override
    public void render(RenderContext ctx) {
        relayout();
        ChildDispatch.render(children, ctx.disabledIf(ownDisabled()));
    }

    @Override
    public void renderOverlay(RenderContext ctx) {
        ChildDispatch.renderOverlay(children, ctx.disabledIf(ownDisabled()));
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

    @Override
    public void onAttach(Screen screen) {
        ChildDispatch.attach(children, screen);
    }

    @Override
    public void onDetach(Screen screen) {
        ChildDispatch.detach(children, screen);
    }

    // ── Builder ────────────────────────────────────────────────────────

    public static final class Builder extends AbstractPanelElement.Builder<Flow, Builder> {
        private final List<PanelElement> children = new ArrayList<>();
        private int gapX = DEFAULT_GAP;
        private int gapY = DEFAULT_GAP;

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

        /** The gap between children in a row, and between rows. */
        public Builder gap(int horizontal, int vertical) {
            this.gapX = Math.max(0, horizontal);
            this.gapY = Math.max(0, vertical);
            return this;
        }

        @Override
        public Flow build() {
            return new Flow(this);
        }
    }
}
