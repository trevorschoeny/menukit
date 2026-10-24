package com.trevlar.menukit.api.element;

import com.trevlar.menukit.inject.SlotScrollGrid;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * A grid of slots that scrolls inside its panel when it doesn't fit (7.4.0): as tall as its
 * live rows while there is room, shorter by whole rows when there isn't, never under its
 * minimum rows, scrolling past what shows. The slots are a slot library's elements (SlotKit's
 * {@code SlotElement}, one per cell); MenuKit names no slot type, so each cell is made by the
 * consumer from its index and its place.
 *
 * <pre>{@code
 * PanelElement grid = SlotScroll.builder()
 *         .cells(27, (i, x, y, shown) -> mySlot(i, x, y, shown))   // SlotElement.builder().slot(id, i).at(x, y).visibleWhen(shown).build()
 *         .build();
 * }</pre>
 *
 * <h3>Sizing</h3>
 * It is a {@link ScrollContainer} that fits the room ({@link ScrollContainer.Builder#fitRoom}) in
 * whole rows: in a {@link com.trevlar.menukit.api.panel.ContainerRegion} section the region asks
 * it to give back what the list would overflow the screen by, so it shows every live row and no
 * scrollbar while the window has the height, and scrolls once it is too short. Elsewhere nothing
 * asks, and it is as tall as its live rows. Never under {@link Builder#minRows} rows, so a box
 * with fewer live slots keeps a chest's height and the frame holds still as its rows change.
 *
 * <h3>Narrowing or a lane outside</h3>
 * A grid that may narrow ({@link Builder#narrows()}, a bundle) drops to one column fewer while it
 * scrolls, so those columns and the scrollbar fill the grid's width and the panel never widens for
 * it. One that must keep its columns (a chest) has the scrollbar's lane beside it while it
 * scrolls ({@link ScrollContainer.Builder#laneOutside}), and the region widens its frame by as much.
 * Fewer columns are more rows, so once narrowed a grid stays narrowed until the narrow grid fits.
 *
 * <h3>Live slots</h3>
 * Only the first {@link Builder#liveCount live} cells show, so the scroll ends at the last live
 * row. Without a live count, it is the leading cells that are visible on their own (a slot
 * library's element hides itself where its slot isn't on the menu). Either is read before the
 * screen lays out, once at each of its inits and once a frame while the grid is on screen, never
 * per cell: layout asks for the grid's height many times a frame. A live count that throws counts
 * as none live, and is logged once.
 *
 * <h3>Flat cells</h3>
 * Each cell is placed at its own place straight in the scroll area, not in rows of their own: a
 * nested row makes layout measure every cell many times a frame. A narrowing grid has a second
 * set placed a column fewer a row, one set shown at a time. A slot library's group element (one
 * element drawing many slots) draws nothing inside a scroll area; give one element per cell.
 *
 * <h3>A shift-click lands in view</h3>
 * A quick move into one of its slots scrolled out of view scrolls the grid there, as the container
 * region does for its own rows. A slot the grid hasn't drawn yet is looked for a viewport at a time,
 * and the grid goes back to where it was if no page has it.
 *
 * <p>{@link ScrollContainer#scrolls()} says whether it scrolls now: where a mod would do something else with the
 * wheel over it (switch tabs), the wheel is the grid's while it scrolls. In the container region
 * {@code ContainerRegion.scrolls(screen)} already counts a section's grid.
 */
public final class SlotScroll {

    private SlotScroll() {}

    /** A slot's cell, and a row's height. */
    public static final int CELL = 18;

    /**
     * Makes cell {@code index}: an element at {@code (x, y)} in the grid, shown while {@code shown}
     * holds (on top of whatever hides it on its own). Called once per cell, and once more per
     * cell for the narrow set of a grid that {@link Builder#narrows() narrows}.
     */
    @FunctionalInterface
    public interface Cell {
        PanelElement at(int index, int x, int y, BooleanSupplier shown);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Builds a {@link SlotScroll}. {@link #cells} is required. */
    public static final class Builder {
        private int count = -1;
        private @Nullable Cell cell;
        private int columns = 9, minRows = 3;
        private boolean narrows;
        private @Nullable IntSupplier liveCount;

        private Builder() {}

        /** Required: {@code count} cells, each made by {@code cell}, in row order. */
        public Builder cells(int count, Cell cell) {
            if (count <= 0) throw new IllegalArgumentException("SlotScroll needs at least one cell, got " + count);
            this.count = count;
            this.cell = Objects.requireNonNull(cell, "cell");
            return this;
        }

        /** Cells a row, at least 2; 9 by default, a vanilla container's. */
        public Builder columns(int columns) {
            if (columns < 2) throw new IllegalArgumentException("SlotScroll needs at least 2 columns, got " + columns);
            this.columns = columns;
            return this;
        }

        /** The fewest rows it is ever as tall as, however few are live or short the window; 3 by default, a chest's. */
        public Builder minRows(int rows) {
            if (rows <= 0) throw new IllegalArgumentException("SlotScroll minRows must be > 0, got " + rows);
            this.minRows = rows;
            return this;
        }

        /**
         * How many cells are live, read once a frame: only the first that many show. Without it,
         * the leading cells that are visible on their own.
         */
        public Builder liveCount(IntSupplier live) {
            this.liveCount = Objects.requireNonNull(live, "live");
            return this;
        }

        /**
         * One column fewer while it scrolls, the scrollbar in the column freed (a bundle), so it
         * is never wider than its columns. Without it the lane is beside the grid while it scrolls.
         */
        public Builder narrows() {
            this.narrows = true;
            return this;
        }

        /** The grid: a {@link ScrollContainer}, so {@link ScrollContainer#scrolls()} says whether it scrolls now. */
        public ScrollContainer build() {
            if (cell == null) throw new IllegalStateException("SlotScroll: cells(count, cell) is required");
            return SlotScrollGrid.build(count, cell, columns, minRows, narrows, liveCount);
        }
    }
}
