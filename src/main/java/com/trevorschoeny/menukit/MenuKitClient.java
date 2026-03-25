package com.trevorschoeny.menukit;

import com.trevorschoeny.menukit.mixin.MKRecipeBookAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Client-side entry point for MenuKit.
 * Registers the MKScreen factory for standalone panel screens.
 *
 * <p>Also provides static utility methods for interacting with
 * client-only UI components (like the recipe book) so that
 * consumers don't need to cast to vanilla screen internals.
 */
public class MenuKitClient implements ClientModInitializer {

    // Cached family reference — tooltip callbacks fire every frame while hovering,
    // so we avoid repeated map lookups on this hot path.
    private static MKFamily cachedFamily;

    @Override
    public void onInitializeClient() {
        MenuKit.initClient();

        // Item Tips — enriched tooltips showing durability, food stats, and
        // inventory totals. Registered at the framework level because it
        // benefits ALL inventory screens. The toggle lives in the family
        // config ("trevmods") so users can disable it in settings.
        registerItemTipsCallback();
    }

    // ── Item Tips ────────────────────────────────────────────────────────────

    /**
     * Registers a Fabric {@link ItemTooltipCallback} that appends enriched
     * info lines to item tooltips. Uses the "trevmods" family's
     * {@code SHOW_ITEM_TIPS} general option as a runtime toggle.
     *
     * <p>Why Fabric callback instead of a mixin? The callback is the idiomatic
     * Fabric approach for tooltip modification — it fires after vanilla has
     * built its tooltip lines, and multiple mods can participate without
     * conflicting mixin targets.
     */
    private static void registerItemTipsCallback() {
        ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipType, lines) -> {
            // Check the family toggle — if the family hasn't been created yet
            // (shouldn't happen in normal flow, but guard defensively), default
            // to showing tips. The option defaults to true, so first-time users
            // see tips immediately.
            if (cachedFamily == null) cachedFamily = MenuKit.family("trevmods");
            if (!cachedFamily.getGeneral(MKItemTips.SHOW_ITEM_TIPS)) return;

            // Generate and append all tip lines. MKItemTips returns an empty
            // list when nothing applies, so this is always safe.
            List<Component> tips = MKItemTips.generateTips(stack);
            lines.addAll(tips);
        });
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
