package com.trevorschoeny.menukit;

import net.minecraft.world.inventory.*;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Static registry that defines, for each vanilla menu type, what container
 * groups exist and which menu slot indices they map to.
 *
 * <p>This is the single source of truth for "what slots belong to what container"
 * across all vanilla screens. When a menu is constructed, MenuKit reads this
 * mapping to auto-create {@link MKContainer} wrappers.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKContainerMapping {

    /**
     * A group of contiguous slots in a menu that belong to one logical container.
     *
     * @param name          MK container name (e.g., "mk:hotbar", "mk:chest")
     * @param menuSlotStart first menu slot index (inclusive)
     * @param menuSlotEnd   last menu slot index (inclusive)
     * @param persistence   how items in this group behave
     */
    public record SlotGroup(
            String name,
            int menuSlotStart,
            int menuSlotEnd,
            MKContainerDef.Persistence persistence
    ) {
        /** Number of slots in this group. */
        public int size() {
            return menuSlotEnd - menuSlotStart + 1;
        }
    }

    // ── Persistence shortcuts ─────────────────────────────────────────────
    private static final MKContainerDef.Persistence P = MKContainerDef.Persistence.PERSISTENT;
    private static final MKContainerDef.Persistence T = MKContainerDef.Persistence.TRANSIENT;
    private static final MKContainerDef.Persistence O = MKContainerDef.Persistence.OUTPUT;

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Returns all slot groups for a given menu instance and context.
     * The player inventory groups (hotbar, main, armor, offhand) are included
     * automatically for menus that contain them.
     *
     * @param menu    the menu instance (used to determine slot count for variable-size menus)
     * @param context the MKContext (determines which container-specific groups exist)
     * @return list of SlotGroups in this menu
     */
    public static List<SlotGroup> getSlotGroups(AbstractContainerMenu menu, @Nullable MKContext context) {
        List<SlotGroup> groups = new ArrayList<>();

        if (context == null) return groups;

        // Add context-specific (non-player) containers first
        addContextContainers(groups, menu, context);

        // Add player inventory containers (present in almost all menus)
        addPlayerInventory(groups, menu, context);

        return groups;
    }

    // ── Player Inventory ──────────────────────────────────────────────────

    /**
     * Adds the 4 player inventory container groups to the list.
     * The player inventory slots are always at the END of the menu's slot list
     * in interaction screens, but at specific positions in InventoryMenu.
     */
    private static void addPlayerInventory(List<SlotGroup> groups,
                                            AbstractContainerMenu menu,
                                            MKContext context) {
        switch (context) {
            // ── Personal screens ──────────────────────────────────────────
            case SURVIVAL_INVENTORY -> {
                // InventoryMenu layout:
                // 0    = crafting result
                // 1-4  = crafting 2x2
                // 5-8  = armor (helmet=5, chest=6, legs=7, boots=8)
                // 9-35 = main inventory (27 slots)
                // 36-44 = hotbar (9 slots)
                // 45   = offhand
                groups.add(new SlotGroup("mk:armor", 5, 8, P));
                groups.add(new SlotGroup("mk:main_inventory", 9, 35, P));
                groups.add(new SlotGroup("mk:hotbar", 36, 44, P));
                groups.add(new SlotGroup("mk:offhand", 45, 45, P));
            }

            case CREATIVE_INVENTORY, CREATIVE_TABS -> {
                // Creative mode uses the same InventoryMenu on the server,
                // but the client screen is completely different.
                // The InventoryMenu slot layout is the same as survival.
                groups.add(new SlotGroup("mk:armor", 5, 8, P));
                groups.add(new SlotGroup("mk:main_inventory", 9, 35, P));
                groups.add(new SlotGroup("mk:hotbar", 36, 44, P));
                groups.add(new SlotGroup("mk:offhand", 45, 45, P));
            }

            // ── Interaction screens with standard player inventory ─────────
            // All interaction menus add player inventory via addStandardInventorySlots(),
            // which places 27 main inventory slots followed by 9 hotbar slots at the
            // END of the slot list. No armor or offhand in interaction menus.
            default -> {
                if (MKContext.ALL_WITH_PLAYER_INVENTORY.contains(context)) {
                    int totalSlots = menu.slots.size();
                    // MKSlots may be appended after vanilla slots — count only vanilla slots
                    int vanillaSlotCount = countVanillaSlots(menu);
                    int hotbarEnd = vanillaSlotCount - 1;
                    int hotbarStart = hotbarEnd - 8;   // 9 hotbar slots
                    int mainEnd = hotbarStart - 1;
                    int mainStart = mainEnd - 26;      // 27 main inventory slots

                    if (mainStart >= 0 && hotbarEnd < totalSlots) {
                        groups.add(new SlotGroup("mk:main_inventory", mainStart, mainEnd, P));
                        groups.add(new SlotGroup("mk:hotbar", hotbarStart, hotbarEnd, P));
                    }
                }
            }
        }
    }

    // ── Context-Specific Containers ───────────────────────────────────────

    /**
     * Adds the non-player containers for a specific context.
     */
    private static void addContextContainers(List<SlotGroup> groups,
                                              AbstractContainerMenu menu,
                                              MKContext context) {
        switch (context) {
            // ── Personal crafting ─────────────────────────────────────────
            case SURVIVAL_INVENTORY -> {
                groups.add(new SlotGroup("mk:craft_result", 0, 0, O));
                groups.add(new SlotGroup("mk:craft_2x2", 1, 4, T));
            }

            // ── Storage ───────────────────────────────────────────────────
            case CHEST, ENDER_CHEST, BARREL -> {
                // ChestMenu: container slots are 0 to (totalVanilla - 37)
                int containerEnd = countVanillaSlots(menu) - 37;
                if (containerEnd >= 0) {
                    groups.add(new SlotGroup("mk:chest", 0, containerEnd, P));
                }
            }
            case DOUBLE_CHEST -> {
                int containerEnd = countVanillaSlots(menu) - 37;
                if (containerEnd >= 0) {
                    groups.add(new SlotGroup("mk:chest", 0, containerEnd, P));
                }
            }
            case SHULKER_BOX -> {
                groups.add(new SlotGroup("mk:shulker", 0, 26, P));
            }
            case HOPPER -> {
                groups.add(new SlotGroup("mk:hopper", 0, 4, P));
            }
            case DISPENSER -> {
                groups.add(new SlotGroup("mk:dispenser", 0, 8, P));
            }

            // ── Crafting ──────────────────────────────────────────────────
            case CRAFTING_TABLE -> {
                groups.add(new SlotGroup("mk:crafting_3x3_result", 0, 0, O));
                groups.add(new SlotGroup("mk:crafting_3x3", 1, 9, T));
            }
            case STONECUTTER -> {
                groups.add(new SlotGroup("mk:stonecutter_input", 0, 0, T));
                groups.add(new SlotGroup("mk:stonecutter_output", 1, 1, O));
            }
            case SMITHING_TABLE -> {
                groups.add(new SlotGroup("mk:smithing_template", 0, 0, T));
                groups.add(new SlotGroup("mk:smithing_base", 1, 1, T));
                groups.add(new SlotGroup("mk:smithing_addition", 2, 2, T));
                groups.add(new SlotGroup("mk:smithing_output", 3, 3, O));
            }
            case LOOM -> {
                groups.add(new SlotGroup("mk:loom_banner", 0, 0, T));
                groups.add(new SlotGroup("mk:loom_dye", 1, 1, T));
                groups.add(new SlotGroup("mk:loom_pattern", 2, 2, T));
                groups.add(new SlotGroup("mk:loom_output", 3, 3, O));
            }
            case CARTOGRAPHY_TABLE -> {
                groups.add(new SlotGroup("mk:cartography_map", 0, 0, T));
                groups.add(new SlotGroup("mk:cartography_material", 1, 1, T));
                groups.add(new SlotGroup("mk:cartography_output", 2, 2, O));
            }
            case GRINDSTONE -> {
                groups.add(new SlotGroup("mk:grindstone_input_1", 0, 0, T));
                groups.add(new SlotGroup("mk:grindstone_input_2", 1, 1, T));
                groups.add(new SlotGroup("mk:grindstone_output", 2, 2, O));
            }
            case CRAFTER -> {
                groups.add(new SlotGroup("mk:crafter", 0, 8, P));
            }

            // ── Processing ────────────────────────────────────────────────
            case FURNACE, BLAST_FURNACE, SMOKER -> {
                groups.add(new SlotGroup("mk:furnace_input", 0, 0, P));
                groups.add(new SlotGroup("mk:furnace_fuel", 1, 1, P));
                // Furnace output is OUTPUT (can only take, not put) but items persist
                groups.add(new SlotGroup("mk:furnace_output", 2, 2, O));
            }
            case BREWING_STAND -> {
                groups.add(new SlotGroup("mk:brewing_potions", 0, 2, P));
                groups.add(new SlotGroup("mk:brewing_ingredient", 3, 3, P));
                groups.add(new SlotGroup("mk:brewing_fuel", 4, 4, P));
            }

            // ── Special ───────────────────────────────────────────────────
            case ANVIL -> {
                groups.add(new SlotGroup("mk:anvil_input_1", 0, 0, T));
                groups.add(new SlotGroup("mk:anvil_input_2", 1, 1, T));
                groups.add(new SlotGroup("mk:anvil_output", 2, 2, O));
            }
            case ENCHANTING_TABLE -> {
                groups.add(new SlotGroup("mk:enchanting_item", 0, 0, T));
                groups.add(new SlotGroup("mk:enchanting_lapis", 1, 1, T));
            }
            case BEACON -> {
                groups.add(new SlotGroup("mk:beacon_payment", 0, 0, T));
            }
            case VILLAGER_TRADING -> {
                groups.add(new SlotGroup("mk:merchant_input_1", 0, 0, T));
                groups.add(new SlotGroup("mk:merchant_input_2", 1, 1, T));
                groups.add(new SlotGroup("mk:merchant_output", 2, 2, O));
            }
            case HORSE_INVENTORY -> {
                // Horse inventory: slot 0 = saddle, slot 1 = armor
                // Slots 2+ = chest inventory (if donkey/llama with chest)
                groups.add(new SlotGroup("mk:horse_saddle", 0, 0, P));
                groups.add(new SlotGroup("mk:horse_armor", 1, 1, P));
                int vanillaSlots = countVanillaSlots(menu);
                int chestSlots = vanillaSlots - 38; // subtract 2 (saddle+armor) + 36 (player inv)
                if (chestSlots > 0) {
                    groups.add(new SlotGroup("mk:horse_chest", 2, 1 + chestSlots, P));
                }
            }
            case LECTERN -> {
                groups.add(new SlotGroup("mk:lectern_book", 0, 0, O));
            }

            default -> {
                // Unknown context — no context-specific containers
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Counts vanilla slots in a menu (excludes appended MKSlots).
     * MKSlots are always appended after vanilla slots, so we count
     * slots that are NOT instances of MKSlot.
     */
    static int countVanillaSlots(AbstractContainerMenu menu) {
        int count = 0;
        for (int i = 0; i < menu.slots.size(); i++) {
            MKSlotState s = MKSlotStateRegistry.get(menu.slots.get(i));
            if (s != null && s.isMenuKitSlot()) break;
            count++;
        }
        return count;
    }

    /**
     * Returns all container names that exist in a given context (static lookup).
     * Useful for mod authors to know what containers are available without a menu instance.
     */
    public static List<String> getContainerNames(MKContext context) {
        // Create a dummy list and extract just the names
        // For static lookup, we can't determine variable-size containers (chest/horse)
        List<String> names = new ArrayList<>();

        // Player inventory (present in all contexts with player inventory)
        if (MKContext.ALL_WITH_PLAYER_INVENTORY.contains(context)) {
            names.add("mk:main_inventory");
            names.add("mk:hotbar");
        }
        if (MKContext.PERSONAL.contains(context)) {
            names.add("mk:armor");
            names.add("mk:offhand");
        }

        // Context-specific — use switch to add known names
        switch (context) {
            case SURVIVAL_INVENTORY -> { names.add("mk:craft_result"); names.add("mk:craft_2x2"); }
            case CHEST, DOUBLE_CHEST, ENDER_CHEST, BARREL -> names.add("mk:chest");
            case SHULKER_BOX -> names.add("mk:shulker");
            case HOPPER -> names.add("mk:hopper");
            case DISPENSER -> names.add("mk:dispenser");
            case CRAFTING_TABLE -> { names.add("mk:crafting_3x3_result"); names.add("mk:crafting_3x3"); }
            case STONECUTTER -> { names.add("mk:stonecutter_input"); names.add("mk:stonecutter_output"); }
            case SMITHING_TABLE -> { names.add("mk:smithing_template"); names.add("mk:smithing_base"); names.add("mk:smithing_addition"); names.add("mk:smithing_output"); }
            case LOOM -> { names.add("mk:loom_banner"); names.add("mk:loom_dye"); names.add("mk:loom_pattern"); names.add("mk:loom_output"); }
            case CARTOGRAPHY_TABLE -> { names.add("mk:cartography_map"); names.add("mk:cartography_material"); names.add("mk:cartography_output"); }
            case GRINDSTONE -> { names.add("mk:grindstone_input_1"); names.add("mk:grindstone_input_2"); names.add("mk:grindstone_output"); }
            case CRAFTER -> names.add("mk:crafter");
            case FURNACE, BLAST_FURNACE, SMOKER -> { names.add("mk:furnace_input"); names.add("mk:furnace_fuel"); names.add("mk:furnace_output"); }
            case BREWING_STAND -> { names.add("mk:brewing_potions"); names.add("mk:brewing_ingredient"); names.add("mk:brewing_fuel"); }
            case ANVIL -> { names.add("mk:anvil_input_1"); names.add("mk:anvil_input_2"); names.add("mk:anvil_output"); }
            case ENCHANTING_TABLE -> { names.add("mk:enchanting_item"); names.add("mk:enchanting_lapis"); }
            case BEACON -> names.add("mk:beacon_payment");
            case VILLAGER_TRADING -> { names.add("mk:merchant_input_1"); names.add("mk:merchant_input_2"); names.add("mk:merchant_output"); }
            case HORSE_INVENTORY -> { names.add("mk:horse_saddle"); names.add("mk:horse_armor"); }
            case LECTERN -> names.add("mk:lectern_book");
            default -> {}
        }

        return names;
    }
}
