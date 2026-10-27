package com.trevlar.menukit.inject;

import com.trevlar.menukit.api.panel.ContainerTabs.Edge;
import com.trevlar.menukit.api.panel.ModeScope;
import com.trevlar.menukit.api.panel.Reference;
import com.trevlar.menukit.api.panel.ScreenMatcher;
import com.trevlar.menukit.mixin.AbstractContainerScreenAccessor;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

/**
 * Where a container screen's frame goes, its tab rows counted (7.4.0): the one centering
 * rule the container region's block and a screen's own frame share ({@link #up}), and on a
 * screen the region isn't up on, the frame moved from vanilla's place so that it and the rows
 * standing on it are centered as one block ({@link #centre}), or, where a mod asks the frame to
 * stay ({@link #declareStays}), kept at vanilla's place unless its rows wouldn't fit there
 * ({@link #stayingTop}). Everything that works from where
 * vanilla would have put the frame asks here: the region ({@link #vanillaFrame}), a stretched
 * screen's recipe book button ({@link #place}), a screen too tall for its window
 * ({@link #room}) and a swapped screen's moving frame ({@link #frame}).
 *
 * <p>How the frame moves: at the head of each init ({@code MKFrameCentringMixin}) the move
 * {@code dy} is worked out; vanilla's container screen code then reads the window as
 * {@code 2 * dy} taller ({@code MKFrameCentringHeightMixin}), so everything vanilla centers from
 * it (the frame, its image, its slots and labels, the recipe book and its button) moves
 * {@code dy} down together, and so does every widget placed from {@code topPos}.
 *
 * <p>Render thread only.
 */
@ApiStatus.Internal
public final class FrameCentring {

    private FrameCentring() {}

    // The one rule, pure

    /** The room a block has on a window {@code window} tall: the window less the region's margins. */
    public static int room(int window) {
        return window - RegionLayout.TOP_MARGIN - RegionLayout.BOTTOM_MARGIN;
    }

    /**
     * The one centering rule, for the region's block and a screen's own frame with its tab rows
     * alike: how far the block's top goes up from vanilla's {@code topPos} when the block is
     * {@code grow} px taller than vanilla's image (negative: shorter). The room still free once
     * grown is split evenly above and below (floor, as vanilla centers: an odd pixel goes below),
     * so with vanilla's image centered the block's top is {@code (screenHeight - block) / 2}.
     * Clamped so that neither end moves the other way from the block's growth (back, when it
     * grows), nor past its margin while it has room: where one side has less room than the
     * other, that side holds and the other moves until the block is centered.
     */
    public static int up(Reference vanilla, int screenHeight, int grow) {
        int imageBottom = vanilla.topPos() + vanilla.imageHeight();
        int roomBelow = Math.max(0, screenHeight - RegionLayout.BOTTOM_MARGIN - imageBottom);
        int roomAbove = Math.max(0, vanilla.topPos() - RegionLayout.TOP_MARGIN);
        int over = Math.floorDiv(roomAbove + roomBelow - grow, 2);
        return Math.max(Math.min(0, grow), Math.min(Math.max(0, grow), roomAbove - over));
    }

    /**
     * Where a screen's own frame goes ({@code topPos}) with a tab row {@code above} px tall on
     * its top edge and one {@code below} px on its bottom edge (0: none), the region down: the
     * frame and the rows are one block, centered by {@link #up} as the region's block is. With
     * no row it is vanilla's own {@code topPos}.
     *
     * @param vanilla the screen's bounds as vanilla centered them
     */
    public static int frameTop(Reference vanilla, int screenHeight, int above, int below) {
        return vanilla.topPos() - up(vanilla, screenHeight, above + below) + above;
    }

