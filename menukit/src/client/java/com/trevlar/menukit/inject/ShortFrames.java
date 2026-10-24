package com.trevlar.menukit.inject;

import com.trevlar.menukit.api.element.PanelElement;
import com.trevlar.menukit.api.element.RenderContext;
import com.trevlar.menukit.api.element.ScrollContainer;
import com.trevlar.menukit.api.element.SlotRendering;
import com.trevlar.menukit.api.element.SlotScroll;
import com.trevlar.menukit.api.host.SlotClip;
import com.trevlar.menukit.api.panel.ContainerRegion;
import com.trevlar.menukit.api.panel.ContainerTabs.Edge;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.ScreenMatcher;
import com.trevlar.menukit.mixin.AbstractContainerScreenAccessor;
import com.trevlar.menukit.mixin.SlotAccessor;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

/**
 * {@code ScreenFrames.scrollWhenShort}: a vanilla screen's own container grid scrolls inside its
 * frame when the frame wouldn't fit the window (Fluorite). Off unless a mod turns it on for a screen.
 *
 * <p>It is the container region, fitted, with one section MenuKit makes itself: the screen's own
 * grid as a {@link SlotScroll} under the screen's title. So the frame, the clip, the claims, the
 * wheel, the shift-click reveal and the screen's bounds are the region's, and nothing here repeats
 * them. The section exists only while the screen is short ({@link #isShort}): on a window the frame
 * fits, the region isn't up and the screen is vanilla's, pixel for pixel.
 *
 * <p>Short means vanilla's frame, MenuKit's seams included ({@code ScreenFrames}), and the tab rows
 * on its outer edges don't fit between the region's margins: the shared centering rule's block
 * ({@link RegionLayout#up}). The fitted frame of a vanilla-sized grid under its title is vanilla's
 * frame, so the region then gives back just the rows the window lacks.
 *
 * <p>The grid's slots are vanilla's own, moved, as the region moves the inventory's: each one the
 * grid shows sits where it is drawn, so a recipe viewer or a mod reading {@code slot.x/y} finds it
 * there; one scrolled out of view is parked off the screen; and every one goes back to vanilla's
 * place when the screen fits again or closes. Render thread only.
 */
@ApiStatus.Internal
public final class ShortFrames {

    private ShortFrames() {}

    /** The section's kind id: one section, for the open screen's grid. */
    public static final String SECTION = "menukit:short_frame";
    /** Off the screen, as a slot library parks a slot no panel shows. */
    private static final int PARKED = -10000;

    private record Switch(ModeScope scope, ScreenMatcher screens) {}

    private static final List<Switch> SWITCHES = new CopyOnWriteArrayList<>();

    /** {@code ScreenFrames.scrollWhenShort}: the screens {@code screens} takes scroll their grid in {@code scope}'s mode. */
    public static synchronized void declare(ModeScope scope, ScreenMatcher screens) {
        boolean first = SWITCHES.isEmpty();
        SWITCHES.add(new Switch(scope, screens));
        if (!first) return;
        // The region on a short screen: fitted, one section for its grid, first in the list.
        ContainerRegions.declareFitted(ModeScope.BOTH, ScreenMatcher.where(ShortFrames::isShort));
        ContainerRegion.sections(SECTION, ModeScope.BOTH, s -> isShort(s) ? List.of(gridOf(s)) : null)
                .section((grid, b) -> b.title(() -> grid.title).content(grid.element).order(Integer.MIN_VALUE))
                .onMatching(ScreenMatcher.where(ShortFrames::on))
                .declare();
    }

    /** Whether a switch is on for {@code screen} now: in the player's mode, never the player's own inventory. */
    static boolean on(AbstractContainerScreen<?> screen) {
        if (screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) return false;
        for (Switch s : SWITCHES) if (s.scope().covers(PanelHost.currentMode()) && s.screens().matches(screen)) return true;
        return false;
    }

    /**
     * Whether {@code screen}'s grid scrolls now: a switch is on for it, it has a grid, and its frame
     * with the tab rows on its top and bottom edges is taller than the window less the region's margins.
     */
    static boolean isShort(AbstractContainerScreen<?> screen) {
        if (!on(screen) || gridOf(screen) == null) return false;
        return tooTall(frameHeight(screen), ContainerTabRows.height(Edge.CONTAINERS_TOP)
                + ContainerTabRows.height(Edge.INVENTORY_BOTTOM), screen.height);
    }

    /** Whether a frame {@code frame} tall with {@code rows} of tab rows on it leaves the window, {@code window} tall, within the margins. Pure. */
    @VisibleForTesting
    public static boolean tooTall(int frame, int rows, int window) {
        return frame + rows + RegionLayout.TOP_MARGIN + RegionLayout.BOTTOM_MARGIN > window;
    }

    /** Vanilla's frame height, seams included: the region keeps it while its union is the screen's. */
    private static int frameHeight(AbstractContainerScreen<?> screen) {
        ContainerRegions.State st = ContainerRegions.state;
        if (st.screen.get() == screen && st.vanilla != null) return st.vanilla.imageHeight();
        return ((AbstractContainerScreenAccessor) screen).mk$getImageHeight();
    }

    // The screen's own grid

    /**
     * A screen's own container grid: its slots in row order, as vanilla placed them, and the
     * section's element showing them.
     */
    static final class Grid {
        final List<Slot> slots;
        final int[] homeX, homeY;
        final Component title;
        final ScrollContainer element;
        /** Whether the grid has its slots now (parked or placed), rather than vanilla. */
        boolean holds;

