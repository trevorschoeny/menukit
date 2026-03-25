package com.trevorschoeny.menukit;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure functions that generate enriched tooltip lines for items.
 *
 * <p>Each method takes an {@link ItemStack} (and optionally a player context)
 * and returns a list of {@link Component} lines to append to the tooltip.
 * Methods return empty lists when the tip doesn't apply (e.g., durability
 * for a non-damageable item), so callers can blindly addAll without checks.
 *
 * <p>Registered as a Fabric {@code ItemTooltipCallback} in {@link MenuKitClient}.
 * Gated by the {@code SHOW_ITEM_TIPS} family general option (registered by
 * InventoryPlus) so users can toggle it off in settings.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public final class MKItemTips {

    // The GeneralOption descriptor that controls whether item tips are shown.
    // Defined here so both MenuKitClient (reader) and InventoryPlusClient
    // (registrar) reference the same descriptor. The family ID is "trevmods".
    public static final GeneralOption<Boolean> SHOW_ITEM_TIPS =
            new GeneralOption<>("show_item_tips", true, Boolean.class);

    private MKItemTips() {} // utility class — no instances

    // ── Public Entry Point ───────────────────────────────────────────────

    /**
     * Generates all enriched tooltip lines for the given item stack.
     * Called by the {@code ItemTooltipCallback} registered in MenuKitClient.
     *
     * <p>Returns an empty list if item tips are disabled or the stack is empty.
     * Each section adds a blank separator line before its content for readability.
     *
     * @param stack the item being hovered
     * @return extra tooltip lines to append (never null, may be empty)
     */
    public static List<Component> generateTips(ItemStack stack) {
        if (stack.isEmpty()) return List.of();

        List<Component> tips = new ArrayList<>();

        // Durability — most immediately useful for tools/armor
        tips.addAll(durabilityTip(stack));

        // Enchantments — compact summary when vanilla's own lines are hidden
        // (Vanilla already shows enchantments by default, so we skip this to
        // avoid duplication. Kept as a method for future use if we want a
        // compact mode that hides vanilla lines and shows our summary instead.)

        // Food — nutrition and saturation for edible items
        tips.addAll(foodTip(stack));

        // Total count — how many of this item the player has across inventory
        tips.addAll(totalCountTip(stack));

        return tips;
    }

    // ── Durability ──────────────────────────────────────────────────────

    /**
     * Returns a durability line for damageable items.
     * Format: "Durability: 250 / 1561 (16%)" with color coding:
     * - Green (>50% remaining) — item is in good shape
     * - Yellow (25-50%) — getting worn
     * - Red (<25%) — nearly broken, needs repair
     *
     * @param stack the item to check
     * @return singleton list with the durability line, or empty if not damageable
     */
    public static List<Component> durabilityTip(ItemStack stack) {
        if (!stack.isDamageableItem()) return List.of();

        int maxDamage = stack.getMaxDamage();
        int currentDamage = stack.getDamageValue();
        int remaining = maxDamage - currentDamage;

        // Percentage of durability remaining (0-100)
        int percent = (int) ((remaining / (float) maxDamage) * 100);

        // Color thresholds:
        // Green  = healthy (>50%)
        // Yellow = worn (25-50%)
        // Red    = critical (<25%)
        ChatFormatting color;
        if (percent > 50) {
            color = ChatFormatting.GREEN;
        } else if (percent >= 25) {
            color = ChatFormatting.YELLOW;
        } else {
            color = ChatFormatting.RED;
        }

        MutableComponent line = Component.literal("Durability: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(remaining + " / " + maxDamage)
                        .withStyle(color))
                .append(Component.literal(" (" + percent + "%)")
                        .withStyle(color));

        return List.of(Component.empty(), line);
    }

    // ── Food Info ────────────────────────────────────────────────────────

    /**
     * Returns nutrition and saturation lines for edible items.
     * Format:
     *   "Nutrition: 6"
     *   "Saturation: 7.2"
     *
     * <p>Nutrition is in half-drumstick units (same as vanilla's hunger bar).
     * Saturation is the raw float value — higher means you stay full longer.
     *
     * @param stack the item to check
     * @return lines with food stats, or empty if not edible
     */
    public static List<Component> foodTip(ItemStack stack) {
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null) return List.of();

        // Nutrition line — integer half-drumsticks
        MutableComponent nutritionLine = Component.literal("Nutrition: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(food.nutrition()))
                        .withStyle(ChatFormatting.GOLD));

        // Saturation line — effective saturation points restored, not the raw modifier.
        // Vanilla's food.saturation() is a multiplier; the actual points restored
        // are nutrition * saturation * 2. E.g., golden carrot: 6 * 1.2 * 2 = 14.4
        float effectiveSaturation = food.nutrition() * food.saturation() * 2f;
        String satStr = String.format("%.1f", effectiveSaturation);
        MutableComponent saturationLine = Component.literal("Saturation: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(satStr)
                        .withStyle(ChatFormatting.GOLD));

        return List.of(Component.empty(), nutritionLine, saturationLine);
    }

    // ── Total Inventory Count ────────────────────────────────────────────

    /**
     * Returns a "Total in inventory: N" line showing how many of this item
     * the player has across all inventory slots (main inventory + armor + offhand).
     *
     * <p>Only shown when the player has more than 1 total (a single stack
     * doesn't need a "total" indicator — you can already see the count).
     * Uses {@link ItemStack#isSameItem} for matching — ignores NBT/components
     * so all swords of the same type are counted together regardless of
     * enchantments or damage.
     *
     * @param stack the item to count
     * @return singleton list with the count line, or empty if count <= 1
     *         or player is unavailable
     */
    public static List<Component> totalCountTip(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return List.of();

        Inventory inventory = mc.player.getInventory();
        int total = 0;

        // Scan all inventory slots: main (0-35), armor (36-39), offhand (40)
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack slotStack = inventory.getItem(i);
            if (!slotStack.isEmpty() && ItemStack.isSameItem(slotStack, stack)) {
                total += slotStack.getCount();
            }
        }

        // Skip if only one stack exists — the count is already visible on the item
        if (total <= 1) return List.of();

        MutableComponent line = Component.literal("Total in inventory: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(total))
                        .withStyle(ChatFormatting.WHITE));

        return List.of(Component.empty(), line);
    }
}
