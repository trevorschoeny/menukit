package com.trevorschoeny.menukit;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Central registry for {@link MKRegion}s. Resolves which regions exist for
 * a given menu instance and provides fast lookups by name or slot index.
 *
 * <p>This is the mixin-layer's single source of truth for "what container
 * indices belong together." Replaces the old panel-based slot grouping.
 *
 * <p>Regions are resolved once per menu construction and cached. They are
 * cleaned up when the menu is removed.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKRegionRegistry {

    // ── Persistence shortcuts ─────────────────────────────────────────────
    private static final MKContainerDef.Persistence P = MKContainerDef.Persistence.PERSISTENT;
    private static final MKContainerDef.Persistence T = MKContainerDef.Persistence.TRANSIENT;
    private static final MKContainerDef.Persistence O = MKContainerDef.Persistence.OUTPUT;

    // ── Live region cache per menu instance ────────────────────────────────
    // Key: System.identityHashCode(menu) — avoids holding strong references
    // to menu objects that should be GC'd
    private static final Map<Integer, List<MKRegion>> menuRegions = new HashMap<>();

    // ── Custom region definitions (registered by mods) ────────────────────
    // These are resolved alongside vanilla regions during menu construction
    private static final Map<String, CustomRegionDef> customRegionDefs = new LinkedHashMap<>();

    /**
     * Definition for a custom region registered by a mod.
     * The container is resolved per-player at menu construction time.
     */
    record CustomRegionDef(
            String name,
            int size,
            MKContainerDef.Persistence persistence,
            boolean shiftClickIn,
            boolean shiftClickOut
    ) {}

    // ── Registration (mod init time) ──────────────────────────────────────

    /**
     * Registers a custom region definition. Called when a mod registers
     * a container via {@code MenuKit.container("name").size(N).register()}.
     * The actual MKRegion is created at menu construction time.
     */
    public static void registerCustom(String name, int size,
                                       MKContainerDef.Persistence persistence,
                                       boolean shiftClickIn, boolean shiftClickOut) {
        customRegionDefs.put(name, new CustomRegionDef(name, size, persistence,
                shiftClickIn, shiftClickOut));
    }

    // ── Resolution (menu construction time) ───────────────────────────────

    /**
     * Resolves all regions for a menu instance. Called once per menu
     * construction from the mixin layer.
     *
     * <p>Creates MKRegion objects that carry direct references to the
     * actual Container backing each slot group. These regions are cached
     * and used for all subsequent lookups on this menu.
     *
     * @param menu    the menu instance
     * @param context the MKContext (determines which vanilla regions exist)
     * @return the resolved regions (also cached internally)
     */
    public static List<MKRegion> resolveForMenu(AbstractContainerMenu menu,
                                                  @Nullable MKContext context) {
        List<MKRegion> regions = new ArrayList<>();
        if (context == null) {
            menuRegions.put(System.identityHashCode(menu), regions);
            return regions;
        }

        // Add context-specific (non-player) regions first
        addContextRegions(regions, menu, context);

        // Add player inventory regions (present in almost all menus)
        addPlayerInventoryRegions(regions, menu, context);

        // Cache by menu identity
        menuRegions.put(System.identityHashCode(menu), regions);
        return regions;
    }

    // ── Lookups ───────────────────────────────────────────────────────────

    /** Returns all regions for a menu, or empty list if not resolved. */
    public static List<MKRegion> getRegions(AbstractContainerMenu menu) {
        return menuRegions.getOrDefault(System.identityHashCode(menu), List.of());
    }

    /** Finds a region by name in a menu, or null. */
    public static @Nullable MKRegion getRegion(AbstractContainerMenu menu, String name) {
        for (MKRegion region : getRegions(menu)) {
            if (region.name().equals(name)) return region;
        }
        return null;
    }

    /** Finds the region that owns a given menu slot index, or null. */
    public static @Nullable MKRegion getRegionForSlot(AbstractContainerMenu menu,
                                                        int menuSlotIndex) {
        for (MKRegion region : getRegions(menu)) {
            if (region.containsMenuSlot(menuSlotIndex)) return region;
        }
        return null;
    }

    /** Cleans up all region data for a closed menu. */
    public static void cleanup(AbstractContainerMenu menu) {
        menuRegions.remove(System.identityHashCode(menu));
    }

    // ── Player Inventory Regions ──────────────────────────────────────────

    /**
     * Adds the 4 player inventory regions. The player inventory slots are
     * at fixed positions in InventoryMenu but at the END of slot lists in
     * interaction menus.
     */
    private static void addPlayerInventoryRegions(List<MKRegion> regions,
                                                    AbstractContainerMenu menu,
                                                    MKContext context) {
        switch (context) {
            case SURVIVAL_INVENTORY, CREATIVE_INVENTORY, CREATIVE_TABS -> {
                // InventoryMenu layout:
                // 5-8  = armor (container indices 36-39)
                // 9-35 = main inventory (container indices 9-35)
                // 36-44 = hotbar (container indices 0-8)
                // 45   = offhand (container index 40)
                Container inv = getContainerForSlot(menu, 9); // main inventory slot
                if (inv == null) return;

                MKRegion armor = new MKRegion("mk:armor", inv, 36, 4, P, true, true);
                armor.setMenuSlotRange(5, 8);
                regions.add(armor);

                MKRegion main = new MKRegion("mk:main_inventory", inv, 9, 27, P, true, true);
                main.setMenuSlotRange(9, 35);
                regions.add(main);

                MKRegion hotbar = new MKRegion("mk:hotbar", inv, 0, 9, P, true, true);
                hotbar.setMenuSlotRange(36, 44);
                regions.add(hotbar);

                MKRegion offhand = new MKRegion("mk:offhand", inv, 40, 1, P, true, true);
                offhand.setMenuSlotRange(45, 45);
                regions.add(offhand);
            }

            default -> {
                // Interaction screens: player inventory at the END
                if (MKContext.ALL_WITH_PLAYER_INVENTORY.contains(context)) {
                    int vanillaSlotCount = countNonMKSlots(menu);
                    int hotbarEnd = vanillaSlotCount - 1;
                    int hotbarStart = hotbarEnd - 8;   // 9 hotbar slots
                    int mainEnd = hotbarStart - 1;
                    int mainStart = mainEnd - 26;      // 27 main inventory slots

                    if (mainStart >= 0 && hotbarEnd < menu.slots.size()) {
                        Container inv = getContainerForSlot(menu, mainStart);
                        if (inv == null) return;

                        MKRegion main = new MKRegion("mk:main_inventory", inv, 9, 27, P, true, true);
                        main.setMenuSlotRange(mainStart, mainEnd);
                        regions.add(main);

                        MKRegion hotbar = new MKRegion("mk:hotbar", inv, 0, 9, P, true, true);
                        hotbar.setMenuSlotRange(hotbarStart, hotbarEnd);
                        regions.add(hotbar);
                    }
                }
            }
        }
    }

    // ── Context-Specific Regions ──────────────────────────────────────────

    /**
     * Adds non-player regions for a specific context. Each region gets
     * the Container reference from the actual slot at that position.
     */
    private static void addContextRegions(List<MKRegion> regions,
                                            AbstractContainerMenu menu,
                                            MKContext context) {
        switch (context) {
            // ── Personal crafting ─────────────────────────────────────────
            case SURVIVAL_INVENTORY -> {
                addRegion(regions, menu, "mk:craft_result", 0, 0, O, false, true);
                addRegion(regions, menu, "mk:craft_2x2", 1, 4, T, true, true);
            }

            // ── Storage ───────────────────────────────────────────────────
            case CHEST, ENDER_CHEST, BARREL -> {
                int containerEnd = countNonMKSlots(menu) - 37;
                if (containerEnd >= 0)
                    addRegion(regions, menu, "mk:chest", 0, containerEnd, P, true, true);
            }
            case DOUBLE_CHEST -> {
                int containerEnd = countNonMKSlots(menu) - 37;
                if (containerEnd >= 0)
                    addRegion(regions, menu, "mk:chest", 0, containerEnd, P, true, true);
            }
            case SHULKER_BOX ->
                addRegion(regions, menu, "mk:shulker", 0, 26, P, true, true);
            case HOPPER ->
                addRegion(regions, menu, "mk:hopper", 0, 4, P, true, true);
            case DISPENSER ->
                addRegion(regions, menu, "mk:dispenser", 0, 8, P, true, true);

            // ── Crafting ──────────────────────────────────────────────────
            case CRAFTING_TABLE -> {
                addRegion(regions, menu, "mk:crafting_3x3_result", 0, 0, O, false, true);
                addRegion(regions, menu, "mk:crafting_3x3", 1, 9, T, true, true);
            }
            case STONECUTTER -> {
                addRegion(regions, menu, "mk:stonecutter_input", 0, 0, T, true, true);
                addRegion(regions, menu, "mk:stonecutter_output", 1, 1, O, false, true);
            }
            case SMITHING_TABLE -> {
                addRegion(regions, menu, "mk:smithing_template", 0, 0, T, true, true);
                addRegion(regions, menu, "mk:smithing_base", 1, 1, T, true, true);
                addRegion(regions, menu, "mk:smithing_addition", 2, 2, T, true, true);
                addRegion(regions, menu, "mk:smithing_output", 3, 3, O, false, true);
            }
            case LOOM -> {
                addRegion(regions, menu, "mk:loom_banner", 0, 0, T, true, true);
                addRegion(regions, menu, "mk:loom_dye", 1, 1, T, true, true);
                addRegion(regions, menu, "mk:loom_pattern", 2, 2, T, true, true);
                addRegion(regions, menu, "mk:loom_output", 3, 3, O, false, true);
            }
            case CARTOGRAPHY_TABLE -> {
                addRegion(regions, menu, "mk:cartography_map", 0, 0, T, true, true);
                addRegion(regions, menu, "mk:cartography_material", 1, 1, T, true, true);
                addRegion(regions, menu, "mk:cartography_output", 2, 2, O, false, true);
            }
            case GRINDSTONE -> {
                addRegion(regions, menu, "mk:grindstone_input_1", 0, 0, T, true, true);
                addRegion(regions, menu, "mk:grindstone_input_2", 1, 1, T, true, true);
                addRegion(regions, menu, "mk:grindstone_output", 2, 2, O, false, true);
            }
            case CRAFTER ->
                addRegion(regions, menu, "mk:crafter", 0, 8, P, true, true);

            // ── Processing ────────────────────────────────────────────────
            case FURNACE, BLAST_FURNACE, SMOKER -> {
                addRegion(regions, menu, "mk:furnace_input", 0, 0, P, true, true);
                addRegion(regions, menu, "mk:furnace_fuel", 1, 1, P, true, true);
                addRegion(regions, menu, "mk:furnace_output", 2, 2, O, false, true);
            }
            case BREWING_STAND -> {
                addRegion(regions, menu, "mk:brewing_potions", 0, 2, P, true, true);
                addRegion(regions, menu, "mk:brewing_ingredient", 3, 3, P, true, true);
                addRegion(regions, menu, "mk:brewing_fuel", 4, 4, P, true, true);
            }

            // ── Special ───────────────────────────────────────────────────
            case ANVIL -> {
                addRegion(regions, menu, "mk:anvil_input_1", 0, 0, T, true, true);
                addRegion(regions, menu, "mk:anvil_input_2", 1, 1, T, true, true);
                addRegion(regions, menu, "mk:anvil_output", 2, 2, O, false, true);
            }
            case ENCHANTING_TABLE -> {
                addRegion(regions, menu, "mk:enchanting_item", 0, 0, T, true, true);
                addRegion(regions, menu, "mk:enchanting_lapis", 1, 1, T, true, true);
            }
            case BEACON ->
                addRegion(regions, menu, "mk:beacon_payment", 0, 0, T, true, true);
            case VILLAGER_TRADING -> {
                addRegion(regions, menu, "mk:merchant_input_1", 0, 0, T, true, true);
                addRegion(regions, menu, "mk:merchant_input_2", 1, 1, T, true, true);
                addRegion(regions, menu, "mk:merchant_output", 2, 2, O, false, true);
            }
            case HORSE_INVENTORY -> {
                addRegion(regions, menu, "mk:horse_saddle", 0, 0, P, true, true);
                addRegion(regions, menu, "mk:horse_armor", 1, 1, P, true, true);
                int vanillaSlots = countNonMKSlots(menu);
                int chestSlots = vanillaSlots - 38;
                if (chestSlots > 0)
                    addRegion(regions, menu, "mk:horse_chest", 2, 1 + chestSlots, P, true, true);
            }
            case LECTERN ->
                addRegion(regions, menu, "mk:lectern_book", 0, 0, O, false, false);

            default -> {
                // Unknown context — no context-specific regions
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Creates an MKRegion from a menu slot range and adds it to the list.
     * The Container reference is extracted from the actual Slot at the
     * start position.
     */
    private static void addRegion(List<MKRegion> regions,
                                   AbstractContainerMenu menu,
                                   String name,
                                   int menuSlotStart, int menuSlotEnd,
                                   MKContainerDef.Persistence persistence,
                                   boolean shiftClickIn, boolean shiftClickOut) {
        if (menuSlotStart >= menu.slots.size()) return;

        Slot firstSlot = menu.slots.get(menuSlotStart);
        Container container = firstSlot.container;
        int containerStart = firstSlot.getContainerSlot();
        int size = menuSlotEnd - menuSlotStart + 1;

        MKRegion region = new MKRegion(name, container, containerStart, size,
                persistence, shiftClickIn, shiftClickOut);
        region.setMenuSlotRange(menuSlotStart, menuSlotEnd);
        regions.add(region);
    }

    /**
     * Gets the Container reference from a slot at a given menu position.
     * Returns null if the slot doesn't exist.
     */
    private static @Nullable Container getContainerForSlot(AbstractContainerMenu menu,
                                                             int menuSlotIndex) {
        if (menuSlotIndex < 0 || menuSlotIndex >= menu.slots.size()) return null;
        return menu.slots.get(menuSlotIndex).container;
    }

    /**
     * Counts non-MKSlot slots in a menu. MKSlots are appended after vanilla
     * slots, so we count until we hit one (or reach the end).
     */
    static int countNonMKSlots(AbstractContainerMenu menu) {
        int count = 0;
        for (int i = 0; i < menu.slots.size(); i++) {
            MKSlotState s = MKSlotStateRegistry.get(menu.slots.get(i));
            if (s != null && s.isMenuKitSlot()) break;
            count++;
        }
        return count;
    }

    /**
     * Returns all region names that exist in a given context (static lookup).
     * Useful for mod authors to discover available regions without a menu instance.
     */
    public static List<String> getRegionNames(MKContext context) {
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

        // Context-specific
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
