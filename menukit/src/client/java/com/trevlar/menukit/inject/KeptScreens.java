package com.trevlar.menukit.inject;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * A container screen taken down for a screen that isn't one keeps its MenuKit layout
 * (8.0.0): its region, its hosts, where its slots are. The screen over it can then draw
 * it as its backdrop ({@code MK.drawBackdrop}) as it looked while it was open.
 *
 * <p>Its taking down waits until a container screen opens, or no screen: that is when its
 * slots and layout are wanted elsewhere, and it runs then, before that screen initializes,
 * as it would have when the screen was first taken down. When the kept screen itself comes
 * back, nothing is taken down: it initializes again as after a resize, its scroll and open
 * sections as they were.
 *
 * <p>Every MenuKit hook that takes a container screen's layout down registers here
 * ({@link #onTakenDown}) instead of on Fabric's remove event. Hooks about the change itself
 * (the cursor, a frame's move to the next screen) stay on the remove event.
 *
 * <p>Render thread only.
 */
@ApiStatus.Internal
public final class KeptScreens {

    private KeptScreens() {}

    /**
     * Leaving the world keeps nothing: a disconnect opens a title or disconnected screen,
     * which isn't a container screen, so without this the last world's screen, its menu
     * and its layout would stay kept until the next container screen.
     */
    public static void init() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> takeDownKept());
    }

    /** The screen {@code Gui.setScreen} is opening, while it runs; {@code null} outside it (and for no screen). */
    private static @Nullable Screen incoming;
    /** Whether a {@code Gui.setScreen} is under way. */
    private static boolean switching;
    /** Each kept screen's waiting take-downs, in the order they were registered. */
    private static final Map<Screen, List<Runnable>> KEPT = new IdentityHashMap<>();

    /**
     * At {@code Gui.setScreen} HEAD, before the open screen is taken down. A container screen
     * or no screen: what was kept is taken down now, except the screen coming back, which
     * picks up where it was.
     */
    public static void opening(@Nullable Screen next) {
        incoming = next;
        switching = true;
        if (next != null && !(next instanceof AbstractContainerScreen<?>)) return;
        KEPT.remove(next);
        takeDownKept();
    }

    /** At {@code Gui.setScreen} RETURN. */
    public static void opened() {
        incoming = null;
        switching = false;
    }

    /**
     * Runs {@code takeDown} when {@code screen} is taken down: at once, or, when it is a
     * container screen taken down for a screen that isn't one, once it stops being kept.
     */
    public static void onTakenDown(Screen screen, Runnable takeDown) {
        ScreenEvents.remove(screen).register(s -> {
            if (keeps(s)) KEPT.computeIfAbsent(s, k -> new ArrayList<>()).add(takeDown);
            else takeDown.run();
        });
    }

    /** Whether {@code screen} is kept now: taken down, its layout still up. */
    public static boolean kept(Screen screen) {
        return KEPT.containsKey(screen);
    }

    // A removal outside setScreen (the game closing) keeps nothing.
    private static boolean keeps(Screen removed) {
        return switching && removed instanceof AbstractContainerScreen<?>
                && incoming != null && !(incoming instanceof AbstractContainerScreen<?>);
    }

    private static void takeDownKept() {
        // Copied first: a take-down may open or close a screen of its own.
        List<List<Runnable>> waiting = new ArrayList<>(KEPT.values());
        KEPT.clear();
        for (List<Runnable> list : waiting) for (Runnable r : list) r.run();
    }
}
