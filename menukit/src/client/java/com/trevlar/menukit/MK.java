package com.trevlar.menukit;

import com.trevlar.menukit.core.Panel;
import com.trevlar.menukit.hud.MKHudNotification;
import com.trevlar.menukit.inject.LayerPlan;
import com.trevlar.menukit.inject.PanelHost;
import com.trevlar.menukit.window.Declarations;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.ApiStatus;

/**
 * Mod entry point for MenuKit and the process-wide facade for HUD panels,
 * notifications, and the recipe-book query.
 *
 * <p>The consumer surface by task:
 * <ul>
 *   <li><b>Panels and elements:</b> {@link com.trevlar.menukit.core.Panel},
 *       {@link com.trevlar.menukit.core.PanelElement},
 *       {@link com.trevlar.menukit.core.Button},
 *       {@link com.trevlar.menukit.core.TextLabel}</li>
 *   <li><b>Panels on vanilla container screens:</b>
 *       {@link com.trevlar.menukit.inject.ScreenPanelAdapter},
 *       {@link com.trevlar.menukit.inject.SlotGroupPanelAdapter}</li>
 *   <li><b>HUD panels:</b> {@link com.trevlar.menukit.hud.MKHudPanel}</li>
 *   <li><b>Standalone screens:</b> {@link com.trevlar.menukit.screen.MKScreen}</li>
 *   <li><b>Layout:</b> {@link com.trevlar.menukit.core.layout.Row},
 *       {@link com.trevlar.menukit.core.layout.Column}</li>
 * </ul>
 * Created slots, custom menus, and per-slot state are in MenuKit: Containers
 * ({@code MKCContainerPanel}, {@code MKCMenu}, {@code MKSlotState}).
 *
 * <p>Supported static methods on this class: {@link #registerHud},
 * {@link #registerNotification}, {@link #notify}, {@link #isRecipeBookOpen},
 * {@link #setRecipeBookOpen}. HUD panels and notifications register from a client
 * initializer; registration freezes at client start.
 */
public class MK {

    /** MenuKit's own logger — independent of any consuming mod's logger. */
    public static final Logger LOGGER = LoggerFactory.getLogger("menukit");

