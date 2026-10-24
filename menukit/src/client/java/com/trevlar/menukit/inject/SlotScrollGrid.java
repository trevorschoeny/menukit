package com.trevlar.menukit.inject;

import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.api.element.ScrollContainer;
import com.trevlar.menukit.api.element.SlotScroll;
import com.trevlar.menukit.core.Scroller;
import com.trevlar.menukit.mixin.AbstractContainerScreenAccessor;

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
import java.util.function.IntSupplier;

/**
 * A {@link SlotScroll} grid (Fluorite; the public face and what it does are
 * {@code api.element.SlotScroll}): a scroll area that fits the room, its cells placed straight in
 * it, and every grid's live cells read once a frame, the slots their cells place, and quick moves
 * into them. Render thread only.
 */
@ApiStatus.Internal
public final class SlotScrollGrid extends ScrollContainer {

    private static final int CELL = SlotScroll.CELL;

    /** What the cells read this frame, worked out once ({@link #frame}): shared by the grid and every cell's {@code shown}. */
    private static final class Now {
        int live;
        boolean narrowed;
        /** While the live count is probed: every cell of the wide set answers by itself. */
        boolean counting;
        double scroll;
    }

    /** {@code SlotScroll.Builder.build}: {@code count} cells made by {@code cell}, {@code columns} a row, never under {@code minRows}. */
    public static ScrollContainer build(int count, SlotScroll.Cell cell, int columns, int minRows, boolean narrows,
                                        @Nullable IntSupplier liveCount) {
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
        return new SlotScrollGrid(b, now, count, columns, narrows, liveCount, wide);
    }


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

    private SlotScrollGrid(ScrollContainer.Builder b, Now now, int count, int columns, boolean narrows,
                           @Nullable IntSupplier liveCount, List<PanelElement> wide) {
        super(b);
        this.now = now;
        this.columns = columns;
        this.count = count;
        this.narrows = narrows;
        this.liveCount = liveCount;
        this.wide = wide;
        GRIDS.add(this);
        // Its live cells now, so a grid made while a screen lays out (a section made for a key
        // that just appeared) is laid out at its height, not empty until the next frame. Not
        // before any screen is open, or while the client is still starting (a mod building its
        // content at init, when the GUI isn't made yet): slots aren't there to count, and the
        // first screen's init frames every grid anyway. (No client at all is MenuKit's own
        // headless checks, which count with a live count.)
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui != null && mc.gui.screen() != null) frame();
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
        SlotScrollGrid outer = rendering;
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

    // Every grid, each frame; the slots their cells place; quick moves into them.

    /** Weak, so a grid nothing shows any more goes. */
    private static final Set<SlotScrollGrid> GRIDS = Collections.newSetFromMap(new WeakHashMap<>());
    /** The grid drawing now, so a slot its cell places is noted as its. */
    private static @Nullable SlotScrollGrid rendering;

    /** Every grid's live cells, each frame of a container screen before it lays out. Called once from client init. */
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
        for (SlotScrollGrid grid : new ArrayList<>(GRIDS)) grid.frame();
    }

    /** For MenuKit's checks, which have no screen: one frame of every grid, as a screen's frame starts. */
    @VisibleForTesting
    public static void frameForChecks() {
        frameAll();
    }

    /** {@code SlotClip.shown}: a cell of the grid drawing now, if any, placed {@code slot}. */
    public static void noteShown(Slot slot) {
        if (rendering != null) rendering.note(slot);
    }

    /** Whether a grid is on screen: a quick move is watched for while one is. */
    public static boolean anyShown() {
        for (SlotScrollGrid grid : GRIDS) if (grid.drawnLately()) return true;
        return false;
    }

    /**
     * A quick move landed in {@code slot}: a grid on screen that placed it shows it; else, when it
     * isn't the player's and isn't on screen, a grid with cells it hasn't drawn yet looks for it.
     *
     * @return whether a grid took it
     */
    public static boolean quickMoved(Slot slot) {
        Objects.requireNonNull(slot, "slot");
        List<SlotScrollGrid> shown = new ArrayList<>();
        for (SlotScrollGrid grid : GRIDS) if (grid.drawnLately()) shown.add(grid);
        for (SlotScrollGrid grid : shown) if (grid.reveal(slot)) return true;
        // Not one a grid has drawn: only one that is nobody's on screen now (the player's never is a grid's).
        if (slot.container instanceof Inventory || PresentedSlots.lastPanel(slot) != null || onScreen(slot)) return false;
        for (SlotScrollGrid grid : shown) {
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
}
