package com.trevlar.menukit;

import com.trevlar.menukit.api.MK;
import com.trevlar.menukit.api.panel.Focus;
import com.trevlar.menukit.core.ControlLook;
import com.trevlar.menukit.core.OptiGuiRules;
import com.trevlar.menukit.core.PackLook;
import com.trevlar.menukit.core.PackText;
import com.trevlar.menukit.inject.ContainerRegions;
import com.trevlar.menukit.inject.MenuChrome;
import com.trevlar.menukit.inject.ScreenPanelRegistry;
import com.trevlar.menukit.input.CursorContinuity;
import com.trevlar.menukit.mixin.AbstractContainerScreenAccessor;
import com.trevlar.menukit.mixin.MKRecipeBookAccessor;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import org.jetbrains.annotations.ApiStatus;

/**
 * Client-side entry point for MenuKit.
 *
 * <p>Also holds the recipe-book utilities behind {@code MK.isRecipeBookOpen} and
 * {@code MK.setRecipeBookOpen}. Item Tips (durability and food lines on every
 * tooltip) moved from MenuKit to Inventory Plus in 6.0.0: it is a feature, and
 * MenuKit leaves features to the mods that use it.
 */
@ApiStatus.Internal
public class MenuKitClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Declarations freeze once every client entrypoint has run, in a phase
        // after the default one, so anything a library wires at client start declares
        // before the freeze, whichever mod's entrypoint ran first.
        Identifier freeze = Identifier.fromNamespaceAndPath("menukit", "freeze");
        var started = net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED;
        started.addPhaseOrdering(net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE, freeze);
        started.register(freeze, client -> com.trevlar.menukit.api.Declarations.freeze("client started"));

        MK.initClient();

        // Vanilla menu-chrome providers, registered here so region-aware
        // ScreenPanelAdapters built later resolve origins around the chrome.
        registerVanillaMenuChrome();

        // One ScreenEvents.AFTER_INIT listener for every panel adapter, so
        // consumers don't each register their own.
        ScreenPanelRegistry.init();
        // The container region's per-screen hooks (7.4.0): its frame, and the wheel, last.
        ContainerRegions.init();

        // MenuKit's looks follow resource packs: what each reads from the packs (the panels'
        // container art, the controls' sprites, the text shader, OptiGUI's rules) is cached,
        // and read again from the new packs after every resource reload.
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
                Identifier.fromNamespaceAndPath("menukit", "pack_look"), (ResourceManagerReloadListener) rm -> {
                    PackLook.onReload();
                    PackText.onReload();
                    ControlLook.onReload();
                    OptiGuiRules.onReload();
                });

        // A focused MenuKit widget (such as TextField or Keybindery's SearchBox)
        // loses focus when the player clicks outside it. Vanilla only moves focus
        // when something claims the click, and panels eat clicks, so this has to be
        // done by hand. See Focus.
        Focus.init();

        // Keeps the cursor where it was across screen changes, undoing vanilla's
        // re-centring. Applies to every screen with no opt-in. See CursorContinuity.
        CursorContinuity.registerRestoreHook();

        // Cursor lock under a modal. Window has a global allowCursorChanges flag;
        // with it false, every selectCursor call returns CursorType.DEFAULT
        // (selectCursor returns early in vanilla). One flag replaces per-widget
        // hover suppression.
        //
        // Synced every tick (20 Hz is fine for a cursor shape, and avoids
        // tracking visibility changes): while an injected modal gates input the
        // cursor stays DEFAULT whatever hover code asks for, and returns to
        // vanilla behaviour when it closes.
        //
        // Only modals lock the cursor. Non-modal opaque panels don't: the cursor
        // shape is window state, not tied to the pointer's position, and switching
        // it per panel would flicker on edge crossings.
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getWindow() != null) {
                client.getWindow().setAllowCursorChanges(
                        !com.trevlar.menukit.inject.ScreenPanelRegistry.modalGatesInput());
            }
        });
    }

    // Vanilla menu chrome
    //
    // Only screens something actually uses so far. Other recipe-book screens
    // (FurnaceScreen, SmokerScreen, BlastFurnaceScreen) can be added when a mod
    // needs them.

    // Recipe-book chrome for InventoryScreen and CraftingScreen, from vanilla's
    // RecipeBookComponent.updateTabs: with the book visible on a wide enough screen,
    // the book body is 147 px left of the frame and the filter-tab column adds
    // about 31 px more.
    //
    // Assumes xOffset = 86 while visible, the value of vanilla's
    // AbstractRecipeBookScreen.getXOffset(boolean narrow) for both the player
    // inventory and the crafting table. A subclass that overrides getXOffset needs
    // a provider that computes the offset itself.
    private static MenuChrome.ChromeExtents recipeBookChromeFor(
            AbstractContainerScreenAccessor screenAcc, int screenWidth) {
        int currentLeftPos = screenAcc.mk$getLeftPos();
        int tabLeft = (screenWidth - 147) / 2 - 116;
        int chromeLeft = currentLeftPos - tabLeft;
        if (chromeLeft <= 0) return MenuChrome.ChromeExtents.NONE;
        return new MenuChrome.ChromeExtents(0, chromeLeft, 0, 0);
    }

    private static void registerVanillaMenuChrome() {
        // CreativeModeInventoryScreen: tab rows above and below the declared
        // 195x136 frame. TAB_HEIGHT=32 includes 3-4 px of transparent sprite
        // padding around each tab, so anchoring to 32 leaves panels floating too
        // far from the visible edge. These values come from vanilla's own hit test
        // in checkTabHovering (21x27 at sprite offset +3, +3):
        //   top tab: topPos - 25 to topPos + 2, so 25 px above the frame
        //   bottom tab: topPos + iH - 1 to topPos + iH + 26, so 26 px below
        // The 25/26 difference is vanilla's bottom tabs sitting 1 px lower.
        // Panels land STACK_GAP=2 past the visible edge.
        MenuChrome.register(CreativeModeInventoryScreen.class,
                screen -> new MenuChrome.ChromeExtents(25, 0, 0, 26));

        // InventoryScreen: the open recipe book extends left of the frame by the
        // book body (147 px) plus the filter-tab column (about 31 px, with
        // 35-wide tab buttons). recipeBookChromeFor is shared with CraftingScreen,
        // since both have xOffset=86.
        //
        // Recomputed every frame because screen.width changes on resize. No
        // chrome in "widthTooNarrow" mode (screen width < 379): vanilla draws the
        // book over the inventory instead of shifting the frame, so there is no
        // space left of the book to anchor LEFT_ALIGN regions to.
        MenuChrome.register(InventoryScreen.class,
                screen -> recipeBookChromeIfOpen((AbstractRecipeBookScreen<?>) screen));

        // CraftingScreen: the same recipe-book treatment as the player inventory.
        // If its xOffset ever differs from 86, panels will visibly misalign and
        // the formula needs to read getXOffset per screen. The furnace screens
        // could reuse this formula the same way.
        MenuChrome.register(CraftingScreen.class,
                screen -> recipeBookChromeIfOpen((AbstractRecipeBookScreen<?>) screen));
    }

    // Chrome for a recipe-book screen when the book is visible and the screen is
    // wide enough for the book to shift the frame rather than cover it; else NONE.
    private static MenuChrome.ChromeExtents recipeBookChromeIfOpen(
            AbstractRecipeBookScreen<?> screen) {
        var rbAccessor = (MKRecipeBookAccessor) screen;
        if (!rbAccessor.mk$getRecipeBookComponent().isVisible()) {
            return MenuChrome.ChromeExtents.NONE;
        }
        if (screen.width < 379) {
            return MenuChrome.ChromeExtents.NONE;
        }
        var bndsAccessor = (AbstractContainerScreenAccessor) screen;
        return recipeBookChromeFor(bndsAccessor, screen.width);
    }

    // Recipe book
    //
    // The recipe book is a client-side overlay drawn by AbstractRecipeBookScreen
    // (InventoryScreen, CraftingScreen and others). These let mods check or toggle
    // it without importing vanilla screen classes or writing an accessor mixin.

    /**
     * Whether the recipe book is visible on the active screen. Safe to call at any
     * time; false when there is no screen or it does not extend
     * {@link AbstractRecipeBookScreen}.
     */
    public static boolean isRecipeBookOpen() {
        var mc = Minecraft.getInstance();

        if (!(mc.gui.screen() instanceof AbstractRecipeBookScreen<?>)) {
            return false;
        }

        var accessor = (MKRecipeBookAccessor) mc.gui.screen();
        return accessor.mk$getRecipeBookComponent().isVisible();
    }

    /**
     * Sets the recipe book's visibility on the active screen.
     *
     * <p>Does nothing if the active screen has no recipe book or it is already in
     * the requested state.
     *
     * @param open {@code true} to show the recipe book, {@code false} to hide it
     */
    public static void setRecipeBookOpen(boolean open) {
        var mc = Minecraft.getInstance();

        if (!(mc.gui.screen() instanceof AbstractRecipeBookScreen<?>)) {
            return;
        }

        var accessor = (MKRecipeBookAccessor) mc.gui.screen();
        var recipeBook = accessor.mk$getRecipeBookComponent();

        // toggleVisibility() flips, so only call it when the state differs.
        if (recipeBook.isVisible() != open) {
            recipeBook.toggleVisibility();
        }
    }
}
