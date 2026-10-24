package com.trevlar.menukit.api.element;

import com.trevlar.menukit.inject.ContainerRegions;
import com.trevlar.menukit.inject.PresentedSlots;
import com.trevlar.menukit.mixin.AbstractContainerScreenAccessor;
import com.trevlar.menukit.core.Scroller;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * A grid of slots that scrolls inside its panel when it doesn't fit (Fluorite): as tall as its
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
 * library's element hides itself where its slot isn't on the menu). Either is read once a frame,
 * before the screen lays out, never per cell: layout asks each cell many times a frame.
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

    /** What the cells read this frame, worked out once ({@link #frame}): shared by the grid and every cell's {@code shown}. */
    private static final class Now {
        int live;
        boolean narrowed;
        /** While the live count is probed: every cell of the wide set answers by itself. */
        boolean counting;
        double scroll;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The grid itself: a scroll area that fits the room, its cells placed straight in it. */
    private static final class Grid extends ScrollContainer {
        private final Now now;
        private final int columns, count;
        private final boolean narrows;
        private final @Nullable IntSupplier liveCount;
        /** The full-width set, in index order: probed for the live count. */
        private final List<PanelElement> wide;
        /** Whether rows were given back at the last layout, so it scrolls. */
        private boolean scrolls;
        /** When it was last drawn, by {@code Util.getMillis()}. */
        private long drawn;
        /** Where on screen its content's top-left was drawn this frame, the scroll in it (for {@link #note}). */
        private int drawX, drawY;
        /** Each slot a cell of it has placed, by its index in the grid. */
        private final Map<Slot, Integer> seen = new WeakHashMap<>();
        /** A quick move landed in a slot it hasn't drawn: the slot, the page looked at, the scroll before. */
        private @Nullable Slot seeking;
        private int page;
        private double seekFrom;

        private Grid(ScrollContainer.Builder b, Now now, SlotScroll.Builder s, List<PanelElement> wide) {
            super(b);
            this.now = now;
            this.columns = s.columns;
            this.count = s.count;
            this.narrows = s.narrows;
            this.liveCount = s.liveCount;
            this.wide = wide;
            GRIDS.add(this);
            // Its live cells now, so a grid made while a screen lays out (a section made for a key
            // that just appeared) is laid out at its height, not empty until the next frame.
            frame();
        }

        /** The columns a row now: one fewer while a narrowing grid scrolls. */
        private int columnsNow() {
            return now.narrowed ? columns - 1 : columns;
        }

        /** The live rows' height now. */
        private int liveHeight() {
            int cols = columnsNow();
            return (now.live + cols - 1) / cols * CELL;
        }

        /**
         * Rows given back mean it scrolls. A narrowing grid narrows on the next frame: the overflow is
         * known only during layout, after the cells were placed.
         */
        @Override
        public int yieldHeight(int overflow) {
            int given = super.yieldHeight(overflow);
            scrolls = given > 0;
            return given;
        }

        @Override
        public void render(RenderContext ctx) {
            drawn = Util.getMillis();
            drawX = ctx.originX() + childX;
            drawY = ctx.originY() + childY;
            Grid outer = rendering;
            rendering = this;
            try {
                super.render(ctx);
            } finally {
                rendering = outer;
            }
        }

        /**
         * This frame, before the screen lays out: how many cells are live and which set shows them,
         * and one step of a search for a slot a quick move landed in.
         */
        private void frame() {
            if (liveCount != null) {
                now.live = Math.max(0, Math.min(count, liveCount.getAsInt()));
            } else {
                now.counting = true;
                int live = 0;
                while (live < count && wide.get(live).isVisible()) live++;
                now.counting = false;
                now.live = live;
            }
            now.narrowed = narrows && scrolls;
            if (seeking == null) return;
            if (reveal(seeking)) {
                seeking = null;
                return;
            }
            // Not drawn yet: the next page, until past the end; then back where it was.
            int view = Math.max(CELL, getHeight());
            if (page * view >= liveHeight()) {
                now.scroll = seekFrom;
                seeking = null;
                return;
            }
            now.scroll = page++ * view;
        }

        /** A cell placed {@code slot} while this grid draws: which cell it is, from where it sits. */
        private void note(Slot slot) {
            if (!(Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> screen)) return;
            AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
            int cols = columnsNow();
            // The slot's cell, from the content's top-left as drawn (moved up by the scroll).
            int col = Math.floorDiv(acc.mk$getLeftPos() + slot.x - 1 - drawX, CELL);
            int row = Math.floorDiv(acc.mk$getTopPos() + slot.y - 1 - drawY + (int) now.scroll, CELL);
            int index = row * cols + col;
            if (col >= 0 && col < cols && row >= 0 && index < count) seen.put(slot, index);
        }

        /** Scrolls the least to show {@code slot}'s row whole; whether it is one of this grid's. */
        private boolean reveal(Slot slot) {
            Integer index = seen.get(slot);
            if (index == null) return false;
            now.scroll = Scroller.intoView(now.scroll, index / columnsNow() * CELL, CELL, liveHeight(), getHeight());
            return true;
        }

        private boolean drawnLately() {
            return Util.getMillis() - drawn < 100;
        }
    }

    // Every grid, each frame; the slots their cells place; quick moves into them.

    /** Weak, so a grid nothing shows any more goes. */
    private static final Set<Grid> GRIDS = Collections.newSetFromMap(new WeakHashMap<>());
    /** The grid drawing now, so a slot its cell places is noted as its. */
    private static @Nullable Grid rendering;

    /** Every grid's live cells, each frame of a container screen before it lays out. Called once from client init. */
    @ApiStatus.Internal
    public static void init() {
        // Ahead of the region's own init phase, so this runs before the region lays the list out
        // from the grids' rows each frame, and once at init too, so the screen opens at its height.
        Identifier phase = Identifier.fromNamespaceAndPath("menukit", "slot_scroll");
        ScreenEvents.AFTER_INIT.addPhaseOrdering(phase, ContainerRegions.FIRST);
        ScreenEvents.AFTER_INIT.register(phase, (client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?>)) return;
            ScreenEvents.beforeExtract(screen).register((s, g, mx, my, a) -> frameAll());
            frameAll();
        });
    }

    private static void frameAll() {
        for (Grid grid : new ArrayList<>(GRIDS)) grid.frame();
    }

    /** For MenuKit's checks, which have no screen: one frame of every grid, as a screen's frame starts. */
    @ApiStatus.Internal
    @VisibleForTesting
    public static void frameForChecks() {
        frameAll();
    }

    /** {@code SlotClip.shown}: a cell of the grid drawing now, if any, placed {@code slot}. */
    @ApiStatus.Internal
    public static void noteShown(Slot slot) {
        if (rendering != null) rendering.note(slot);
    }

    /** Whether a grid is on screen: a quick move is watched for while one is. */
    @ApiStatus.Internal
    public static boolean anyShown() {
        for (Grid grid : GRIDS) if (grid.drawnLately()) return true;
        return false;
    }

    /**
     * A quick move landed in {@code slot}: a grid on screen that placed it shows it; else, when it
     * isn't the player's and isn't on screen, a grid with cells it hasn't drawn yet looks for it.
     *
     * @return whether a grid took it
     */
    @ApiStatus.Internal
    public static boolean quickMoved(Slot slot) {
        Objects.requireNonNull(slot, "slot");
        List<Grid> shown = new ArrayList<>();
        for (Grid grid : GRIDS) if (grid.drawnLately()) shown.add(grid);
        for (Grid grid : shown) if (grid.reveal(slot)) return true;
        // Not one a grid has drawn: only one that is nobody's on screen now (the player's never is a grid's).
        if (slot.container instanceof Inventory || PresentedSlots.lastPanel(slot) != null || onScreen(slot)) return false;
        for (Grid grid : shown) {
            if (grid.seen.size() >= grid.now.live) continue;
            grid.seeking = slot;
            grid.page = 0;
            grid.seekFrom = grid.now.scroll;
            return true;
        }
        return false;
    }

    /** Whether {@code slot} sits on the open screen (a parked slot doesn't). */
    private static boolean onScreen(Slot slot) {
        if (!(Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> screen)) return false;
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        int x = acc.mk$getLeftPos() + slot.x, y = acc.mk$getTopPos() + slot.y;
        return x > -CELL && y > -CELL && x < screen.width && y < screen.height;
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
            Now now = new Now();
            List<PanelElement> cells = new ArrayList<>(narrows ? 2 * count : count);
            List<PanelElement> wide = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int index = i;
                PanelElement c = cell.at(i, i % columns * CELL, i / columns * CELL,
                        () -> now.counting || !now.narrowed && index < now.live);
                wide.add(c);
                cells.add(c);
            }
            if (narrows) {
                int cols = columns - 1;
                for (int i = 0; i < count; i++) {
                    int index = i;
                    cells.add(cell.at(i, i % cols * CELL, i / cols * CELL,
                            () -> !now.counting && now.narrowed && index < now.live));
                }
            }
            ScrollContainer.Builder b = ScrollContainer.builder().size(columns * CELL, minRows * CELL)
                    .fitRoom(CELL).state(() -> now.scroll, v -> now.scroll = v).content(cells);
            // A narrowing grid keeps its lane inside its width: the column it drops leaves room for it.
            if (!narrows) b.laneOutside();
            return new Grid(b, now, this, wide);
        }
    }
}
