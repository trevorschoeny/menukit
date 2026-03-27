package com.trevorschoeny.menukit;

/**
 * Classifies containers by their functional role in the UI. Used to
 * determine which features apply to a container (e.g., only SIMPLE
 * containers get sort and move-matching buttons).
 *
 * <p>Every vanilla region is classified via {@link MKContextLayout},
 * and custom containers default to SIMPLE unless explicitly set via
 * {@link MKContainerDef.Builder#type(MKContainerType)}.
 *
 * <p>Part of the <b>MenuKit</b> API.
 */
public enum MKContainerType {

    /** Generic storage — chests, shulkers, barrels, player main inventory,
     *  custom storage containers. Sortable and eligible for move-matching. */
    SIMPLE,

    /** Crafting grids — 2x2, 3x3. Items are arranged by recipe, not sorted. */
    CRAFTING,

    /** Processing I/O — furnace inputs/outputs, stonecutter, smithing,
     *  brewing, anvil, enchanting, loom, cartography, grindstone, merchant. */
    PROCESSING,

    /** Equipment slots — armor, offhand, equipment panels. */
    EQUIPMENT,

    /** Hotbar — the 9-slot action bar. Sorted separately or not at all. */
    HOTBAR,

    /** Anything that doesn't fit the above categories. */
    OTHER
}
