package com.trevlar.menukit.api.layout;

import com.trevlar.menukit.api.element.PanelElement;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Lays elements out top to bottom, the vertical counterpart of {@link Row}: it
 * positions each child with even spacing and a horizontal alignment, and returns a
 * flat list of positioned {@link PanelElement}s.
 *
 * <p>Measured on first use, as {@link Row} is: the positions are computed on
 * the first read of any emitted element's position, so a Column builds at mod
 * init, before the game's font exists.
 *
 * <p>Like Row, Column is a helper, not a container: it is not a
 * {@link PanelElement}, does not render, and is gone once built.
 *
 * <h3>Usage</h3>
 *
 * <pre>{@code
 * List<PanelElement> stack = Column.at(0, 0).spacing(2)
 *     .add(TextLabel.builder().text(Component.literal("Line 1")).build())
 *     .add(TextLabel.builder().text(Component.literal("Line 2")).build())
 *     .add(TextLabel.builder().text(Component.literal("Line 3")).build())
 *     .build();
 * }</pre>
 *
 * @see Row
 * @see ElementSpec
 * @see CrossAlign
 */
public final class Column {

    private Column() {}

    /**
     * Starts a column at a panel-local origin.
     *
     * @param originX panel-local X of the column; alignment applies within
     *                [originX, originX + widest child]
     * @param originY panel-local Y of the first child's top edge
     */
    public static Builder at(int originX, int originY) {
        return new Builder(originX, originY);
    }

    /** Builds one column. Finish with {@link #build()}. */
    public static final class Builder {

        private final int originX;
        private final int originY;
        private int spacing = 0;
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
         * Horizontal alignment. The default, {@link CrossAlign#START}, aligns
         * children to the column's left edge.
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

        /**
         * Appends an element that is already built, including under
         * {@link CrossAlign#FILL}. It must extend
         * {@link com.trevlar.menukit.api.element.AbstractPanelElement} (every
         * MenuKit element does) so the layout can move it; a bare custom
         * {@link PanelElement} throws at build time. See
         * {@link ElementSpec#of(PanelElement)}.
         */
        public Builder add(PanelElement element) {
            entries.add(LayoutEntry.fromElement(element));
            return this;
        }

        /** Appends a nested column. See {@link Row.Builder#addRow(Consumer)}. */
        public Builder addColumn(Consumer<Column.Builder> config) {
            Column.Builder nested = new Column.Builder(0, 0);
            config.accept(nested);
            entries.add(LayoutEntry.fromNested(nested::computeCrossAxisExtent, nested::computeMainAxisExtent,
                    nested::buildAt, LayoutEntry.members(nested.entries)));
            return this;
        }

        /** Appends a nested row. */
        public Builder addRow(Consumer<Row.Builder> config) {
            Row.Builder nested = new Row.Builder(0, 0);
            config.accept(nested);
            nested.requireSpacerWidth();
            entries.add(LayoutEntry.fromNested(nested::computeMainAxisExtent, nested::computeCrossAxisExtent,
                    nested::buildAt, LayoutEntry.members(nested.entries)));
            return this;
        }

        /**
         * Returns the children as a flat list, each with its {@code childX} and
         * {@code childY} set.
         */
        public List<PanelElement> build() {
            return LayoutEntry.placeOnFirstRead(entries, () -> buildAt(originX, originY));
        }

        // Used by nesting and by build()

        /** Height: the children's heights plus spacing. */
        int computeMainAxisExtent() {
            if (entries.isEmpty()) return 0;
            int sum = 0;
            for (int i = 0; i < entries.size(); i++) {
                sum += entries.get(i).height();
                if (i < entries.size() - 1) sum += spacing;
            }
            return sum;
        }

        /** Width: the widest child. */
        int computeCrossAxisExtent() {
            int max = 0;
            for (LayoutEntry e : entries) {
                if (e.width() > max) max = e.width();
            }
            return max;
        }

        /** Lays the column out from any origin, as when it is nested. */
        List<PanelElement> buildAt(int baseX, int baseY) {
            int crossExtent = computeCrossAxisExtent();
            List<PanelElement> result = new ArrayList<>();
            // FILL: collect the leaf elements to stretch afterwards. Nested rows and
            // columns are left out, since stretching their leaves would override
            // their own layout.
            List<PanelElement> fillTargets =
                    (crossAlign == CrossAlign.FILL) ? new ArrayList<>() : null;
            int y = baseY;
            for (int i = 0; i < entries.size(); i++) {
                LayoutEntry entry = entries.get(i);
                int x = switch (crossAlign) {
                    // FILL left-aligns like START, then stretches in the post-pass.
                    case START, FILL -> baseX;
                    case CENTER -> baseX + (crossExtent - entry.width()) / 2;
                    case END -> baseX + (crossExtent - entry.width());
                };
                List<PanelElement> emitted = entry.emitAt(x, y);
                result.addAll(emitted);
                if (fillTargets != null && entry.isLeaf()) {
                    fillTargets.addAll(emitted);
                }
                y += entry.height();
                if (i < entries.size() - 1) y += spacing;
            }
            // FILL: take the widest width the leaves report after emitting (a
            // Toggle's getWidth() is at least its label, so the spec width can be
            // smaller), then stretch every leaf to it. Widgets that support
            // fillWidth take the new width; auto-sized ones keep their own and stay
            // left-aligned.
            if (fillTargets != null && !fillTargets.isEmpty()) {
                int fillExtent = 0;
                for (PanelElement e : fillTargets) {
                    if (e.getWidth() > fillExtent) fillExtent = e.getWidth();
                }
                for (PanelElement e : fillTargets) {
                    e.fillWidth(fillExtent);
                }
            }
            return result;
        }
    }
}
