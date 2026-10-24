package com.trevlar.menukit.api.panel;

import com.trevlar.menukit.api.Declarations;
import com.trevlar.menukit.inject.ContainerRegions;
import com.trevlar.menukit.inject.ContainerTabRows;
import com.trevlar.menukit.inject.RegionLayout;
import com.trevlar.menukit.mixin.AbstractContainerScreenAccessor;
import com.trevlar.menukit.mixin.SlotAccessor;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A vanilla container screen made taller or shorter at a seam (Fluorite): its frame gains
 * (or loses) {@code by} rows at row {@code seam} of its own layout, so screens of nearly one
 * size can be made exactly one, and a tab row standing on their frames doesn't move as one
 * opens in place of another.
 *
 * <pre>{@code
 * // A mod's screen, 170 tall, made 166 like vanilla's: four rows out of the gap above its inventory.
 * ScreenFrames.stretch(MyBoxScreen.class, 76, -4);
 * }</pre>
 *
 * <h3>Vanilla's screens are one size already</h3>
 * MenuKit makes every vanilla container screen whose frame is nearly 166 tall exactly 166,
 * the size of nearly all of them (the inventory, the crafting table, the furnaces, the
 * dispenser, the enchanting table, the anvil and the rest), with no declaration:
 * <ul>
 *   <li><b>The shulker box</b>, 167 tall with an image of 166, loses the blank row under its image.</li>
 *   <li><b>The 3-row chest</b> (chests, barrels, the ender chest), 168 tall with an image of
 *       167, loses that blank row and one row of the gap under its slots, so its inventory
 *       slots and label sit where the shulker box's do, its inventory where every 166 screen's is.</li>
 *   <li><b>Every other chest size</b> (the double chest's 222) loses only its blank row.</li>
 * </ul>
 * So a vanilla screen's frame is exactly what it draws, and the moving frame of a screen
 * opened in place of another lands on the art. A mod's own seam is added to MenuKit's.
 *
 * <ul>
 *   <li><b>The screen's size</b> is vanilla's plus {@code by}, centered by vanilla as any
 *       screen of that size is, so it sits exactly where a vanilla screen of that size does:
 *       the inventory and a shulker box share one frame on every window.</li>
 *   <li><b>Above the seam</b> everything keeps its place in the frame: slots, labels, the
 *       player model, the recipe book button. <b>From the seam down</b> everything is
 *       {@code by} lower in the frame: slots (another mod's added to the menu included) and
 *       the "Inventory" label.</li>
 *   <li><b>The screen's own image</b> draws in parts with the seam row repeated
 *       {@code by} times between them, or with {@code -by} rows from the seam left out.
 *       OptiGUI's swap and a resource pack's texture apply to each part as to the whole.</li>
 *   <li>Exactly the class given; a subclass keeps its own size. While the
 *       {@link ContainerRegion container region} takes the screen's image, its panels draw in
 *       place of the image; the region lays out from the stretched size, slots and label.</li>
 *   <li>Another mod reading the screen's bounds ({@code topPos}, {@code imageHeight}) reads
 *       the stretched frame; one placing something by vanilla's fixed offsets from the top,
 *       below the seam, is {@code by} off.</li>
 * </ul>
 *
 * <p>Declare from your client initializer: frames freeze with every other declaration.
 */
public final class ScreenFrames {

    private ScreenFrames() {}

    private record Seam(int at, int by) {}

    /** A screen's seams, top down in vanilla's own rows, and how many rows they add in all. */
    private record Seams(List<Seam> list, int by) {

        /** How far vanilla's row {@code row} moves: every seam at or above it. */
        int shift(int row) {
            int s = 0;
            for (Seam m : list) if (m.at() <= row) s += m.by();
            return s;
        }
    }

    private static final Map<Class<?>, Seam> SEAMS = new ConcurrentHashMap<>();

    /**
     * Stretches every {@code screen} by {@code by} rows at row {@code seam} of its frame
     * (negative to shrink it, leaving out rows {@code seam} to {@code seam - by - 1}).
     *
     * @throws IllegalStateException after declarations froze, or when the class is already stretched
     */
    public static synchronized void stretch(Class<? extends AbstractContainerScreen<?>> screen, int seam, int by) {
        Objects.requireNonNull(screen, "screen");
        Declarations.requireOpen("ScreenFrames.stretch(" + screen.getSimpleName() + ")");
        if (by == 0 || seam <= 0) throw new IllegalArgumentException("ScreenFrames.stretch needs a seam inside the frame and a non-zero size");
        if (SEAMS.putIfAbsent(screen, new Seam(seam, by)) != null)
            throw new IllegalStateException("ScreenFrames: " + screen.getName() + " is already stretched");
    }

    /**
     * MenuKit's own seams on a vanilla screen (the class doc's list): the blank row vanilla
     * leaves under the shulker box's and every chest's image, and on the 3-row chest one row
     * of the gap under its slots too. Exactly vanilla's classes.
     */
    private static List<Seam> vanilla(Screen screen) {
        if (screen.getClass() == ShulkerBoxScreen.class) return List.of(new Seam(166, -1));
        if (screen.getClass() == ContainerScreen.class) {
            int rows = ((ContainerScreen) screen).getMenu().getRowCount();
            // Vanilla's frame is 114 + 18 a row; its image (rows * 18 + 17, then the inventory's 96) a row less.
            int frame = 113 + rows * 18;
            Seam blank = new Seam(frame, -1);
            // Then the standard size's rule, the fitted frame's too: row 18 + 18 a row is plain
            // gap, under the grid's last row and over the "Inventory" label.
            int trim = RegionLayout.gapTrim(frame);
            return trim > 0 ? List.of(new Seam(18 + rows * 18, -trim), blank) : List.of(blank);
        }
        return List.of();
    }

    // Each screen's seams, from its first init on (a screen grows once, a resize initializes it again), and how far each slot was moved.
    private static final Map<Screen, Seams> FRAMES = new WeakHashMap<>();
    private static final Map<Slot, Integer> MOVED = new WeakHashMap<>();

    private static @Nullable Seams of(Screen screen) {
        return FRAMES.get(screen);
    }

    /** The after-init phase the slots move in: before the region's, so it lays out from where they are. */
    private static final Identifier PHASE = Identifier.fromNamespaceAndPath("menukit", "screen_frames");

    /** Hooks every stretched screen's size before vanilla centers it, and its slots each frame. Called once from client init. */
    @ApiStatus.Internal
    public static void init() {
        ScreenEvents.BEFORE_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?>) || FRAMES.containsKey(screen)) return;
            List<Seam> list = new ArrayList<>(vanilla(screen));
            Seam declared = SEAMS.get(screen.getClass());
            if (declared != null) list.add(declared);
            if (list.isEmpty()) return;
            list.sort(Comparator.comparingInt(Seam::at));
            Seams seams = new Seams(List.copyOf(list), list.stream().mapToInt(Seam::by).sum());
            FRAMES.put(screen, seams);
            AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
            acc.mk$setImageHeight(acc.mk$getImageHeight() + seams.by());
            acc.mk$setInventoryLabelY(acc.mk$getInventoryLabelY() + seams.shift(acc.mk$getInventoryLabelY()));
        });
        ScreenEvents.AFTER_INIT.addPhaseOrdering(PHASE, ContainerRegions.FIRST);
        ScreenEvents.AFTER_INIT.register(PHASE, (client, screen, w, h) -> {
            Seams seams = of(screen);
            if (seams == null) return;
            AbstractContainerScreen<?> acs = (AbstractContainerScreen<?>) screen;
            ScreenEvents.beforeExtract(screen).register((s, g, mx, my, delta) -> placeSlots(acs, seams));
            // The menu can outlive the screen (the player's inventory menu does): its slots go back.
            ScreenEvents.remove(screen).register(s -> unplaceSlots());
            placeSlots(acs, seams);
        });
    }

    /**
     * Every slot of the menu moved by the seams above it, each moved once, however late it
     * joined the menu. Before the region moves any (this phase, and this frame hook, come
     * first), so a slot is seen where vanilla put it.
     */
    private static void placeSlots(AbstractContainerScreen<?> screen, Seams seams) {
        for (Slot slot : screen.getMenu().slots) {
            if (MOVED.containsKey(slot)) continue;
            int by = seams.shift(slot.y);
            if (by == 0) continue;
            ((SlotAccessor) slot).mk$setY(slot.y + by);
            MOVED.put(slot, by);
        }
    }

    private static void unplaceSlots() {
        for (Map.Entry<Slot, Integer> e : MOVED.entrySet()) ((SlotAccessor) e.getKey()).mk$setY(e.getKey().y - e.getValue());
        MOVED.clear();
    }

    /**
     * {@code y}, a place vanilla worked out on the screen as if it were its own size (the
     * recipe book button's, from the window's middle), moved to the same place in the
     * stretched frame. Vanilla works it out from the window as its container screen code reads
     * it, taller while the frame is centered with tab rows ({@code ContainerTabRows.centre}),
     * so it has moved with the frame already.
     */
    @ApiStatus.Internal
    public static int place(Screen screen, int y) {
        Seams seams = of(screen);
        if (seams == null) return y;
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        // topPos is vanilla's place for the stretched size in the window as vanilla read it: the row is from vanilla's own.
        int vanillaTop = (screen.height + 2 * ContainerTabRows.centring(screen) - acc.mk$getImageHeight() + seams.by()) / 2;
        int row = y - vanillaTop;
        // ponytail: assumes the place was worked out from the window (vanilla's recipe screens do); one from topPos would move twice
        return acc.mk$getTopPos() + row + seams.shift(row);
    }

    /** Set while the image's parts are being drawn, so they draw as given. */
    private static boolean drawingParts;

    /**
     * At the head of a texture blit that is {@code screen}'s own image: draws it in its parts
     * around the seams and says so, or {@code false} to let it draw as given.
     */
    @ApiStatus.Internal
    public static boolean drawParts(Screen screen, GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier texture,
                                    int x, int y, float u, float v, int width, int height, int uWidth, int vHeight,
                                    int textureWidth, int textureHeight, int color) {
        Seams seams = of(screen);
        if (seams == null || drawingParts || ContainerRegions.replacesImage(screen)) return false;
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        int top = acc.mk$getTopPos(), vanillaHeight = acc.mk$getImageHeight() - seams.by();
        // Rows of the frame as vanilla laid it out, this blit's from a to b; nothing past vanilla's own height.
        int a = y - top, b = Math.min(a + height, vanillaHeight);
        if (vHeight != height) {
            // ponytail: a scaled blit moves whole, by the seams above where it starts
            int by = seams.shift(a);
            if (by == 0) return false;
            g.blit(pipeline, texture, x, y + by, u, v, width, height, uWidth, vHeight, textureWidth, textureHeight, color);
            return true;
        }
        drawingParts = true;
        try {
            // Down the blit's rows: each run between seams at its row moved by the seams above
            // it, a growing seam's row repeated, a shrinking seam's rows left out.
            int r = a, shift = 0;
            for (Seam m : seams.list()) {
                if (m.at() >= b) break;
                if (m.at() > r) {
                    part(g, pipeline, texture, x, top, u, v, width, a, r, m.at(), shift, uWidth, textureWidth, textureHeight, color);
                    r = m.at();
                }
                if (m.by() > 0 && m.at() == r) g.blit(pipeline, texture, x, top + r + shift, u, v + r - a, width, m.by(), uWidth, 1,
                        textureWidth, textureHeight, color);
                if (m.by() < 0) r = Math.max(r, Math.min(b, m.at() - m.by()));
                shift += m.by();
            }
            part(g, pipeline, texture, x, top, u, v, width, a, r, b, shift, uWidth, textureWidth, textureHeight, color);
        } finally {
            drawingParts = false;
        }
        return true;
    }

    /** Rows {@code from} to {@code to} of a blit that starts at frame row {@code a}, {@code shift} lower. */
    private static void part(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier texture, int x, int top, float u, float v,
                             int width, int a, int from, int to, int shift, int uWidth, int textureWidth, int textureHeight, int color) {
        if (to <= from) return;
        g.blit(pipeline, texture, x, top + from + shift, u, v + from - a, width, to - from, uWidth, to - from,
                textureWidth, textureHeight, color);
    }

}