    /**
     * Where a screen's own frame goes ({@code topPos}) with rows {@code above} and {@code below}
     * it while it stays ({@code ContainerTabs.frameStaysWhen}): vanilla's own {@code topPos},
     * moved only as far as brings the rows inside the region's margins, the top row down or the
     * bottom one up. Where the frame and its rows don't fit the window at all (the block a
     * {@code scrollsWhenShort} grid scrolls for), or with no row, it is {@link #frameTop}'s.
     */
    public static int stayingTop(Reference vanilla, int screenHeight, int above, int below) {
        int lo = RegionLayout.TOP_MARGIN + above;
        int hi = screenHeight - RegionLayout.BOTTOM_MARGIN - below - vanilla.imageHeight();
        if (above + below == 0 || lo > hi) return frameTop(vanilla, screenHeight, above, below);
        return Math.max(lo, Math.min(hi, vanilla.topPos()));
    }

    /**
     * How far a frame {@code imageHeight} tall on a window {@code window} tall moves down from
     * vanilla's place for rows {@code above} and {@code below} it ({@link #frameTop}, or
     * {@link #stayingTop} while it {@code stays}), so that vanilla's own centring from the taller
     * window puts its top there, odd pixels and windows shorter than the frame included.
     */
    public static int dy(int window, int imageHeight, int above, int below, boolean stays) {
        int n = window - imageHeight, top = n / 2;
        Reference vanilla = new Reference(0, top, 0, imageHeight);
        int target = stays ? stayingTop(vanilla, window, above, below) : frameTop(vanilla, window, above, below);
        // Vanilla centres from the window read 2 * dy taller, (n + 2 * dy) / 2, rounding toward
        // zero: on a window shorter than the frame an odd n rounds the other way once the sum
        // turns positive, a pixel off the target. Stepped until vanilla's own sum lands on it.
        int dy = target - top;
        while ((n + 2 * dy) / 2 < target) dy++;
        while ((n + 2 * dy) / 2 > target) dy--;
        return dy;
    }

    // Frames that stay

    private record Stay(ModeScope scope, ScreenMatcher screens, BooleanSupplier when) {}

    private static final List<Stay> STAYS = new CopyOnWriteArrayList<>();

    /** {@code ContainerTabs.frameStaysWhen}: the screens {@code screens} takes keep vanilla's place in {@code scope}'s mode while {@code when}. */
    public static void declareStays(ModeScope scope, ScreenMatcher screens, BooleanSupplier when) {
        STAYS.add(new Stay(scope, screens, when));
    }

    /** Whether {@code screen}'s frame stays at vanilla's place now: a declaration takes it, in the player's mode, and its condition holds. */
    static boolean stays(AbstractContainerScreen<?> screen) {
        for (Stay s : STAYS) {
            if (s.scope().covers(PanelHost.currentMode()) && s.screens().matches(screen)
                    && Guard.test(s.when(), "frameStaysWhen", s.screens().toString())) return true;
        }
        return false;
    }

    // Each screen's own frame

    /** Each container screen's centering at its last init: the frame's move down from vanilla's place. */
    private static final Map<Screen, Integer> CENTRED = new WeakHashMap<>();

    /**
     * At the head of each of {@code screen}'s inits, before vanilla centers it: how far its frame
     * goes down from vanilla's place so that the frame and the rows standing on it now, the top
     * one above it and the bottom one below, are centered as one block, or, while it stays
     * ({@link #stays}), as near vanilla's place as they fit. No row, the region up
     * on it, or a screen of a mod's own class ({@link #vanillas}): vanilla's place. The rows are measured ({@code ContainerTabRows.settledHeights}),
     * not chosen: nothing slides for it.
     */
    public static void centre(AbstractContainerScreen<?> screen) {
        if (!vanillas(screen)) {
            CENTRED.remove(screen);
            return;
        }
        int above = 0, below = 0;
        boolean regionUp = ContainerRegions.state.geometry != null && ContainerRegions.state.screen.get() == screen;
        if (ContainerTabRows.carries(screen) && !regionUp) {
            int[] h = ContainerTabRows.settledHeights(screen);
            above = h[Edge.CONTAINERS_TOP.ordinal()];
            below = h[Edge.INVENTORY_BOTTOM.ordinal()];
        }
        CENTRED.put(screen, dyNow(screen, above, below));
    }