        Grid(List<Slot> slots, int columns, Component title) {
            this.slots = slots;
            this.title = title;
            this.homeX = new int[slots.size()];
            this.homeY = new int[slots.size()];
            for (int i = 0; i < slots.size(); i++) {
                homeX[i] = slots.get(i).x;
                homeY[i] = slots.get(i).y;
            }
            this.element = SlotScroll.builder().columns(columns).liveCount(slots::size)
                    .cells(slots.size(), (i, x, y, shown) -> new Cell(this, i, x, y, shown)).build();
        }

        void park() {
            for (Slot slot : slots) place(slot, PARKED, PARKED);
            holds = true;
        }

        void home() {
            for (int i = 0; i < slots.size(); i++) place(slots.get(i), homeX[i], homeY[i]);
            holds = false;
        }
    }

    // Each screen's grid, found at its first init (a resize keeps it); empty for a screen with none.
    private static final Map<Screen, Optional<Grid>> GRIDS = new WeakHashMap<>();

    static @Nullable Grid gridOf(AbstractContainerScreen<?> screen) {
        return GRIDS.computeIfAbsent(screen, s -> Optional.ofNullable(findGrid(screen))).orElse(null);
    }

    /**
     * The screen's own container's slots, when they are a full grid of 18 px cells under the title
     * at the frame's content edge, as a chest's are (slot x 8, y 18); else {@code null}.
     */
    private static @Nullable Grid findGrid(AbstractContainerScreen<?> screen) {
        if (screen.getMenu() == null) return null;
        Container own = null;
        List<Slot> slots = new ArrayList<>();
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container instanceof Inventory || SlotScreenDispatcher.fireIsCreated(slot)) continue;
            if (own == null) own = slot.container;
            if (slot.container == own) slots.add(slot);
        }
        if (slots.isEmpty()) return null;
        slots.sort(Comparator.comparingInt((Slot s) -> s.y).thenComparingInt(s -> s.x));
        int columns = 0;
        while (columns < slots.size() && slots.get(columns).y == slots.getFirst().y) columns++;
        if (columns < 2 || slots.size() % columns != 0) return null;
        for (int i = 0; i < slots.size(); i++) {
            Slot s = slots.get(i);
            if (s.x != 8 + i % columns * SlotScroll.CELL || s.y != 18 + i / columns * SlotScroll.CELL) return null;
        }
        return new Grid(List.copyOf(slots), columns, screen.getTitle());
    }

    /**
     * Hooks every screen a switch may be on, after MenuKit's seams moved its slots and before the
     * region lays it out: the grid takes its slots while the screen is short and gives them back
     * once it fits or closes. Called once from {@code ScreenFrames.init}, in its init phase.
     */
    public static void init(Identifier phase) {
        ScreenEvents.AFTER_INIT.register(phase, (client, screen, w, h) -> {
            if (SWITCHES.isEmpty() || !(screen instanceof AbstractContainerScreen<?> acs)) return;
            ScreenEvents.beforeExtract(screen).register((s, g, mx, my, delta) -> sync(acs));
            ScreenEvents.remove(screen).register(s -> {
                Grid grid = known(acs);
                if (grid != null && grid.holds) grid.home();
            });
            sync(acs);
        });
    }

    /** The grid already found on {@code screen}, or {@code null}: none is looked for until a switch is on for it. */
    private static @Nullable Grid known(AbstractContainerScreen<?> screen) {
        Optional<Grid> grid = GRIDS.get(screen);
        return grid == null ? null : grid.orElse(null);
    }

    /** Parks the grid's slots the frame it starts scrolling (its cells place the ones it shows); home again once it stops. */
    private static void sync(AbstractContainerScreen<?> screen) {
        boolean scrolling = isShort(screen);
        Grid grid = known(screen);
        if (grid == null) return;
        if (scrolling && !grid.holds) grid.park();
        else if (!scrolling && grid.holds) grid.home();
    }

    private static void place(Slot slot, int x, int y) {
        SlotAccessor pos = (SlotAccessor) slot;
        pos.mk$setX(x);
        pos.mk$setY(y);
    }

    /**
     * One cell of a screen's own grid: its slot sits where the cell draws, reported for the clip
     * ({@code SlotClip.shown}), on the slot frame it draws; held out of view, parked. Vanilla draws
     * the slot and hit-tests it there, as it does every slot.
     */
    private record Cell(Grid grid, int index, int x, int y, BooleanSupplier shown) implements PanelElement {
        @Override public int getChildX() { return x; }
        @Override public int getChildY() { return y; }
        @Override public int getWidth() { return SlotRendering.DEFAULT_SIZE; }
        @Override public int getHeight() { return SlotRendering.DEFAULT_SIZE; }
        @Override public boolean isVisible() { return shown.getAsBoolean(); }
        @Override public boolean isElementOpaque() { return false; }

        @Override
        public void render(RenderContext ctx) {
            if (!(Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?> screen)) return;
            Slot slot = grid.slots.get(index);
            int sx = ctx.originX() + x, sy = ctx.originY() + y;
            AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
            place(slot, sx + SlotRendering.ITEM_INSET - acc.mk$getLeftPos(), sy + SlotRendering.ITEM_INSET - acc.mk$getTopPos());
            SlotClip.shown(ctx, slot);
            SlotRendering.drawSlotBackground(ctx.graphics(), sx, sy, SlotRendering.DEFAULT_SIZE, false);
        }

        @Override
        public void renderCulled(RenderContext ctx) {
            place(grid.slots.get(index), PARKED, PARKED);
        }
    }
}
