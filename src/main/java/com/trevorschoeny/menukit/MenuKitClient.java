package com.trevorschoeny.menukit;

import com.trevorschoeny.menukit.inject.MenuChrome;
import com.trevorschoeny.menukit.inject.ScreenPanelRegistry;
import com.trevorschoeny.menukit.inject.VanillaSlotGroupResolvers;
import com.trevorschoeny.menukit.mixin.AbstractContainerScreenAccessor;
import com.trevorschoeny.menukit.mixin.MKRecipeBookAccessor;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
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
 *       lines to item tooltips. Always on post-M3 scope-down (see
 *       POST_PHASE_11.md: "MenuKit own-config primitive" is a deferred
 *       mechanism candidate; until a real consumer need surfaces, tooltips
 *       being always-on is the reasonable default).</li>
 *   <li><b>Recipe book utilities</b> — read/toggle visibility of the
 *       vanilla recipe book on any screen extending
 *       {@link AbstractRecipeBookScreen}.</li>
 * </ul>
 *
 * <p>Both exist here because they're cross-cutting concerns that benefit
 * every inventory screen, not features tied to any single consumer mod.
 */
public class MenuKitClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MenuKit.initClient();

        // Item Tips — enriched tooltips showing durability and food stats.
        // Registered at the framework level because it benefits ALL inventory
        // screens. The toggle lives in the "trevmods" family config so users
        // can disable it in settings.
        registerItemTipsCallback();

        // M7 — vanilla menu-chrome providers. Registered at client init so
        // any region-aware ScreenPanelAdapter constructed later sees
        // chrome-aware origin resolution. See M7 design doc §3.3 for scope.
        registerVanillaMenuChrome();

        // M8 — vanilla slot-group resolvers for SlotGroupContext dispatch.
        // Must register before ScreenPanelRegistry.init so the first screen-
        // open can resolve categories correctly. See M8 §6 for the 22 menu
        // classes covered.
        VanillaSlotGroupResolvers.registerAll();

        // M8 — library-owned ScreenEvents.AFTER_INIT dispatch for MenuContext
        // + SlotGroupContext adapters. Replaces per-consumer listener
        // boilerplate. See M8_FOUR_CONTEXT_MODEL.md §8 for design. Registered
        // after chrome + resolvers so the first screen-open's orphan
        // checkpoint sees all region-based adapters that completed their
        // targeting declarations during mod init.
        ScreenPanelRegistry.init();
    }

    // ══════════════════════════════════════════════════════════════════════
    // M7 — Vanilla menu chrome
    // ══════════════════════════════════════════════════════════════════════
    //
    // v1 scope is evidence-driven: screens that current library work (V2
    // probes, V4.2's inventory decoration) actually exercises. Other
    // recipe-book screens (CraftingScreen, FurnaceScreen, SmokerScreen,
    // BlastFurnaceScreen) are candidate additions pending consumer evidence.

    /**
     * Shared recipe-book chrome formula used by both {@link InventoryScreen}
     * and {@link CraftingScreen}. Derived from vanilla's
     * {@code RecipeBookComponent.updateTabs}: when the book is visible and
     * the screen is wide enough, the book body is 147px to the left of the
     * vanilla frame and the filter-tab column inserts an additional ~31px
     * beyond that.
     *
     * <p>Assumes {@code xOffset = 86} when visible — the constant in
     * vanilla's {@code AbstractRecipeBookScreen.getXOffset(boolean narrow)}
     * for both player-inventory and crafting-table contexts. If a subclass
     * overrides {@code getXOffset} with a different value, its provider
     * must compute the offset dynamically rather than using this shared
     * formula.
     */
    private static MenuChrome.ChromeExtents recipeBookChromeFor(
            AbstractContainerScreenAccessor screenAcc, int screenWidth) {
        int currentLeftPos = screenAcc.trevorMod$getLeftPos();
        int tabLeft = (screenWidth - 147) / 2 - 116;
        int chromeLeft = currentLeftPos - tabLeft;
        if (chromeLeft <= 0) return MenuChrome.ChromeExtents.NONE;
        return new MenuChrome.ChromeExtents(0, chromeLeft, 0, 0);
    }

    private static void registerVanillaMenuChrome() {
        // CreativeModeInventoryScreen: two tab rows above + below the
        // declared 195×136 frame. The TAB_HEIGHT=32 constant includes 3-4px
        // of transparent sprite padding around each tab's visible shape;
        // anchoring to 32 leaves probes floating too far from the visible
        // tab edge. Values below come from vanilla's own hit-test geometry
        // in checkTabHovering (21×27 at sprite offset +3, +3):
        //   Top visible tab: topPos - 25 to topPos + 2  → 25px above frame
        //   Bottom visible tab: topPos + iH - 1 to topPos + iH + 26  → 26px below
        // Asymmetry (25 vs 26) matches vanilla's 1px deeper bottom-tab bias.
        // Probes land STACK_GAP=2 past the visible edge — clean 2px gap.
        MenuChrome.register(CreativeModeInventoryScreen.class,
                screen -> new MenuChrome.ChromeExtents(25, 0, 0, 26));

        // InventoryScreen: recipe book widget (when visible) extends left of
        // the inventory frame by the book-body width (147px) PLUS the filter
        // tab column (~31px with +35-wide tab buttons). Formula lives in
        // recipeBookChromeFor — shared with CraftingScreen since both have
        // xOffset=86 in vanilla AbstractRecipeBookScreen.
        //
        // Per-frame recompute because screen.width changes on resize. Skip
        // chrome when the book is in "widthTooNarrow" mode (screen width
        // < 379) — vanilla overlays the book on top of the inventory
        // instead of shifting the frame, so there's no clean "left of the
        // book" space to anchor LEFT_ALIGN regions to.
        MenuChrome.register(InventoryScreen.class,
                screen -> recipeBookChromeIfOpen((AbstractRecipeBookScreen<?>) screen));

        // CraftingScreen: same recipe-book treatment as the player inventory.
        // V2 completeness pass adds this as evidence that M7's pattern
        // generalizes to other AbstractRecipeBookScreen subclasses. If the
        // vanilla xOffset turns out to differ for CraftingScreen (the
        // provider formula assumes 86), probes will visibly misalign and
        // we'll learn the formula is per-screen rather than universal.
        // Outcome informs M7 v2 scope — either "add Furnace/Smoker/
        // BlastFurnace with the same formula" or "extract per-screen
        // getXOffset access and compute dynamically."
        MenuChrome.register(CraftingScreen.class,
                screen -> recipeBookChromeIfOpen((AbstractRecipeBookScreen<?>) screen));
    }

    /**
     * Returns chrome extents for a recipe-book screen iff the book is
     * visible and the screen is wide enough for the book to shift the
     * frame (not overlay it). Otherwise {@link MenuChrome.ChromeExtents#NONE}.
     */
    private static MenuChrome.ChromeExtents recipeBookChromeIfOpen(
            AbstractRecipeBookScreen<?> screen) {
        var rbAccessor = (MKRecipeBookAccessor) screen;
        if (!rbAccessor.menuKit$getRecipeBookComponent().isVisible()) {
            return MenuChrome.ChromeExtents.NONE;
        }
        if (screen.width < 379) {
            return MenuChrome.ChromeExtents.NONE;
        }
        var bndsAccessor = (AbstractContainerScreenAccessor) screen;
        return recipeBookChromeFor(bndsAccessor, screen.width);
    }

    // ══════════════════════════════════════════════════════════════════════
    // Item Tips
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Registers a Fabric {@link ItemTooltipCallback} that appends enriched
     * info lines to item tooltips. Always-on post-M3 scope-down — no runtime
     * toggle (MenuKit owns no persistent config primitive of its own).
     *
     * <p>Why Fabric callback instead of a mixin? The callback is the idiomatic
     * Fabric approach for tooltip modification — it fires after vanilla has
     * built its tooltip lines, and multiple mods can participate without
     * conflicting mixin targets.
     */
    private static void registerItemTipsCallback() {
        ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipType, lines) -> {
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
