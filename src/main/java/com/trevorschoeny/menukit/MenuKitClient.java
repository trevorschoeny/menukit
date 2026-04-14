package com.trevorschoeny.menukit;

import com.trevorschoeny.menukit.config.GeneralOption;
import com.trevorschoeny.menukit.config.MKFamily;
import com.trevorschoeny.menukit.mixin.MKRecipeBookAccessor;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side entry point for MenuKit.
 *
 * <p>Also provides two orthogonal convenience APIs:
 * <ul>
 *   <li><b>Item tooltip enrichment</b> — appends durability and food-stat
 *       lines to item tooltips. Gated by the {@code trevmods} family's
 *       {@code SHOW_ITEM_TIPS} option so users can disable it.</li>
 *   <li><b>Recipe book utilities</b> — read/toggle visibility of the
 *       vanilla recipe book on any screen extending
 *       {@link AbstractRecipeBookScreen}.</li>
 * </ul>
 *
 * <p>Both exist here because they're cross-cutting concerns that benefit
 * every inventory screen, not features tied to any single consumer mod.
 */
public class MenuKitClient implements ClientModInitializer {

    // ── Item Tips Configuration ──────────────────────────────────────────
    //
    // The GeneralOption descriptor that controls whether item tips are shown.
    // Public so consumers registering into the "trevmods" family can reference
    // the same descriptor when building their config UI.

    /** Family-wide toggle for enriched item tooltips. Default: enabled. */
    public static final GeneralOption<Boolean> SHOW_ITEM_TIPS =
            new GeneralOption<>("show_item_tips", true, Boolean.class);

    // Cached family reference — tooltip callbacks fire every frame while
    // hovering, so we avoid repeated map lookups on this hot path.
    private static MKFamily cachedFamily;

    @Override
    public void onInitializeClient() {
        MenuKit.initClient();

        // Item Tips — enriched tooltips showing durability and food stats.
        // Registered at the framework level because it benefits ALL inventory
        // screens. The toggle lives in the "trevmods" family config so users
        // can disable it in settings.
        registerItemTipsCallback();
    }

    // ══════════════════════════════════════════════════════════════════════
    // Item Tips
    // ══════════════════════════════════════════════════════════════════════

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
            if (!cachedFamily.getGeneral(SHOW_ITEM_TIPS)) return;

            lines.addAll(generateTips(stack));
        });
    }

    /**
     * Returns extra tooltip lines to append for the given stack. Each section
     * prefixes a blank separator line for readability.
     *
     * @return extra lines (never null, may be empty)
     */
    private static List<Component> generateTips(ItemStack stack) {
        if (stack.isEmpty()) return List.of();

        List<Component> tips = new ArrayList<>();
        tips.addAll(durabilityTip(stack));
        tips.addAll(foodTip(stack));
        return tips;
    }

    /**
     * Returns a durability line for damageable items.
     * Color-coded: green (&gt;50% remaining), yellow (25–50%), red (&lt;25%).
     */
    private static List<Component> durabilityTip(ItemStack stack) {
        if (!stack.isDamageableItem()) return List.of();

        int maxDamage = stack.getMaxDamage();
        int remaining = maxDamage - stack.getDamageValue();
        int percent = (int) ((remaining / (float) maxDamage) * 100);

        ChatFormatting color;
        if (percent > 50) color = ChatFormatting.GREEN;
        else if (percent >= 25) color = ChatFormatting.YELLOW;
        else color = ChatFormatting.RED;

        MutableComponent line = Component.literal("Durability: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(remaining + " / " + maxDamage)
                        .withStyle(color))
                .append(Component.literal(" (" + percent + "%)")
                        .withStyle(color));

        return List.of(Component.empty(), line);
    }

    /**
     * Returns nutrition and effective-saturation lines for edible items.
     * Effective saturation = nutrition * saturation-multiplier * 2
     * (the raw food.saturation() is a multiplier, not points restored).
     */
    private static List<Component> foodTip(ItemStack stack) {
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null) return List.of();

        MutableComponent nutritionLine = Component.literal("Nutrition: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(food.nutrition()))
                        .withStyle(ChatFormatting.GOLD));

        float effectiveSaturation = food.nutrition() * food.saturation() * 2f;
        String satStr = String.format("%.1f", effectiveSaturation);
        MutableComponent saturationLine = Component.literal("Saturation: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(satStr)
                        .withStyle(ChatFormatting.GOLD));

        return List.of(Component.empty(), nutritionLine, saturationLine);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Recipe Book Utilities
    // ══════════════════════════════════════════════════════════════════════
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
     */
    public static boolean isRecipeBookOpen() {
        var mc = Minecraft.getInstance();

        if (!(mc.screen instanceof AbstractRecipeBookScreen<?>)) {
            return false;
        }

        var accessor = (MKRecipeBookAccessor) mc.screen;
        return accessor.menuKit$getRecipeBookComponent().isVisible();
    }

    /**
     * Sets the recipe book's visibility on the active screen.
     *
     * <p>No-op if the active screen doesn't have a recipe book or if the
     * requested state already matches the current state.
     *
     * @param open {@code true} to show the recipe book, {@code false} to hide it
     */
    public static void setRecipeBookOpen(boolean open) {
        var mc = Minecraft.getInstance();

        if (!(mc.screen instanceof AbstractRecipeBookScreen<?>)) {
            return;
        }

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
