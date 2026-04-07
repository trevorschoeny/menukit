package com.trevorschoeny.menukit;

import com.trevorschoeny.menukit.config.GeneralOption;
import com.trevorschoeny.menukit.region.MKRegion;
import com.trevorschoeny.menukit.region.MKRegionGroup;
import com.trevorschoeny.menukit.region.MKRegionRegistry;
import com.trevorschoeny.menukit.mixin.AbstractContainerScreenAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.ShulkerBoxBlock;

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

    // ── Total Count (context-aware) ─────────────────────────────────────

    // Name of the region group that combines hotbar + main inventory.
    // Registered by InventoryPlus at mod init. When hovering within this
    // group, we count across all member regions AND inside containers
    // (shulker boxes, bundles) found in those regions.
    private static final String PLAYER_STORAGE_GROUP = "player_storage";

    // Shulker boxes always have 27 slots
    private static final int SHULKER_SIZE = 27;

    /**
     * Returns a "Total: N" line showing how many of this item the player
     * has in the relevant context.
     *
     * <p><b>Context-aware rules:</b>
     * <ul>
     *   <li><b>Player inventory</b> (hotbar or main): counts across the
     *       entire {@code player_storage} group, plus items inside shulker
     *       boxes and bundles within those slots. This reflects the player's
     *       true available supply.</li>
     *   <li><b>Other containers</b> (chests, peek views, etc.): counts only
     *       within that container's single region.</li>
     * </ul>
     *
     * <p>Falls back to total player inventory count when no region or group
     * context is available (non-container screen, no hovered slot, etc.).
     *
     * <p>Only shown when the total exceeds the hovered stack's own count
     * (showing "Total: 32" when hovering the only stack of 32 is redundant).
     *
     * @param stack the item to count
     * @return singleton list with the count line, or empty if not useful
     */
    public static List<Component> totalCountTip(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return List.of();

        int total = -1; // sentinel: no context-aware count yet

        if (mc.screen instanceof AbstractContainerScreen<?> containerScreen) {
            // Get the hovered slot — either MenuKit's own tracking (works for
            // MKSlots outside vanilla bounds) or vanilla's hoveredSlot via accessor
            Slot hovered = MenuKit.getHoveredMKSlot();
            if (hovered == null) {
                hovered = ((AbstractContainerScreenAccessor) containerScreen)
                        .trevorMod$getHoveredSlot();
            }

            if (hovered != null) {
                AbstractContainerMenu menu = containerScreen.getMenu();

                // Check if the hovered slot is in the player_storage group.
                // If so, count across the whole group + inside containers.
                MKRegionGroup playerGroup = MKRegionRegistry.getGroup(
                        menu, PLAYER_STORAGE_GROUP);

                if (playerGroup != null && playerGroup.containsMenuSlot(hovered.index)) {
                    // Player inventory context: group-wide count + container contents
                    total = playerGroup.countItem(stack)
                            + countInsideContainers(playerGroup, stack);
                } else {
                    // Non-player region (chest, peek, etc.): count within that region only.
                    // Dynamic containers (e.g., peek) get regions registered automatically
                    // when their panel is shown via MenuKit.showPanel().
                    MKRegion region = MKRegionRegistry.getRegionForSlot(menu, hovered.index);
                    if (region != null) {
                        total = countInRegion(region, stack);
                    }
                }
            }
        }

        // Fallback: scan entire player inventory if no region/group context
        if (total < 0) {
            total = countInInventory(mc.player.getInventory(), stack);
        }

        // Skip if total doesn't exceed the hovered stack's own count —
        // the slot already shows that number, so "Total: N" adds nothing
        if (total <= stack.getCount()) return List.of();

        MutableComponent line = Component.literal("Total: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(total))
                        .withStyle(ChatFormatting.WHITE));

        return List.of(Component.empty(), line);
    }

    // ── Container Content Scanning ──────────────────────────────────────

    /**
     * Counts items matching {@code target} that are stored <b>inside</b>
     * shulker boxes and bundles found in the given region group.
     *
     * <p>This looks one level deep — it does not recurse into nested containers
     * (e.g., a shulker inside a bundle). Each container item in the group's
     * slots is opened and its contents scanned for matches.
     *
     * @param group  the region group whose slots contain the containers
     * @param target the item to match (type + components)
     * @return total count of matching items found inside containers
     */
    private static int countInsideContainers(MKRegionGroup group, ItemStack target) {
        int count = 0;

        // Iterate every slot across all regions in the group
        for (MKRegion region : group.regions()) {
            for (int i = 0; i < region.size(); i++) {
                ItemStack slotStack = region.getItem(i);
                if (slotStack.isEmpty()) continue;

                // Shulker boxes: read ItemContainerContents
                if (isShulkerBox(slotStack)) {
                    count += countInsideShulker(slotStack, target);
                }

                // Bundles: read BundleContents
                if (isBundle(slotStack)) {
                    count += countInsideBundle(slotStack, target);
                }
            }
        }

        return count;
    }

    /**
     * Counts items matching {@code target} inside a shulker box item.
     * Reads the {@link ItemContainerContents} component and scans all 27 slots.
     */
    private static int countInsideShulker(ItemStack shulkerStack, ItemStack target) {
        ItemContainerContents contents = shulkerStack.get(DataComponents.CONTAINER);
        if (contents == null) return 0;

        // Copy into a mutable list so we can iterate by index
        NonNullList<ItemStack> items = NonNullList.withSize(SHULKER_SIZE, ItemStack.EMPTY);
        contents.copyInto(items);

        int count = 0;
        for (ItemStack item : items) {
            if (!item.isEmpty() && ItemStack.isSameItemSameComponents(item, target)) {
                count += item.getCount();
            }
        }
        return count;
    }

    /**
     * Counts items matching {@code target} inside a bundle item.
     * Reads the {@link BundleContents} component and iterates its items.
     */
    private static int countInsideBundle(ItemStack bundleStack, ItemStack target) {
        BundleContents contents = bundleStack.get(DataComponents.BUNDLE_CONTENTS);
        if (contents == null) return 0;

        int count = 0;
        for (ItemStack item : contents.items()) {
            if (!item.isEmpty() && ItemStack.isSameItemSameComponents(item, target)) {
                count += item.getCount();
            }
        }
        return count;
    }

    // ── Region / Inventory Counting ─────────────────────────────────────

    /**
     * Counts items matching {@code target} within a single region.
     * Used for non-player containers (chests, peek views, etc.)
     * where we only want the count within that container's slots.
     */
    private static int countInRegion(MKRegion region, ItemStack target) {
        int count = 0;
        for (int i = 0; i < region.size(); i++) {
            ItemStack slotStack = region.getItem(i);
            if (!slotStack.isEmpty()
                    && ItemStack.isSameItemSameComponents(slotStack, target)) {
                count += slotStack.getCount();
            }
        }
        return count;
    }

    /**
     * Counts items matching {@code target} across all inventory slots.
     * Used as fallback when no region context is available.
     */
    private static int countInInventory(Inventory inventory, ItemStack target) {
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack slotStack = inventory.getItem(i);
            if (!slotStack.isEmpty()
                    && ItemStack.isSameItemSameComponents(slotStack, target)) {
                count += slotStack.getCount();
            }
        }
        return count;
    }

    // ── Container Type Checks ───────────────────────────────────────────

    /** Returns true if the given ItemStack is a shulker box (any color). */
    private static boolean isShulkerBox(ItemStack stack) {
        return stack.getItem() instanceof BlockItem bi
                && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    /** Returns true if the given ItemStack is a bundle (has BundleContents). */
    private static boolean isBundle(ItemStack stack) {
        return stack.has(DataComponents.BUNDLE_CONTENTS);
    }
}
