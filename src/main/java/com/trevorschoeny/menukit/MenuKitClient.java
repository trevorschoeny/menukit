package com.trevorschoeny.menukit;

import com.trevorschoeny.menukit.mixin.MKRecipeBookAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;

/**
 * Client-side entry point for MenuKit.
 * Registers the MKScreen factory for standalone panel screens.
 *
 * <p>Also provides static utility methods for interacting with
 * client-only UI components (like the recipe book) so that
 * consumers don't need to cast to vanilla screen internals.
 */
public class MenuKitClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MenuKit.initClient();
    }

    // ── Recipe Book Utilities ────────────────────────────────────────────────
    //
    // The recipe book is a client-side overlay rendered by AbstractRecipeBookScreen
    // (InventoryScreen, CraftingScreen, etc.). These helpers let consumers check
    // or toggle its visibility without importing vanilla screen classes or
    // writing their own accessor mixin.

    /**
     * Returns whether the recipe book is currently visible on the active screen.
     *
     * <p>Safe to call at any time. Returns {@code false} if:
     * <ul>
     *   <li>There is no active screen</li>
     *   <li>The active screen does not extend {@link AbstractRecipeBookScreen}</li>
     * </ul>
     *
     * @return {@code true} if the recipe book overlay is open, {@code false} otherwise
     */
    public static boolean isRecipeBookOpen() {
        var mc = Minecraft.getInstance();

        // Only AbstractRecipeBookScreen subclasses have a recipe book component.
        // Anything else (ChestScreen, HopperScreen, etc.) simply returns false.
        if (!(mc.screen instanceof AbstractRecipeBookScreen<?>)) {
            return false;
        }

        // Cast through the accessor mixin to reach the private recipeBookComponent field.
        var accessor = (MKRecipeBookAccessor) mc.screen;
        return accessor.menuKit$getRecipeBookComponent().isVisible();
    }

    /**
     * Sets the recipe book's visibility on the active screen.
     *
     * <p>If the requested state already matches the current state, this is a no-op.
     * If the active screen does not have a recipe book, this does nothing.
     *
     * <p>Internally calls {@code RecipeBookComponent.toggleVisibility()}, which is
     * the same codepath vanilla uses when the player clicks the recipe book button.
     *
     * @param open {@code true} to show the recipe book, {@code false} to hide it
     */
    public static void setRecipeBookOpen(boolean open) {
        var mc = Minecraft.getInstance();

        // Guard: only screens with a recipe book component can be toggled.
        if (!(mc.screen instanceof AbstractRecipeBookScreen<?>)) {
            return;
        }

        // Reach into the private field via the accessor mixin.
        var accessor = (MKRecipeBookAccessor) mc.screen;
        var recipeBook = accessor.menuKit$getRecipeBookComponent();

        // Only toggle if the current state differs from the requested state.
        // toggleVisibility() is a flip, so calling it when already in the
        // desired state would incorrectly reverse it.
        if (recipeBook.isVisible() != open) {
            recipeBook.toggleVisibility();
        }
    }
}
