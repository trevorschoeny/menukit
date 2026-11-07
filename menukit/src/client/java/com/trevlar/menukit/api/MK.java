package com.trevlar.menukit.api;

import com.trevlar.menukit.MenuKitClient;
import com.trevlar.menukit.api.panel.Panel;
import com.trevlar.menukit.inject.ContainerScreenLayers;
import com.trevlar.menukit.inject.HudHosts;
import com.trevlar.menukit.inject.LayerPlan;
import com.trevlar.menukit.inject.PanelHost;
import com.trevlar.menukit.inject.ScreenBackground;
import com.trevlar.menukit.api.Declarations;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import org.jetbrains.annotations.ApiStatus;

/**
 * Mod entry point for MenuKit and the process-wide facade for HUD panels,
 * the recipe-book query and backdrops.
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
 *   <li><b>HUD panels:</b> {@link com.trevlar.menukit.api.hud.HudPanel}, and what a mod
 *       draws on the hotbar's slots, {@link com.trevlar.menukit.api.hud.HotbarDecorations}</li>
 *   <li><b>Standalone screens:</b> {@link com.trevlar.menukit.api.panel.MKScreen}, and
 *       the screen it opened over drawn behind it, {@link #drawBackdrop}</li>
 *   <li><b>Layout:</b> {@link com.trevlar.menukit.api.layout.Row},
 *       {@link com.trevlar.menukit.api.layout.Column}</li>
 *   <li><b>Drawing your own item copy</b> (a flying arc, a ring of stacks):
 *       {@link com.trevlar.menukit.api.ItemOutline} for a colored silhouette,
 *       {@link com.trevlar.menukit.api.DrawAlpha} to fade its decorations</li>
 * </ul>
 * Slot groups, slot operations, created slots and custom menus are SlotKit's, a
 * separate library built on MenuKit.
 *
 * <p>Supported static methods on this class: {@link #registerHud},
 * {@link #isRecipeBookOpen}, {@link #setRecipeBookOpen}, {@link #drawBackdrop}. HUD panels register from a client
 * initializer; registration freezes at client start.
 */
public class MK {

    /** MenuKit's own logger, independent of any consuming mod's logger. */
    @ApiStatus.Internal
    public static final Logger LOGGER = LoggerFactory.getLogger("menukit");

    /** Called from {@link MenuKitClient#onInitializeClient()}; only logs. */
    @ApiStatus.Internal
    public static void initClient() {
        LOGGER.info("[MenuKit] Client initialized");
    }

    // HUD panels

    /**
     * Registers a HUD panel into the HUD's host. Called by
     * {@link com.trevlar.menukit.api.hud.HudPanel.Builder#build()}.
     *
     * @param panel   a panel placed {@code screenAnchor(region)} (or {@code pixel}), in each mode scope it shows in
     * @param padding content padding inside the panel's edge
     * @throws IllegalStateException    after MenuKit's declarations froze
     * @throws IllegalArgumentException if the HUD cannot place the panel's position
     */
    public static void registerHud(Panel panel, int padding) {
        registerHud(HudHosts.WINDOW, panel, padding);
    }

    /**
     * Registers a HUD panel in the status column (since 7.3.0). Called by
     * {@link com.trevlar.menukit.api.hud.HudPanel.Builder#build()} for a panel built
     * with {@code statusColumn()}; the panel is positioned {@code region(TOP_CENTER)}
     * against the column's base.
     */
    @ApiStatus.Internal
    public static void registerHudStatus(Panel panel, int padding) {
        registerHud(HudHosts.STATUS, panel, padding);
    }

