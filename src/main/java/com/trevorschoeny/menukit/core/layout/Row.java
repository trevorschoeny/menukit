package com.trevorschoeny.menukit.core.layout;

import com.trevorschoeny.menukit.core.PanelElement;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Build-time horizontal layout helper. Computes per-child {@code childX}
 * positions for a sequence of {@link ElementSpec}s under uniform spacing
 * and a cross-axis alignment policy. Emits a flat list of positioned
 * {@link PanelElement}s to the consumer.
 *
 * <p>Row exists only during the {@code .build()} call chain. After
 * {@code build()} returns, the Row builder discharges its job and is
 * dead; no Row reference should be retained for "relayout." The emitted
 * elements have their positions baked in (per {@link PanelElement}'s
 * "fixed at construction; never mutated" contract) and are added to a
 * {@link com.trevorschoeny.menukit.core.Panel} like any other elements.
 *
 * <p><b>Helper, not container.</b> Row does NOT implement
 * {@link PanelElement}, does NOT have a {@code render(...)} method, and
 * does NOT exist at runtime. The Panel renders the emitted elements
 * directly with no indirection — preserving "Panel is the ceiling of
 * composition" (THESIS).
 *
 * <h3>Usage</h3>
 *
 * <pre>{@code
 * List<PanelElement> buttonRow = Row.at(20, 30).spacing(4)
 *     .add(Button.spec(60, 20, Component.literal("OK"), this::onConfirm))
 *     .add(Button.spec(60, 20, Component.literal("Cancel"), this::onCancel))
 *     .build();
 *
 * Panel p = new Panel("confirm", buttonRow, true, PanelStyle.RAISED, ...);
 * }</pre>
 *
 * <h3>Nesting</h3>
 *
 * Row supports nesting via {@link Builder#addRow(Consumer)} and
 * {@link Builder#addColumn(Consumer)}. Nested helpers compose: each
 * helper emits a flat list of positioned elements relative to its own
 * origin; outer helpers translate the inner list by their computed
 * child offset. After {@code build()}, the result is a flat list with
 * all positions computed — no nested helper objects survive.
 *
 * @see Column
 * @see ElementSpec
 * @see CrossAlign
 */
public final class Row {

    private Row() {}

    /**
     * Begin a row layout at the given panel-local origin.
     *
     * @param originX the panel-local X coordinate where the row's first
     *                child's left edge will be placed
     * @param originY the panel-local Y coordinate where the row sits
     *                (cross-axis alignment is applied within
     *                [originY, originY + maxChildHeight])
     */
    public static Builder at(int originX, int originY) {
        return new Builder(originX, originY);
    }

    /**
     * Builder for a single row layout. Methods chain; terminate with
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
         * Cross-axis (vertical) alignment policy. Default {@link CrossAlign#START}
         * aligns children to the row's top edge.
         */
        public Builder crossAlign(CrossAlign align) {
            if (align == null) {
                throw new IllegalArgumentException("crossAlign must not be null");
            }
            this.crossAlign = align;
            return this;
        }

        /** Append an element to this row. */
        public Builder add(ElementSpec spec) {
            entries.add(LayoutEntry.fromSpec(spec));
            return this;
        }

        /**
         * Append a nested row to this row. The nested row's elements are
         * laid out per its own spacing + alignment, then translated into
         * this row's main-axis position. The nested row's bounding
         * dimensions ({@code totalWidth} x {@code maxHeight}) determine
         * how much main-axis space it occupies in this row.
         */
        public Builder addRow(Consumer<Row.Builder> config) {
            Row.Builder nested = new Row.Builder(0, 0);
            config.accept(nested);
            int w = nested.computeMainAxisExtent();
            int h = nested.computeCrossAxisExtent();
            entries.add(LayoutEntry.fromNested(w, h, nested::buildAt));
            return this;
        }

        /** Append a nested column to this row. See {@link #addRow(Consumer)}. */
        public Builder addColumn(Consumer<Column.Builder> config) {
            Column.Builder nested = new Column.Builder(0, 0);
            config.accept(nested);
            int w = nested.computeCrossAxisExtent();
            int h = nested.computeMainAxisExtent();
            entries.add(LayoutEntry.fromNested(w, h, nested::buildAt));
            return this;
        }

        /**
         * Finalize layout and emit positioned elements. Returns a flat
         * {@code List<PanelElement>} with {@code childX}/{@code childY}
         * computed for each child.
         *
         * <p>After this call, the Builder has discharged its job. Do not
         * retain or mutate it; do not re-invoke {@code build()} expecting
         * different results (the entries list is mutable internally and
         * the second call would emit the same layout again — but that's
         * not a supported use case).
         */
        public List<PanelElement> build() {
            return buildAt(originX, originY);
        }

        // ── Internal — used by nesting and by build() ─────────────────

        /** Main-axis (horizontal) extent: sum of child widths plus spacings. */
        int computeMainAxisExtent() {
            if (entries.isEmpty()) return 0;
            int sum = 0;
            for (int i = 0; i < entries.size(); i++) {
                sum += entries.get(i).width();
                if (i < entries.size() - 1) sum += spacing;
            }
            return sum;
        }

        /** Cross-axis (vertical) extent: max child height. */
        int computeCrossAxisExtent() {
            int max = 0;
            for (LayoutEntry e : entries) {
                if (e.height() > max) max = e.height();
            }
            return max;
        }

        /**
         * Emit positioned elements at an arbitrary base origin — used by
         * outer-helper translation when this Row is nested. When called
         * directly (via {@link #build()}), {@code baseX}/{@code baseY}
         * are this row's declared origin.
         */
        List<PanelElement> buildAt(int baseX, int baseY) {
            int crossExtent = computeCrossAxisExtent();
            List<PanelElement> result = new ArrayList<>();
            int x = baseX;
            for (int i = 0; i < entries.size(); i++) {
                LayoutEntry entry = entries.get(i);
                int y = switch (crossAlign) {
                    case START -> baseY;
                    case CENTER -> baseY + (crossExtent - entry.height()) / 2;
                    case END -> baseY + (crossExtent - entry.height());
                };
                result.addAll(entry.emitAt(x, y));
                x += entry.width();
                if (i < entries.size() - 1) x += spacing;
            }
            return result;
        }
    }
}
