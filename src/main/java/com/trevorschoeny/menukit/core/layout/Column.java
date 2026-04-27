package com.trevorschoeny.menukit.core.layout;

import com.trevorschoeny.menukit.core.PanelElement;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Build-time vertical layout helper. Mirrors {@link Row} on the vertical
 * axis: computes per-child {@code childY} positions for a sequence of
 * {@link ElementSpec}s under uniform spacing and a cross-axis (horizontal)
 * alignment policy. Emits a flat list of positioned
 * {@link PanelElement}s.
 *
 * <p>Same helper-not-container discipline as Row — Column does not
 * implement {@link PanelElement}, has no {@code render(...)} method, and
 * does not exist at runtime.
 *
 * <h3>Usage</h3>
 *
 * <pre>{@code
 * List<PanelElement> stack = Column.at(0, 0).spacing(2)
 *     .add(TextLabel.spec(Component.literal("Line 1")))
 *     .add(TextLabel.spec(Component.literal("Line 2")))
 *     .add(TextLabel.spec(Component.literal("Line 3")))
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
     * Begin a column layout at the given panel-local origin.
     *
     * @param originX the panel-local X coordinate where the column sits
     *                (cross-axis alignment is applied within
     *                [originX, originX + maxChildWidth])
     * @param originY the panel-local Y coordinate where the column's
     *                first child's top edge will be placed
     */
    public static Builder at(int originX, int originY) {
        return new Builder(originX, originY);
    }

    /**
     * Builder for a single column layout. Methods chain; terminate with
     * {@link #build()} to emit the positioned element list.
     */
    public static final class Builder {

        private final int originX;
        private final int originY;
        private int spacing = 0;
        private CrossAlign crossAlign = CrossAlign.START;
        private final List<LayoutEntry> entries = new ArrayList<>();

        Builder(int originX, int originY) {
            this.originX = originX;
            this.originY = originY;
        }

        /** Pixel spacing between adjacent children. Must be {@code >= 0}. */
        public Builder spacing(int px) {
            if (px < 0) {
                throw new IllegalArgumentException(
                        "spacing must be >= 0, got " + px);
            }
            this.spacing = px;
            return this;
        }

        /**
         * Cross-axis (horizontal) alignment policy. Default
         * {@link CrossAlign#START} aligns children to the column's
         * left edge.
         */
        public Builder crossAlign(CrossAlign align) {
            if (align == null) {
                throw new IllegalArgumentException("crossAlign must not be null");
            }
            this.crossAlign = align;
            return this;
        }

        /** Append an element to this column. */
        public Builder add(ElementSpec spec) {
            entries.add(LayoutEntry.fromSpec(spec));
            return this;
        }

        /** Append a nested column to this column. See {@link Row.Builder#addRow(Consumer)}. */
        public Builder addColumn(Consumer<Column.Builder> config) {
            Column.Builder nested = new Column.Builder(0, 0);
            config.accept(nested);
            int w = nested.computeCrossAxisExtent();
            int h = nested.computeMainAxisExtent();
            entries.add(LayoutEntry.fromNested(w, h, nested::buildAt));
            return this;
        }

        /** Append a nested row to this column. */
        public Builder addRow(Consumer<Row.Builder> config) {
            Row.Builder nested = new Row.Builder(0, 0);
            config.accept(nested);
            int w = nested.computeMainAxisExtent();
            int h = nested.computeCrossAxisExtent();
            entries.add(LayoutEntry.fromNested(w, h, nested::buildAt));
            return this;
        }

        /**
         * Finalize layout and emit positioned elements. Returns a flat
         * {@code List<PanelElement>} with {@code childX}/{@code childY}
         * computed for each child.
         */
        public List<PanelElement> build() {
            return buildAt(originX, originY);
        }

        // ── Internal — used by nesting and by build() ─────────────────

        /** Main-axis (vertical) extent: sum of child heights plus spacings. */
        int computeMainAxisExtent() {
            if (entries.isEmpty()) return 0;
            int sum = 0;
            for (int i = 0; i < entries.size(); i++) {
                sum += entries.get(i).height();
                if (i < entries.size() - 1) sum += spacing;
            }
            return sum;
        }

        /** Cross-axis (horizontal) extent: max child width. */
        int computeCrossAxisExtent() {
            int max = 0;
            for (LayoutEntry e : entries) {
                if (e.width() > max) max = e.width();
            }
            return max;
        }

        /**
         * Emit positioned elements at an arbitrary base origin — used by
         * outer-helper translation when this Column is nested.
         */
        List<PanelElement> buildAt(int baseX, int baseY) {
            int crossExtent = computeCrossAxisExtent();
            List<PanelElement> result = new ArrayList<>();
            int y = baseY;
            for (int i = 0; i < entries.size(); i++) {
                LayoutEntry entry = entries.get(i);
                int x = switch (crossAlign) {
                    case START -> baseX;
                    case CENTER -> baseX + (crossExtent - entry.width()) / 2;
                    case END -> baseX + (crossExtent - entry.width());
                };
                result.addAll(entry.emitAt(x, y));
                y += entry.height();
                if (i < entries.size() - 1) y += spacing;
            }
            return result;
        }
    }
}
