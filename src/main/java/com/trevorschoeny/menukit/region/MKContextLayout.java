package com.trevorschoeny.menukit.region;

import com.trevorschoeny.menukit.MenuKit;

import com.trevorschoeny.menukit.MKContext;
import com.trevorschoeny.menukit.container.MKContainerDef;
import com.trevorschoeny.menukit.container.MKContainerType;
import com.trevorschoeny.menukit.widget.MKSlot;
import com.trevorschoeny.menukit.widget.MKSlotState;
import com.trevorschoeny.menukit.widget.MKSlotStateRegistry;

import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Single source of truth for the slot layout of every vanilla menu type.
 *
 * <p>Both {@link MKContainerMapping} (which creates SlotGroups) and
 * {@link MKRegionRegistry} (which creates MKRegions) read their layout
 * data from here. This eliminates the duplicated 60+ entry switch statements
 * that previously existed in both classes.
 *
 * <p>Layouts are defined statically per {@link MKContext}. Some layouts have
 * dynamic slot ranges that depend on the menu instance at runtime (e.g., chest
 * size, horse chest slots, interaction-screen player inventory offsets). These
 * use sentinel values ({@code DYNAMIC}) and are resolved by
 * {@link #resolveForMenu(MKContext, AbstractContainerMenu)}.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKContextLayout {

    // ── Sentinel for "compute at runtime" ──────────────────────────────────
    /** Sentinel value indicating a slot index must be computed at runtime. */
    public static final int DYNAMIC = -1;

    // ── Persistence shortcuts ──────────────────────────────────────────────
    private static final MKContainerDef.Persistence P = MKContainerDef.Persistence.PERSISTENT;
    private static final MKContainerDef.Persistence T = MKContainerDef.Persistence.TRANSIENT;
    private static final MKContainerDef.Persistence O = MKContainerDef.Persistence.OUTPUT;

    // ── Container type shortcuts ────────────────────────────────────────────
    private static final MKContainerType S = MKContainerType.SIMPLE;
    private static final MKContainerType C = MKContainerType.CRAFTING;
    private static final MKContainerType X = MKContainerType.PROCESSING;  // X for "transform"
    private static final MKContainerType E = MKContainerType.EQUIPMENT;
    private static final MKContainerType H = MKContainerType.HOTBAR;

    // ── Layout Record ──────────────────────────────────────────────────────

    /**
     * Describes one slot group's layout within a menu.
     *
     * <p>Fields serve both consumers:
     * <ul>
     *   <li>{@code menuSlotStart}/{@code menuSlotEnd} — used by MKContainerMapping
     *       for SlotGroup construction and by MKRegionRegistry for setMenuSlotRange</li>
     *   <li>{@code containerStart}/{@code containerSize} — used by MKRegionRegistry
     *       when constructing MKRegion objects. Normally DYNAMIC (meaning "derive
     *       from the Slot at menuSlotStart"), but explicitly set for survival/creative
     *       player inventory where container indices differ from menu slot indices.</li>
     *   <li>{@code shiftClickIn}/{@code shiftClickOut} — used by MKRegionRegistry
     *       for shift-click behavior. Ignored by MKContainerMapping.</li>
     * </ul>
     */
    public record SlotLayout(
            String name,
            int menuSlotStart,
            int menuSlotEnd,
            MKContainerDef.Persistence persistence,
            boolean shiftClickIn,
            boolean shiftClickOut,
            int containerStart,   // DYNAMIC = derive from Slot at runtime
            int containerSize,    // DYNAMIC = derive from menu slot range
            MKContainerType containerType // functional classification
    ) {
        /** Convenience constructor — most layouts derive container indices at runtime.
         *  Container type defaults to SIMPLE. */
        public SlotLayout(String name, int menuSlotStart, int menuSlotEnd,
                          MKContainerDef.Persistence persistence,
                          boolean shiftClickIn, boolean shiftClickOut) {
            this(name, menuSlotStart, menuSlotEnd, persistence,
                    shiftClickIn, shiftClickOut, DYNAMIC, DYNAMIC, MKContainerType.SIMPLE);
        }

        /** Convenience constructor with explicit container type. */
        public SlotLayout(String name, int menuSlotStart, int menuSlotEnd,
                          MKContainerDef.Persistence persistence,
                          boolean shiftClickIn, boolean shiftClickOut,
                          MKContainerType containerType) {
            this(name, menuSlotStart, menuSlotEnd, persistence,
                    shiftClickIn, shiftClickOut, DYNAMIC, DYNAMIC, containerType);
        }

        /** Number of menu slots in this group. Only valid when indices are resolved. */
        public int menuSlotCount() {
            return menuSlotEnd - menuSlotStart + 1;
        }

        /** Whether this layout has explicit container indices (survival/creative player inv). */
        public boolean hasExplicitContainerIndices() {
            return containerStart != DYNAMIC;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  CONTEXT-SPECIFIC (NON-PLAYER) LAYOUTS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Returns the raw (possibly unresolved) slot layouts for a context's
     * non-player containers. Dynamic menus (chest, horse) will have DYNAMIC
     * indices that need resolution via a menu instance.
     */
    public static List<SlotLayout> getContextLayouts(MKContext context) {
        return switch (context) {
            // ── Personal crafting ─────────────────────────────────────────
            case SURVIVAL_INVENTORY -> List.of(
                    new SlotLayout("mk:craft_result", 0, 0, O, false, true, X),
                    new SlotLayout("mk:craft_2x2", 1, 4, T, true, true, C)
            );

            // ── Storage ───────────────────────────────────────────────────
            // Chest/ender chest/barrel: slot 0 to (vanillaSlots - 37), dynamic end
            case CHEST, ENDER_CHEST, BARREL ->
                    List.of(new SlotLayout("mk:chest", 0, DYNAMIC, P, true, true));
            case DOUBLE_CHEST ->
                    List.of(new SlotLayout("mk:chest", 0, DYNAMIC, P, true, true));
            case SHULKER_BOX ->
                    List.of(new SlotLayout("mk:shulker", 0, 26, P, true, true));
            case HOPPER ->
                    List.of(new SlotLayout("mk:hopper", 0, 4, P, true, true));
            case DISPENSER ->
                    List.of(new SlotLayout("mk:dispenser", 0, 8, P, true, true));

            // ── Crafting ──────────────────────────────────────────────────
            case CRAFTING_TABLE -> List.of(
                    new SlotLayout("mk:crafting_3x3_result", 0, 0, O, false, true, X),
                    new SlotLayout("mk:crafting_3x3", 1, 9, T, false, true, C)
            );
            case STONECUTTER -> List.of(
                    new SlotLayout("mk:stonecutter_input", 0, 0, T, true, true, X),
                    new SlotLayout("mk:stonecutter_output", 1, 1, O, false, true, X)
            );
            case SMITHING_TABLE -> List.of(
                    new SlotLayout("mk:smithing_template", 0, 0, T, true, true, X),
                    new SlotLayout("mk:smithing_base", 1, 1, T, true, true, X),
                    new SlotLayout("mk:smithing_addition", 2, 2, T, true, true, X),
                    new SlotLayout("mk:smithing_output", 3, 3, O, false, true, X)
            );
            case LOOM -> List.of(
                    new SlotLayout("mk:loom_banner", 0, 0, T, true, true, X),
                    new SlotLayout("mk:loom_dye", 1, 1, T, true, true, X),
                    new SlotLayout("mk:loom_pattern", 2, 2, T, true, true, X),
                    new SlotLayout("mk:loom_output", 3, 3, O, false, true, X)
            );
            case CARTOGRAPHY_TABLE -> List.of(
                    new SlotLayout("mk:cartography_map", 0, 0, T, true, true, X),
                    new SlotLayout("mk:cartography_material", 1, 1, T, true, true, X),
                    new SlotLayout("mk:cartography_output", 2, 2, O, false, true, X)
            );
            case GRINDSTONE -> List.of(
                    new SlotLayout("mk:grindstone_input_1", 0, 0, T, true, true, X),
                    new SlotLayout("mk:grindstone_input_2", 1, 1, T, true, true, X),
                    new SlotLayout("mk:grindstone_output", 2, 2, O, false, true, X)
            );
            case CRAFTER ->
                    List.of(new SlotLayout("mk:crafter", 0, 8, P, true, true, C));

            // ── Processing ────────────────────────────────────────────────
            case FURNACE, BLAST_FURNACE, SMOKER -> List.of(
                    new SlotLayout("mk:furnace_input", 0, 0, P, true, true, X),
                    new SlotLayout("mk:furnace_fuel", 1, 1, P, true, true, X),
                    new SlotLayout("mk:furnace_output", 2, 2, O, false, true, X)
            );
            case BREWING_STAND -> List.of(
                    new SlotLayout("mk:brewing_potions", 0, 2, P, true, true, X),
                    new SlotLayout("mk:brewing_ingredient", 3, 3, P, true, true, X),
                    new SlotLayout("mk:brewing_fuel", 4, 4, P, true, true, X)
            );

            // ── Special ───────────────────────────────────────────────────
            case ANVIL -> List.of(
                    new SlotLayout("mk:anvil_input_1", 0, 0, T, true, true, X),
                    new SlotLayout("mk:anvil_input_2", 1, 1, T, true, true, X),
                    new SlotLayout("mk:anvil_output", 2, 2, O, false, true, X)
            );
            case ENCHANTING_TABLE -> List.of(
                    new SlotLayout("mk:enchanting_item", 0, 0, T, true, true, X),
                    new SlotLayout("mk:enchanting_lapis", 1, 1, T, true, true, X)
            );
            case BEACON ->
                    List.of(new SlotLayout("mk:beacon_payment", 0, 0, T, true, true, X));
            case VILLAGER_TRADING -> List.of(
                    new SlotLayout("mk:merchant_input_1", 0, 0, T, true, true, X),
                    new SlotLayout("mk:merchant_input_2", 1, 1, T, true, true, X),
                    new SlotLayout("mk:merchant_output", 2, 2, O, false, true, X)
            );
            case HORSE_INVENTORY -> {
                // Horse: slots 0-1 are always saddle+armor.
                // Slots 2+ are chest inventory (dynamic, depends on entity).
                // The chest layout uses DYNAMIC end — resolved at runtime.
                List<SlotLayout> layouts = new ArrayList<>();
                layouts.add(new SlotLayout("mk:horse_saddle", 0, 0, P, true, true, E));
                layouts.add(new SlotLayout("mk:horse_armor", 1, 1, P, true, true, E));
                // mk:horse_chest added only if the entity has chest slots — dynamic
                layouts.add(new SlotLayout("mk:horse_chest", 2, DYNAMIC, P, true, true));
                yield Collections.unmodifiableList(layouts);
            }
            case LECTERN ->
                    // Lectern: shiftClickIn=false, shiftClickOut=false (can't put items in OR take via shift)
                    List.of(new SlotLayout("mk:lectern_book", 0, 0, O, false, false, X));

            // No context-specific containers for other contexts
            default -> List.of();
        };
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  PLAYER INVENTORY LAYOUTS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Returns the raw (possibly unresolved) player inventory slot layouts.
     *
     * <p>Survival/creative inventory has fixed positions with explicit container
     * indices (because container index != menu slot index for player inventory).
     * Interaction screens have player inventory at the END of the slot list,
     * requiring dynamic resolution.
     */
    public static List<SlotLayout> getPlayerInventoryLayouts(MKContext context) {
        return switch (context) {
            // ── Personal screens ──────────────────────────────────────────
            // InventoryMenu layout:
            //   Slot 5-8   = armor   (container indices 36-39)
            //   Slot 9-35  = main    (container indices 9-35)
            //   Slot 36-44 = hotbar  (container indices 0-8)
            //   Slot 45    = offhand (container index 40)
            case SURVIVAL_INVENTORY, CREATIVE_INVENTORY, CREATIVE_TABS -> List.of(
                    new SlotLayout("mk:armor", 5, 8, P, true, true, 36, 4, E),
                    new SlotLayout("mk:main_inventory", 9, 35, P, true, true, 9, 27, S),
                    new SlotLayout("mk:hotbar", 36, 44, P, true, true, 0, 9, H),
                    new SlotLayout("mk:offhand", 45, 45, P, true, true, 40, 1, E)
            );

            // ── Interaction screens ───────────────────────────────────────
            // All interaction menus place 27 main + 9 hotbar at the END
            // of the slot list. DYNAMIC indices resolved from vanilla slot count.
            default -> {
                if (MKContext.ALL_WITH_PLAYER_INVENTORY.contains(context)) {
                    yield List.of(
                            new SlotLayout("mk:main_inventory", DYNAMIC, DYNAMIC, P, true, true, 9, 27, S),
                            new SlotLayout("mk:hotbar", DYNAMIC, DYNAMIC, P, true, true, 0, 9, H)
                    );
                }
                yield List.of();
            }
        };
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  RESOLUTION — DYNAMIC LAYOUTS -> CONCRETE INDICES
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Resolves all layouts (context + player inventory) for a menu instance,
     * computing any DYNAMIC indices from the actual slot count.
     *
     * @param context the MKContext
     * @param menu    the live menu instance (used for slot count)
     * @return fully resolved SlotLayouts with concrete indices
     */
    public static List<SlotLayout> resolveForMenu(MKContext context,
                                                    AbstractContainerMenu menu) {
        List<SlotLayout> resolved = new ArrayList<>();

        // Count vanilla slots once — both resolvers need this, and it iterates
        // the full slot list each time. Cache it here to avoid redundant passes.
        int vanillaSlotCount = countNonMKSlots(menu);

        // Resolve context-specific (non-player) layouts
        resolveContextLayouts(resolved, context, vanillaSlotCount);

        // Resolve player inventory layouts
        resolvePlayerInventoryLayouts(resolved, context, menu, vanillaSlotCount);

        return resolved;
    }

    /**
     * Resolves only the context-specific (non-player) layouts for a menu.
     */
    private static void resolveContextLayouts(List<SlotLayout> out,
                                               MKContext context,
                                               int vanillaSlots) {
        for (SlotLayout raw : getContextLayouts(context)) {
            SlotLayout resolved = resolveContextLayout(raw, context, vanillaSlots);
            // null means the layout should be skipped (e.g., no horse chest)
            if (resolved != null) {
                out.add(resolved);
            }
        }
    }

    /**
     * Resolves a single context layout, computing DYNAMIC indices.
     * Returns null if the layout should be skipped (e.g., guard check fails).
     */
    private static SlotLayout resolveContextLayout(SlotLayout raw,
                                                    MKContext context,
                                                    int vanillaSlots) {
        // If no DYNAMIC indices, use as-is
        if (raw.menuSlotStart() != DYNAMIC && raw.menuSlotEnd() != DYNAMIC) {
            return raw;
        }

        return switch (context) {
            // Chest/ender chest/barrel/double chest: end = vanillaSlots - 37
            case CHEST, ENDER_CHEST, BARREL, DOUBLE_CHEST -> {
                int containerEnd = vanillaSlots - 37;
                if (containerEnd < 0) yield null; // guard: skip if invalid
                yield new SlotLayout(raw.name(), 0, containerEnd, raw.persistence(),
                        raw.shiftClickIn(), raw.shiftClickOut(), raw.containerType());
            }

            // Horse chest: slots 2..(1 + chestSlots), chestSlots = vanillaSlots - 38
            case HORSE_INVENTORY -> {
                if (!"mk:horse_chest".equals(raw.name())) yield raw;
                int chestSlots = vanillaSlots - 38;
                if (chestSlots <= 0) yield null; // no chest on this horse
                yield new SlotLayout(raw.name(), 2, 1 + chestSlots, raw.persistence(),
                        raw.shiftClickIn(), raw.shiftClickOut(), raw.containerType());
            }

            default -> raw;
        };
    }

    /**
     * Resolves player inventory layouts, computing DYNAMIC indices for
     * interaction screens where player inventory is at the end.
     */
    private static void resolvePlayerInventoryLayouts(List<SlotLayout> out,
                                                       MKContext context,
                                                       AbstractContainerMenu menu,
                                                       int vanillaSlotCount) {
        List<SlotLayout> rawLayouts = getPlayerInventoryLayouts(context);
        if (rawLayouts.isEmpty()) return;

        // Survival/creative: all indices are already concrete (with explicit container indices)
        if (MKContext.PERSONAL.contains(context)) {
            out.addAll(rawLayouts);
            return;
        }

        // Interaction screens: compute from pre-cached vanilla slot count
        int hotbarEnd = vanillaSlotCount - 1;
        int hotbarStart = hotbarEnd - 8;   // 9 hotbar slots
        int mainEnd = hotbarStart - 1;
        int mainStart = mainEnd - 26;      // 27 main inventory slots

        if (mainStart < 0 || hotbarEnd >= menu.slots.size()) return; // guard

        // Resolve each player inventory layout with computed menu slot indices.
        // Container indices (9,27 for main; 0,9 for hotbar) are already in the raw layouts.
        for (SlotLayout raw : rawLayouts) {
            if ("mk:main_inventory".equals(raw.name())) {
                out.add(new SlotLayout(raw.name(), mainStart, mainEnd, raw.persistence(),
                        raw.shiftClickIn(), raw.shiftClickOut(),
                        raw.containerStart(), raw.containerSize(), raw.containerType()));
            } else if ("mk:hotbar".equals(raw.name())) {
                out.add(new SlotLayout(raw.name(), hotbarStart, hotbarEnd, raw.persistence(),
                        raw.shiftClickIn(), raw.shiftClickOut(),
                        raw.containerStart(), raw.containerSize(), raw.containerType()));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  STATIC NAME LOOKUP
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Returns all container/region names that exist in a given context.
     * This is a static lookup — no menu instance needed. Variable-size
     * containers (horse chest) are excluded since they may not be present.
     *
     * <p>Replaces both {@code MKContainerMapping.getContainerNames} and
     * {@code MKRegionRegistry.getRegionNames} — they were identical.
     */
    public static List<String> getNames(MKContext context) {
        List<String> names = new ArrayList<>();

        // Player inventory names
        if (MKContext.ALL_WITH_PLAYER_INVENTORY.contains(context)) {
            names.add("mk:main_inventory");
            names.add("mk:hotbar");
        }
        if (MKContext.PERSONAL.contains(context)) {
            names.add("mk:armor");
            names.add("mk:offhand");
        }

        // Context-specific names — extract from the static layouts
        // Skip "mk:horse_chest" because it's conditional on the entity having a chest
        for (SlotLayout layout : getContextLayouts(context)) {
            if ("mk:horse_chest".equals(layout.name())) continue;
            names.add(layout.name());
        }

        return names;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  HOTBAR POSITION DATA
    // ═══════════════════════════════════════════════════════════════════════
    //
    // The hotbar's position within the container texture varies by screen.
    // These values come from vanilla's screen rendering code — each screen
    // type draws the player inventory section at a different X offset, and
    // the hotbar row is always the last row of the player inventory area.
    //
    // Why this matters: consumers (like PocketsPanel) need to place UI
    // relative to specific hotbar slots. Without this data living in MenuKit,
    // every consumer must hardcode per-screen offsets independently.

    /** Slot-to-slot spacing in the hotbar (and inventory grid). */
    public static final int SLOT_SPACING = 18;

    /** Number of slots in the hotbar. */
    public static final int HOTBAR_SLOTS = 9;

    /** Total pixel width of the 9-slot hotbar row: 9 * 18 = 162. */
    public static final int HOTBAR_WIDTH = HOTBAR_SLOTS * SLOT_SPACING;

    /**
     * Returns the X coordinate (container-relative) of the first hotbar slot
     * for the given context. This is the left edge of hotbar slot 0.
     *
     * <p>Values are sourced from vanilla screen rendering code:
     * <ul>
     *   <li>Standard 176px screens: x=8 (most interaction screens)</li>
     *   <li>Beacon (230px): x=36</li>
     *   <li>Villager trading (276px): x=108</li>
     *   <li>Creative (195px): x=9</li>
     * </ul>
     *
     * <p>Contexts without a player inventory hotbar (e.g., LECTERN) return 0.
     *
     * @param context the active screen context
     * @return the X pixel offset of hotbar slot 0 within the container image
     */
    public static int getHotbarX(MKContext context) {
        return switch (context) {
            // Wide interaction screens — player inventory is offset rightward
            case VILLAGER_TRADING -> 108;
            case BEACON          -> 36;
            // Creative screens use a 195px-wide container with x=9
            case CREATIVE_INVENTORY, CREATIVE_TABS -> 9;
            // All standard 176px screens (survival, chest, furnace, etc.)
            default -> 8;
        };
    }

    /**
     * Returns the Y coordinate (container-relative) of the hotbar row
     * for the given context. The hotbar is always the bottom-most row
     * of the player inventory section, 24px from the container bottom.
     *
     * @param context the active screen context
     * @return the Y pixel offset of the hotbar row within the container image
     */
    public static int getHotbarY(MKContext context) {
        // Vanilla rule: hotbar row is always 24px up from the bottom of the
        // container image. This holds for all screens with player inventory.
        return context.containerHeight() - 24;
    }

    /**
     * Returns the X coordinate of a specific hotbar slot (0-8) in the given context.
     *
     * @param context      the active screen context
     * @param hotbarIndex  the hotbar slot index (0 = leftmost, 8 = rightmost)
     * @return the X pixel offset of the specified slot within the container image
     */
    public static int getHotbarSlotX(MKContext context, int hotbarIndex) {
        return getHotbarX(context) + hotbarIndex * SLOT_SPACING;
    }

    /**
     * Returns the center X coordinate of a specific hotbar slot (0-8).
     * Useful for centering panels or buttons above/below a slot.
     *
     * @param context      the active screen context
     * @param hotbarIndex  the hotbar slot index (0-8)
     * @return the center X pixel of the specified slot
     */
    public static int getHotbarSlotCenterX(MKContext context, int hotbarIndex) {
        // Each slot is 18px wide, so center is at slotX + 9
        return getHotbarSlotX(context, hotbarIndex) + SLOT_SPACING / 2;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  SHARED HELPER
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Counts non-MKSlot (i.e., vanilla) slots in a menu. MKSlots are always
     * appended after vanilla slots, so we count from the start until we hit
     * one (or reach the end).
     *
     * <p>Shared by both {@link MKContainerMapping} and {@link MKRegionRegistry}.
     * Previously duplicated as {@code countVanillaSlots} / {@code countNonMKSlots}.
     */
    public static int countNonMKSlots(AbstractContainerMenu menu) {
        int count = 0;
        for (int i = 0; i < menu.slots.size(); i++) {
            MKSlotState s = MKSlotStateRegistry.get(menu.slots.get(i));
            if (s != null && s.isMenuKitSlot()) break;
            count++;
        }
        return count;
    }
}
