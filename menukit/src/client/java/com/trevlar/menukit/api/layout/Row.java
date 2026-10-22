package com.trevlar.menukit.api.layout;

import com.trevlar.menukit.api.element.PanelElement;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Lays elements out left to right: it positions each child with even spacing and
 * a vertical alignment, and returns a flat list of positioned
 * {@link PanelElement}s.
 *
 * <p>A Row only exists while you build it. Don't keep it around to lay out again;
 * add the returned elements to a {@link com.trevlar.menukit.api.panel.Panel} like
 * any others.
 *
 * <p><b>Measured on first use.</b> The positions are computed once, on the
 * first read of any emitted element's position (in practice the panel's first
 * layout pass), not in {@code build()}: a label's or button's size needs the
 * game's font, which does not exist yet at mod init, where panels and dialogs
 * are declared. So a Row builds anywhere. A Row holding a custom
 * {@link ElementSpec}, whose element exists only once placed, is laid out in
 * {@code build()} instead, measuring then.
 *
 * <p><b>Helper, not container.</b> Row is not a {@link PanelElement}, does not
 * render, and is gone once built. The Panel draws the elements directly, so a
 * Panel stays the only thing that contains elements.
 *
 * <h3>Usage</h3>
 *
 * <pre>{@code
 * List<PanelElement> buttonRow = Row.at(20, 30).spacing(4)
 *     .add(Button.builder().label(Component.literal("OK")).size(60, 20).onClick(this::onConfirm).build())
 *     .add(Button.builder().label(Component.literal("Cancel")).size(60, 20).onClick(this::onCancel).build())
 *     .build();
 *
 * Panel p = Panel.builder("confirm")
 *     .elements(buttonRow).style(PanelStyle.RAISED).build();
 * }</pre>
 *
 * <h3>Pushing an element to the far edge</h3>
 *
 * {@link Builder#width(int)} declares the row's overall pixel width, and
 * {@link Builder#addSpacer()} adds a flexible gap that expands to fill
 * whatever the other children and spacing leave over (like CSS
 * {@code justify-content: space-between}), for a header with one thing on the
 * left and another on the right:
 *
 * <pre>{@code
 * List<PanelElement> header = Row.at(0, 0).width(220).spacing(6)
 *     .add(Button.builder().label(Component.literal("Back")).size(60, 16).onClick(this::onBack).build())
 *     .addSpacer()
 *     .add(Button.builder().label(Component.literal("Reset")).size(60, 16).onClick(this::onReset).build())
 *     .build();
 * }</pre>
 *
 * Several spacers in one row split the leftover evenly (the odd pixel to the
 * last one), so a three-part header (one thing at each edge and one centered) is
 * just two spacers.
 *
 * <h3>Nesting</h3>
 *
 * Rows and columns nest through {@link Builder#addRow(Consumer)} and
 * {@link Builder#addColumn(Consumer)}. Each lays its elements out from its own
 * origin and the outer one offsets them, so {@code build()} still returns one
 * flat list of positioned elements.
 *
 * @see Column
 * @see ElementSpec
 * @see CrossAlign
 */
public final class Row {

    private Row() {}

    /**
     * Starts a row at a panel-local origin.
     *
     * @param originX panel-local X of the first child's left edge
     * @param originY panel-local Y of the row; alignment applies within
     *                [originY, originY + tallest child]
     */
    public static Builder at(int originX, int originY) {
        return new Builder(originX, originY);
    }

    /** Builds one row. Finish with {@link #build()}. */
    public static final class Builder {

        private final int originX;
        private final int originY;
        private int spacing = 0;
        private int width = -1;
        private CrossAlign crossAlign = CrossAlign.START;
        final List<LayoutEntry> entries = new ArrayList<>();   // package-private: a nesting helper reads its members

        Builder(int originX, int originY) {
            this.originX = originX;
            this.originY = originY;
        }

        /** Pixels between adjacent children. Must be {@code >= 0}. */
        public Builder spacing(int px) {
            if (px < 0) {
                throw new IllegalArgumentException(
                        "spacing must be >= 0, got " + px);
            }
            this.spacing = px;
            return this;
        }

        /**
         * The row's overall width in pixels, the room an {@link #addSpacer()}
         * expands into. Needed only with a spacer; otherwise it is the width the row
         * reports to an enclosing Row or Column instead of its children's total
         * (to reserve room for a sibling that grows later, say).
         */
        public Builder width(int px) {
            if (px <= 0) {
                throw new IllegalArgumentException("width must be > 0, got " + px);
            }
            this.width = px;
            return this;
        }

        /**
         * A flexible gap that fills whatever {@link #width(int)} leaves after the
         * other children and the spacing between them; this is how "Back" and
         * "Reset" end up at opposite edges of one row. Several spacers split the
         * leftover evenly, the odd pixel going to the last one. Needs
         * {@link #width(int)}; building without it throws.
         */
        public Builder addSpacer() {
            entries.add(LayoutEntry.spacer());
            return this;
        }

        /**
         * Vertical alignment. The default, {@link CrossAlign#START}, aligns children
         * to the row's top edge.
         */
        public Builder crossAlign(CrossAlign align) {
            if (align == null) {
                throw new IllegalArgumentException("crossAlign must not be null");
            }
            this.crossAlign = align;
            return this;
        }

        /** Appends an element. */
        public Builder add(ElementSpec spec) {
            entries.add(LayoutEntry.fromSpec(spec));
            return this;
        }

        /** Appends an element that is already built. See {@link ElementSpec#of(PanelElement)}. */
        public Builder add(PanelElement element) {
            entries.add(LayoutEntry.fromElement(element));
            return this;
        }

        /**
         * Appends a nested row. Its elements are laid out with its own spacing and
         * alignment, then moved to its place in this row, where it takes up its
         * total width.
         */
        public Builder addRow(Consumer<Row.Builder> config) {
            Row.Builder nested = new Row.Builder(0, 0);
            config.accept(nested);
            nested.requireSpacerWidth();
            entries.add(LayoutEntry.fromNested(nested::computeMainAxisExtent, nested::computeCrossAxisExtent,
                    nested::buildAt, LayoutEntry.members(nested.entries)));
            return this;
        }

        /** Appends a nested column. See {@link #addRow(Consumer)}. */
        public Builder addColumn(Consumer<Column.Builder> config) {
            Column.Builder nested = new Column.Builder(0, 0);
            config.accept(nested);
            entries.add(LayoutEntry.fromNested(nested::computeCrossAxisExtent, nested::computeMainAxisExtent,
                    nested::buildAt, LayoutEntry.members(nested.entries)));
            return this;
        }

        /**
         * Returns the children as a flat list, each with its {@code childX} and
         * {@code childY} set.
         *
         * <p>Don't keep, change or build the builder again afterwards; a second
         * {@code build()} is not supported.
         */
        public List<PanelElement> build() {
            requireSpacerWidth();
            return LayoutEntry.placeOnFirstRead(entries, () -> buildAt(originX, originY));
        }

        /** A spacer needs {@link #width(int)}: checked when the row is built or nested, not when it is measured. */
        void requireSpacerWidth() {
            if (width > 0) return;
            for (LayoutEntry e : entries) {
                if (e.isSpacer()) {
                    throw new IllegalStateException(
                            "Row: addSpacer() needs width(px) set, to know how much room it has to fill");
                }
            }
        }

        // Used by nesting and by build()

        /**
         * Width: the declared {@link #width(int)} when set (a nested row with a
         * spacer takes all of it), else the children's widths plus spacing.
         */
        int computeMainAxisExtent() {
            if (width > 0) return width;
            if (entries.isEmpty()) return 0;
            int sum = 0;
            for (int i = 0; i < entries.size(); i++) {
                sum += entries.get(i).width();
                if (i < entries.size() - 1) sum += spacing;
            }
            return sum;
        }

        /** Height: the tallest child. */
        int computeCrossAxisExtent() {
            int max = 0;
            for (LayoutEntry e : entries) {
                if (e.height() > max) max = e.height();
            }
            return max;
        }

        /**
         * Lays the row out from any origin, as when it is nested. From
         * {@link #build()} the origin is the row's own.
         */
        List<PanelElement> buildAt(int baseX, int baseY) {
            int crossExtent = computeCrossAxisExtent();
            int[] advance = resolveAdvances();
            List<PanelElement> result = new ArrayList<>();
            int x = baseX;
            for (int i = 0; i < entries.size(); i++) {
                LayoutEntry entry = entries.get(i);
                int y = switch (crossAlign) {
                    // FILL only applies to Column; a Row treats it as START.
                    // Filling height would need elements that can take a forced
                    // height, which TextLabel can't.
                    case START, FILL -> baseY;
                    case CENTER -> baseY + (crossExtent - entry.height()) / 2;
                    case END -> baseY + (crossExtent - entry.height());
                };
                result.addAll(entry.emitAt(x, y));
                x += advance[i];
                if (i < entries.size() - 1) x += spacing;
            }
            return result;
        }

        /**
         * How far to advance past each entry: its own width, except a spacer, which
         * gets an even share of what {@link #width} leaves after every other entry
         * and gap. Any remainder goes to the last spacer, so the row comes out at
         * exactly {@code width}.
         */
        private int[] resolveAdvances() {
            int n = entries.size();
            int[] advance = new int[n];
            int spacerCount = 0;
            for (int i = 0; i < n; i++) {
                advance[i] = entries.get(i).width();
                if (entries.get(i).isSpacer()) spacerCount++;
            }
            if (spacerCount == 0) return advance;   // a spacer has a width: requireSpacerWidth
            int fixedExtent = 0;
            for (int i = 0; i < n; i++) {
                fixedExtent += advance[i];
                if (i < n - 1) fixedExtent += spacing;
            }
            int leftover = Math.max(0, width - fixedExtent);
            int perSpacer = leftover / spacerCount;
            int remainder = leftover % spacerCount;
            int seen = 0;
            for (int i = 0; i < n; i++) {
                if (entries.get(i).isSpacer()) {
                    seen++;
                    advance[i] = perSpacer + (seen == spacerCount ? remainder : 0);
                }
            }
            return advance;
        }
    }
}