    /** {@code screen}'s move down for rows {@code above} and {@code below}, as it stays or centers now. */
    private static int dyNow(AbstractContainerScreen<?> screen, int above, int below) {
        int imageHeight = ((AbstractContainerScreenAccessor) screen).mk$getImageHeight();
        return dy(screen.height, imageHeight, above, below, stays(screen));
    }

    /**
     * Whether {@code screen} is one of vanilla's own container screens, every class from its own up
     * to {@code AbstractContainerScreen} vanilla's: the ones {@code MKFrameCentringHeightMixin}
     * covers, where every place worked out from the window moves with the frame. A mod's own
     * class (a subclass of vanilla's included) may work out its image's place from the window
     * itself, {@code (height - imageHeight) / 2}, which the mixin doesn't reach, so its image would
     * part from its slots: its frame stays where vanilla centers it, rows or not.
     */
    static boolean vanillas(AbstractContainerScreen<?> screen) {
        return VANILLA.get(screen.getClass());
    }

    private static final ClassValue<Boolean> VANILLA = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            for (Class<?> c = type; c != null && c != AbstractContainerScreen.class; c = c.getSuperclass()) {
                if (!c.getName().startsWith("net.minecraft.")) return false;
            }
            return true;
        }
    };

    /** How far {@code screen}'s frame is moved down from vanilla's place for its tab rows ({@link #centre}); 0 for none. */
    public static int centring(Screen screen) {
        Integer c = CENTRED.get(screen);
        return c == null ? 0 : c;
    }

    /**
     * Whether {@code screen}'s frame, placed at its last init, goes elsewhere with rows
     * {@code above} and {@code below} and its {@link #stays} switch as they are now, so it is laid
     * out again. A row that comes or goes without moving the frame (one that fits above a frame
     * that stays) lays nothing out.
     */
    static boolean stale(AbstractContainerScreen<?> screen, int above, int below) {
        Integer c = CENTRED.get(screen);
        return c != null && c != dyNow(screen, above, below);
    }

    /** {@code screen}'s frame as it stands: its bounds now. */
    public static Reference frame(AbstractContainerScreen<?> screen) {
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        return new Reference(acc.mk$getLeftPos(), acc.mk$getTopPos(), acc.mk$getImageWidth(), acc.mk$getImageHeight());
    }

    /** {@code screen}'s frame where vanilla put it, without the move for its tab rows: what the region lays out from. */
    static Reference vanillaFrame(AbstractContainerScreen<?> screen) {
        Reference f = frame(screen);
        return new Reference(f.leftPos(), f.topPos() - centring(screen), f.imageWidth(), f.imageHeight());
    }

    /**
     * {@code y}, a place vanilla worked out on the screen as if it were its own size (the recipe
     * book button's, from the window's middle), moved to the same place in the stretched frame
     * ({@link ScreenSeams}). Vanilla works it out from the window as its container screen code
     * reads it, taller while the frame is centered with tab rows, so it has moved with the frame
     * already.
     */
    public static int place(Screen screen, int y) {
        int by = ScreenSeams.by(screen);
        if (by == 0 && !ScreenSeams.stretched(screen)) return y;
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        // topPos is vanilla's place for the stretched size in the window as vanilla read it: the row is from vanilla's own.
        int vanillaTop = (screen.height + 2 * centring(screen) - acc.mk$getImageHeight() + by) / 2;
        int row = y - vanillaTop;
        // Assumes the place was worked out from the window (vanilla's recipe screens do); one from topPos would move twice
        return acc.mk$getTopPos() + row + ScreenSeams.shift(screen, row);
    }
}
