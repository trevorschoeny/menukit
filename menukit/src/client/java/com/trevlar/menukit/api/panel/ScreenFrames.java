package com.trevlar.menukit.api.panel;

import com.trevlar.menukit.api.Declarations;
import com.trevlar.menukit.inject.ContainerRegions;
import com.trevlar.menukit.mixin.AbstractContainerScreenAccessor;
import com.trevlar.menukit.mixin.SlotAccessor;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A vanilla container screen made taller or shorter at a seam (Fluorite): its frame gains
 * (or loses) {@code by} rows at row {@code seam} of its own layout, so screens of nearly one
 * size can be made exactly one, and a tab row standing on their frames doesn't move as one
 * opens in place of another.
 *
 * <pre>{@code
 * // The inventory is 166 tall to a shulker box's 167: one more blank row above the inventory.
 * ScreenFrames.stretch(InventoryScreen.class, 81, 1);
 * }</pre>
 *
 * <ul>
 *   <li><b>The screen's size</b> is vanilla's plus {@code by}, centered by vanilla as any
 *       screen of that size is, so it sits exactly where a vanilla screen of that size does:
 *       the stretched inventory and a shulker box share one frame on every window.</li>
 *   <li><b>Above the seam</b> everything keeps its place in the frame: slots, labels, the
 *       player model, the recipe book button. <b>From the seam down</b> everything is
 *       {@code by} lower in the frame: slots (another mod's added to the menu included) and
 *       the "Inventory" label.</li>
 *   <li><b>The screen's own image</b> draws in two parts with the seam row repeated
 *       {@code by} times between them, or with {@code -by} rows from the seam left out.
 *       OptiGUI's swap and a resource pack's texture apply to each part as to the whole.</li>
 *   <li>Exactly the class given; a subclass keeps its own size. Not while the
 *       {@link ContainerRegion container region} takes the screen's image.</li>
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

    // The screens stretched so far (a screen grows once, a resize initializes it again), and how far each slot was moved.
    private static final Set<Screen> GROWN = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<Slot, Integer> MOVED = new WeakHashMap<>();

    private static @Nullable Seam of(Screen screen) {
        return SEAMS.isEmpty() ? null : SEAMS.get(screen.getClass());
    }

    /** Hooks every stretched screen's size before vanilla centers it, and its slots each frame. Called once from client init. */
    @ApiStatus.Internal
    public static void init() {
        ScreenEvents.BEFORE_INIT.register((client, screen, w, h) -> {
            Seam seam = of(screen);
            if (seam == null || !GROWN.add(screen)) return;
            AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
            acc.mk$setImageHeight(acc.mk$getImageHeight() + seam.by());
            if (acc.mk$getInventoryLabelY() >= seam.at()) acc.mk$setInventoryLabelY(acc.mk$getInventoryLabelY() + seam.by());
        });
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            Seam seam = of(screen);
            if (seam == null) return;
            AbstractContainerScreen<?> acs = (AbstractContainerScreen<?>) screen;
            ScreenEvents.beforeExtract(screen).register((s, g, mx, my, delta) -> placeSlots(acs, seam));
            // The menu can outlive the screen (the player's inventory menu does): its slots go back.
            ScreenEvents.remove(screen).register(s -> unplaceSlots());
            placeSlots(acs, seam);
        });
    }

    /**
     * Every slot of the menu from the seam down {@code by} lower, each moved once, however
     * late it joined the menu; none while the region has the screen.
     */
    private static void placeSlots(AbstractContainerScreen<?> screen, Seam seam) {
        if (ContainerRegions.replacesImage(screen)) {
            unplaceSlots();
            return;
        }
        for (Slot slot : screen.getMenu().slots) {
            if (MOVED.containsKey(slot) || slot.y < seam.at()) continue;
            ((SlotAccessor) slot).mk$setY(slot.y + seam.by());
            MOVED.put(slot, seam.by());
        }
    }

    private static void unplaceSlots() {
        for (Map.Entry<Slot, Integer> e : MOVED.entrySet()) ((SlotAccessor) e.getKey()).mk$setY(e.getKey().y - e.getValue());
        MOVED.clear();
    }

    /**
     * {@code y}, a place vanilla worked out on the screen as if it were its own size (the
     * recipe book button's, from the window's middle), moved to the same place in the
     * stretched frame.
     */
    @ApiStatus.Internal
    public static int place(Screen screen, int y) {
        Seam seam = of(screen);
        if (seam == null || !(screen instanceof AbstractContainerScreen<?>)) return y;
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        int vanillaTop = (screen.height - acc.mk$getImageHeight() + seam.by()) / 2;
        int row = y - vanillaTop;
        // ponytail: assumes the place was worked out from the window (vanilla's recipe screens do); one from topPos would move twice
        return acc.mk$getTopPos() + row + (row >= seam.at() ? seam.by() : 0);
    }

    /** Set while the image's parts are being drawn, so they draw as given. */
    private static boolean drawingParts;

    /**
     * At the head of a texture blit that is {@code screen}'s own image: draws it in its parts
     * around the seam and says so, or {@code false} to let it draw as given.
     */
    @ApiStatus.Internal
    public static boolean drawParts(Screen screen, GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier texture,
                                    int x, int y, float u, float v, int width, int height, int uWidth, int vHeight,
                                    int textureWidth, int textureHeight, int color) {
        Seam seam = of(screen);
        if (seam == null || drawingParts || ContainerRegions.replacesImage(screen)) return false;
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        int top = acc.mk$getTopPos(), vanillaHeight = acc.mk$getImageHeight() - seam.by();
        // Rows of the frame as vanilla laid it out, this blit's from a to b; nothing past vanilla's own height.
        int a = y - top, b = Math.min(a + height, vanillaHeight), at = seam.at(), by = seam.by();
        if (vHeight != height) {
            // ponytail: a scaled blit moves whole, on whichever side of the seam it starts
            if (a < at) return false;
            g.blit(pipeline, texture, x, y + by, u, v, width, height, uWidth, vHeight, textureWidth, textureHeight, color);
            return true;
        }
        if (b <= a) return true;
        drawingParts = true;
        try {
            if (a < at) g.blit(pipeline, texture, x, y, u, v, width, Math.min(b, at) - a, uWidth, Math.min(b, at) - a,
                    textureWidth, textureHeight, color);
            if (by > 0 && a <= at && at < b)
                g.blit(pipeline, texture, x, top + at, u, v + at - a, width, by, uWidth, 1, textureWidth, textureHeight, color);
            int from = Math.max(a, at + Math.max(0, -by));
            if (from < b) g.blit(pipeline, texture, x, top + from + by, u, v + from - a, width, b - from, uWidth, b - from,
                    textureWidth, textureHeight, color);
        } finally {
            drawingParts = false;
        }
        return true;
    }

}