    // ── HUD panels: one host (§0065), placing each panel on its InsideRegion spot
    //    of the game window with the HUD's insets. Registered at init, frozen at
    //    client start, rendered in sorted (priority, modId, sequence) order.
    private static final PanelHost HUD = PanelHost.hud(() -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return null;
        return new PanelHost.Frame(mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight(), null);
    });

    // ── Notification definitions (registered at mod init) ─────────────────
    private static final Map<String, MKHudNotification> notificationDefs = new LinkedHashMap<>();

    // ── Active notifications (runtime animation state) ────────────────────
    // Written by notify() from any thread (a server-side feature in singleplayer
    // notifies from the server thread) and read by the HUD on the render thread:
    // synchronized, and render works on a snapshot.
    private static final Map<String, ActiveNotification> activeNotifications =
            java.util.Collections.synchronizedMap(new LinkedHashMap<>());

    /** Snapshot of what a caller passed to {@link #notify}, plus trigger time. */
    record ActiveNotification(long triggerTimeMs,
                              @Nullable String textData,
                              @Nullable ItemStack itemData) {}

    // ══════════════════════════════════════════════════════════════════════
    // Mod lifecycle
    // ══════════════════════════════════════════════════════════════════════


    /** Client-side initialization for the MenuKit artifact. Invoked from
     *  {@link MKClient#onInitializeClient()}; logs only. */
    public static void initClient() {
        LOGGER.info("[MenuKit] Client initialized");
    }

    // ══════════════════════════════════════════════════════════════════════
    // HUD — registration, rendering, notifications
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Registers a HUD panel into the HUD's host. Called by
     * {@link com.trevlar.menukit.hud.MKHudPanel.Builder#build()}.
     *
     * @param panel   a panel positioned {@code screenAnchor(region)} (or {@code pixel})
     * @param padding content padding inside the panel's edge
     * @throws IllegalStateException    after MenuKit's declarations froze
     * @throws IllegalArgumentException if the HUD cannot place the panel's position
     */
    public static void registerHud(Panel panel, int padding) {
        Declarations.requireOpen("MK.registerHud(" + panel.getId() + ")");
        for (PanelHost.Entry e : HUD.entries()) {
            if (e.panel().getId().equals(panel.getId())) {
                throw new IllegalStateException("MenuKit: a HUD panel '" + panel.getId() + "' is already registered.");
            }
        }
        HUD.add(panel, panel.getPosition(), padding, PanelHost.captureCallerModId(), PanelHost.nextSeq());
        LOGGER.info("[MenuKit] Registered HUD panel '{}'", panel.getId());
    }

    /**
     * Registers a notification definition. Called by
     * {@link MKHudNotification.Builder#build()}.
     *
     * @throws IllegalStateException after MenuKit's declarations froze, or for a
     *         second notification with the same key
     */
    public static void registerNotification(MKHudNotification def) {
        Declarations.requireOpen("MK.registerNotification(" + def.getKey() + ")");
        if (notificationDefs.putIfAbsent(def.getKey(), def) != null) {
            throw new IllegalStateException("MenuKit: a notification '" + def.getKey() + "' is already registered.");
        }
        LOGGER.info("[MenuKit] Registered notification '{}'", def.getKey());
    }

    /**
     * Renders the HUD panels, then the active notifications. Called at RETURN of the
     * HUD's render by {@code MKGuiMixin}. The HUD host follows the one
     * {@link LayerPlan} with no pointer: HUD panels never take input.
     */
    public static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        LayerPlan.compose(List.of(HUD), graphics, -1, -1, screenW, screenH, (host, placed) -> false);

        renderActiveNotifications(graphics, deltaTracker, screenW, screenH);
    }

    /**
     * Triggers a notification by key. The notification slides in, displays
     * for its configured duration, then fades out.
     *
     * @param key  notification key (must match a
     *             {@link MKHudNotification#builder(String)} registration)
     * @param text text to display, or null for icon-only
     */
    public static void notify(String key, String text) {
        notify(key, text, (ItemStack) null);
    }

    /** Triggers a notification with text and an item icon. */
    public static void notify(String key, String text, @Nullable ItemStack item) {
        if (!notificationDefs.containsKey(key)) {
            LOGGER.warn("[MenuKit] Unknown notification key '{}'", key);
            return;
        }
        activeNotifications.put(key, new ActiveNotification(
                System.currentTimeMillis(), text, item));
    }

    /** Triggers a notification with text and an item type. */
    public static void notify(String key, String text, net.minecraft.world.item.Item item) {
        notify(key, text, new ItemStack(item));
    }

    /**
     * Renders all active notifications, removing expired ones.
     * Iterates once; each notification's own {@link MKHudNotification#render}
     * handles its slide-in / display / fade-out animation based on elapsed
     * time since trigger.
     */
    private static void renderActiveNotifications(GuiGraphicsExtractor graphics,
                                                   DeltaTracker deltaTracker,
                                                   int screenW, int screenH) {
        if (activeNotifications.isEmpty()) return;

        var expired = new ArrayList<String>();
        long now = System.currentTimeMillis();
        List<Map.Entry<String, ActiveNotification>> snapshot;
        synchronized (activeNotifications) {
            snapshot = new ArrayList<>(activeNotifications.entrySet());
        }

        for (var entry : snapshot) {
            String key = entry.getKey();
            ActiveNotification active = entry.getValue();
            MKHudNotification def = notificationDefs.get(key);
            if (def == null) { expired.add(key); continue; }

            long elapsed = now - active.triggerTimeMs();
            if (elapsed > def.getDurationMs()) {
                expired.add(key);
                continue;
            }

            def.render(graphics, deltaTracker, screenW, screenH,
                    elapsed, active.textData(), active.itemData());
        }

        // Remove only the entry that expired: a notify() since the snapshot replaced it.
        synchronized (activeNotifications) {
            for (var entry : snapshot) {
                if (expired.contains(entry.getKey())) activeNotifications.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // Recipe book — public face
    // ══════════════════════════════════════════════════════════════════════
    //
    // The recipe-book overlay query/toggle is a client-side utility consumers
    // genuinely reach for (e.g. "make room for my panel when the recipe book
    // is open"). The implementation lives on {@link MKClient} (which stays
    // {@code @ApiStatus.Internal} — it's the Fabric client entry point, not a
    // consumer surface), so the PUBLIC face belongs here on {@code MK}
    // alongside registerHud/notify. These two methods are pure delegators —
    // no behavior of their own — so the impl stays single-sourced in MKClient.

    /**
     * Returns whether the recipe book is currently visible on the active
     * screen. Safe to call at any time; returns {@code false} when there is
     * no active screen or the active screen has no recipe book.
     *
     * <p>Delegates to the client-side implementation; this is the public,
     * consumer-facing entry point.
     */
    public static boolean isRecipeBookOpen() {
        return MKClient.isRecipeBookOpen();
    }

    /**
     * Sets the recipe book's visibility on the active screen. No-op when the
     * active screen has no recipe book or the requested state already matches.
     *
     * <p>Delegates to the client-side implementation; this is the public,
     * consumer-facing entry point.
     *
     * @param open {@code true} to show the recipe book, {@code false} to hide it
     */
    public static void setRecipeBookOpen(boolean open) {
        MKClient.setRecipeBookOpen(open);
    }
}