    private static void registerHud(PanelHost host, Panel panel, int padding) {
        Declarations.requireOpen("MK.registerHud(" + panel.id() + ")");
        for (PanelHost h : HudHosts.all()) {
            for (PanelHost.Entry e : h.entries()) {
                if (e.panel().id().equals(panel.id())) {
                    throw new IllegalStateException("MenuKit: a HUD panel '" + panel.id() + "' is already registered.");
                }
            }
        }
        for (var p : panel.placements().values()) {
            if (p.anchor() != null) {
                throw new IllegalArgumentException("MenuKit: HUD panel '" + panel.id() + "' is placed on "
                        + p.anchor() + "; the HUD has no anchors.");
            }
        }
        // Each mode scope's placement (7.4.0); it shows in the mode it is for.
        host.addPlacements(panel, padding, PanelHost.captureCallerModId(), PanelHost.nextSeq());
        LOGGER.info("[MenuKit] Registered HUD panel '{}'", panel.id());
    }

    /**
     * How far something on the HUD must move up this frame to clear the status column,
     * in GUI pixels: by how much the column's top rises above {@code bottom}, the thing's
     * own bottom edge, provided the column and the span {@code left..right} overlap
     * across. 0 while nothing in the column shows, or while it stays clear. Vanilla's
     * held-item name, action-bar message and chat move up by this
     * ({@code MKHudStatusLiftMixin}, {@code MKChatStatusLiftMixin}), so they shift only
     * when, and only as far as, the column reaches them.
     */
    @ApiStatus.Internal
    public static int statusColumnClearance(int bottom, int left, int right) {
        int top = Integer.MAX_VALUE;
        for (PanelHost.Placed p : HudHosts.STATUS.layout().placed()) {
            if (PanelHost.movedTo(p.entry()) != null) continue; // moved out of the column by the player
            if (p.x() < right && p.x() + p.w() > left) top = Math.min(top, p.y());
        }
        return top == Integer.MAX_VALUE ? 0 : Math.max(0, bottom - top);
    }

    /**
     * Renders the HUD panels. Called at RETURN of the
     * HUD's render by {@code MKGuiMixin}. The HUD host follows the one
     * {@link LayerPlan} with no pointer: HUD panels never take input.
     */
    @ApiStatus.Internal
    public static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        LayerPlan.compose(HudHosts.all(), graphics, -1, -1, screenW, screenH, (host, placed) -> false);
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

    // Backdrops

    /**
     * Draws {@code screen}, which is not the open screen, as it looks open: its background
     * (on a container screen, the container region's panels where its image goes), its
     * widgets, slots and items, and every panel on it, sections and tab rows included. It
     * takes no input and nothing on it is hovered. Call it from the open screen's
     * {@code extractBackground}, usually with the screen it was opened from, then draw
     * over it (a dim, say):
     *
     * <pre>{@code
     * @Override
     * public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
     *     MK.drawBackdrop(parent, graphics, a);
     *     graphics.fill(0, 0, width, height, 0x80000000);
     * }
     * }</pre>
     *
     * A container screen keeps its MenuKit layout (its region, where its slots are) while
     * the screen over it is not a container screen, so it draws as the player left it.
     * Under a container screen it draws without it, as vanilla lays it out. Its Fabric
     * {@code beforeExtract} and {@code afterExtract} listeners run around it, as they do
     * around the open screen.
     *
     * <p>It draws at the size it was last laid out at: a window resized while the screen
     * over it is open leaves it at its old size until it opens again.
     *
     * @param screen      the screen to draw; not the open screen
     * @param graphics    the open screen's graphics
     * @param partialTick the frame's partial tick
     * @since 8.0.0
     */
    public static void drawBackdrop(Screen screen, GuiGraphicsExtractor graphics, float partialTick) {
        // -1, -1: no pointer, so nothing hovers and no tooltip draws.
        ScreenEvents.beforeExtract(screen).invoker().beforeExtract(screen, graphics, -1, -1, partialTick);
        ScreenBackground.outside(() -> ContainerScreenLayers.nested(
                () -> screen.extractRenderStateWithTooltipAndSubtitles(graphics, -1, -1, partialTick)));
        ScreenEvents.afterExtract(screen).invoker().afterExtract(screen, graphics, -1, -1, partialTick);
    }
}
