package com.trevlar.menukit.inject;

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
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code ScreenFrames}' machinery (Fluorite): each container screen's seams, its size before
 * vanilla centers it, its slots from each seam down, the recipe book button's place in the
 * stretched frame, and the screen's image drawn in parts around the seams. The public face,
 * and what a stretched screen does, is {@code api.panel.ScreenFrames}. Render thread only.
 */
@ApiStatus.Internal
public final class ScreenSeams {

    private ScreenSeams() {}

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

    /** {@code ScreenFrames.stretch}: {@code screen} gains {@code by} rows at row {@code seam}; {@code false} when it is already stretched. */
    public static boolean declare(Class<? extends AbstractContainerScreen<?>> screen, int seam, int by) {
        return SEAMS.putIfAbsent(screen, new Seam(seam, by)) == null;
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
        // A short screen's grid takes its slots after the seams moved them, before the region lays out.
        ShortFrames.init(PHASE);
    }

    /**
     * Every slot of the menu moved by the seams above it, each moved once, however late it
     * joined the menu. Before the region moves any (this phase, and this frame hook, come
     * first), so a slot is seen where vanilla put it. A slot above every seam is noted too:
     * once the region has moved it down past a seam, it is not vanilla's below it.
     */
    private static void placeSlots(AbstractContainerScreen<?> screen, Seams seams) {
        for (Slot slot : screen.getMenu().slots) {
            if (MOVED.containsKey(slot)) continue;
            int by = seams.shift(slot.y);
            if (by != 0) ((SlotAccessor) slot).mk$setY(slot.y + by);
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
