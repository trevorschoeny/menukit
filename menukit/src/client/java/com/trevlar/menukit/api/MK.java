package com.trevlar.menukit.api;

import com.trevlar.menukit.MenuKitClient;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.api.hud.HudNotification;
import com.trevlar.menukit.inject.LayerPlan;
import com.trevlar.menukit.inject.PanelHost;
import com.trevlar.menukit.api.Declarations;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
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
 *   <li><b>Panels and elements:</b> {@link com.trevlar.menukit.api.panel.Panel},
 *       {@link com.trevlar.menukit.api.element.PanelElement},
 *       {@link com.trevlar.menukit.api.element.Button},
 *       {@link com.trevlar.menukit.api.element.TextLabel}</li>
 *   <li><b>Panels on vanilla container screens:</b>
 *       {@link com.trevlar.menukit.api.panel.ScreenPanelAdapter},
 *       {@link com.trevlar.menukit.api.panel.AnchorPanelAdapter}</li>
 *   <li><b>HUD panels:</b> {@link com.trevlar.menukit.api.hud.HudPanel}, and the cycle
 *       strip beside the hotbar, {@link com.trevlar.menukit.api.hud.HudCycleStrip}</li>
 *   <li><b>Standalone screens:</b> {@link com.trevlar.menukit.api.panel.MKScreen}</li>
 *   <li><b>Layout:</b> {@link com.trevlar.menukit.api.layout.Row},
 *       {@link com.trevlar.menukit.api.layout.Column}</li>
 * </ul>
 * Slot groups, slot operations, created slots and custom menus are SlotKit's, a
 * separate library built on MenuKit.
 *
 * <p>Supported static methods on this class: {@link #registerHud},
 * {@link #registerNotification}, {@link #notify}, {@link #isRecipeBookOpen},
 * {@link #setRecipeBookOpen}. HUD panels and notifications register from a client
 * initializer; registration freezes at client start.
 */
public class MK {

    /** MenuKit's own logger, independent of any consuming mod's logger. */
    @ApiStatus.Internal
    public static final Logger LOGGER = LoggerFactory.getLogger("menukit");

    // HUD panels share one host, which places each panel on its InsideRegion spot
    // of the game window with the HUD's insets, or in an OutsideRegion around the
    // vanilla hotbar (PanelHost.hudFrame). Registered at init, frozen at client
    // start, rendered in sorted (priority, modId, sequence) order.
    private static final PanelHost HUD = PanelHost.hud(() -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return null;
        return PanelHost.hudFrame(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
                drawnOffhandSide(mc));
    });

    /**
     * The side vanilla draws the offhand slot on beside the hotbar this frame, or
     * {@code null} when it draws none. Mirrors {@code Hud.extractItemHotbar} (26.x):
     * the item hotbar is drawn for the camera's player outside spectator mode (a
     * spectator gets the spectator bar instead), and the offhand slot only while
     * that player's offhand holds something, on the side opposite the main arm
     * (left for a right-handed player, right for a left-handed one). The geometry
     * this feeds lives in {@code RegionMath.resolveHud}; this only reads the game.
     */
    private static @Nullable HumanoidArm drawnOffhandSide(Minecraft mc) {
        if (mc.gameMode == null || mc.gameMode.getPlayerMode() == GameType.SPECTATOR) return null;
        if (!(mc.getCameraEntity() instanceof Player player)) return null;
        if (player.getOffhandItem().isEmpty()) return null;
        return player.getMainArm().getOpposite();
    }

    // Notification definitions, registered at mod init
    private static final Map<String, HudNotification> notificationDefs = new LinkedHashMap<>();

    // Active notifications. Written by notify() from any thread (a server-side feature in singleplayer
    // notifies from the server thread) and read by the HUD on the render thread:
    // synchronized, and render works on a snapshot.
    private static final Map<String, ActiveNotification> activeNotifications =
            java.util.Collections.synchronizedMap(new LinkedHashMap<>());

    /** Snapshot of what a caller passed to {@link #notify}, plus trigger time. */
    record ActiveNotification(long triggerTimeMs,
                              @Nullable String textData,
                              @Nullable ItemStack itemData) {}

    /** Called from {@link MenuKitClient#onInitializeClient()}; only logs. */
    @ApiStatus.Internal
    public static void initClient() {
        LOGGER.info("[MenuKit] Client initialized");
    }

    // HUD panels and notifications

    /**
     * Registers a HUD panel into the HUD's host. Called by
     * {@link com.trevlar.menukit.api.hud.HudPanel.Builder#build()}.
     *
     * @param panel   a panel positioned {@code screenAnchor(region)} (or {@code pixel})
     * @param padding content padding inside the panel's edge
     * @throws IllegalStateException    after MenuKit's declarations froze
     * @throws IllegalArgumentException if the HUD cannot place the panel's position
     */
    public static void registerHud(Panel panel, int padding) {
        Declarations.requireOpen("MK.registerHud(" + panel.id() + ")");
        for (PanelHost.Entry e : HUD.entries()) {
            if (e.panel().id().equals(panel.id())) {
                throw new IllegalStateException("MenuKit: a HUD panel '" + panel.id() + "' is already registered.");
            }
        }
        HUD.add(panel, panel.getPosition(), padding, PanelHost.captureCallerModId(), PanelHost.nextSeq());
        LOGGER.info("[MenuKit] Registered HUD panel '{}'", panel.id());
    }

    /**
     * Registers a notification definition. Called by
     * {@link HudNotification.Builder#build()}.
     *
     * @throws IllegalStateException after MenuKit's declarations froze, or for a
     *         second notification with the same key
     */
    public static void registerNotification(HudNotification def) {
        Declarations.requireOpen("MK.registerNotification(" + def.id() + ")");
        if (notificationDefs.putIfAbsent(def.id(), def) != null) {
            throw new IllegalStateException("MenuKit: a notification '" + def.id() + "' is already registered.");
        }
        LOGGER.info("[MenuKit] Registered notification '{}'", def.id());
    }

    /**
     * Renders the HUD panels, then the active notifications. Called at RETURN of the
     * HUD's render by {@code MKGuiMixin}. The HUD host follows the one
     * {@link LayerPlan} with no pointer: HUD panels never take input.
     */
    @ApiStatus.Internal
    public static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        LayerPlan.compose(List.of(HUD), graphics, -1, -1, screenW, screenH, (host, placed) -> false);

        renderActiveNotifications(graphics, deltaTracker, screenW, screenH);
    }

    /**
     * Triggers a notification by key. It slides in, stays for its configured
     * duration, then fades out.
     *
     * @param key  notification key (must match a
     *             {@link HudNotification#builder(String)} registration)
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

    // Draws the active notifications and removes expired ones. Each one's
    // HudNotification.render animates itself from the time since it was triggered.
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
            HudNotification def = notificationDefs.get(key);
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

    // Recipe book
    //
    // Mods use these to make room for a panel while the recipe book is open. The
    // implementation is in MenuKitClient, which is internal (it is the Fabric
    // client entry point), so the public methods live here and only delegate.

    /**
     * Whether the recipe book is visible on the active screen. Safe to call at any
     * time; false when there is no screen or it has no recipe book.
     */
    public static boolean isRecipeBookOpen() {
        return MenuKitClient.isRecipeBookOpen();
    }

    /**
     * Sets the recipe book's visibility on the active screen. Does nothing when
     * the screen has no recipe book or it is already in the requested state.
     *
     * @param open {@code true} to show the recipe book, {@code false} to hide it
     */
    public static void setRecipeBookOpen(boolean open) {
        MenuKitClient.setRecipeBookOpen(open);
    }
}
