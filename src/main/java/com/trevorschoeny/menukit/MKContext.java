package com.trevorschoeny.menukit;

import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.world.inventory.*;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * Defines WHERE a MenuKit panel appears. Each context represents a specific
 * vanilla GUI screen — survival inventory, creative inventory, chest, furnace, etc.
 *
 * <p>Users list contexts in {@link MKPanel.Builder#showIn} to control panel visibility:
 * <pre>{@code
 * MKPanel.builder("my_panel")
 *     .showIn(MKContext.SURVIVAL_INVENTORY, MKContext.CREATIVE_INVENTORY)
 *     .posRight(4, 4)
 *     .build();
 * }</pre>
 *
 * <p>Shortcut groups (e.g. {@link #ALL_INVENTORIES}, {@link #ALL_STORAGE}) let you
 * target multiple related contexts at once.
 *
 * <p>Part of the <b>MenuKit</b> API.
 */
public enum MKContext {

    // ── Inventory ────────────────────────────────────────────────────────────

    /** Survival inventory screen (InventoryScreen). */
    SURVIVAL_INVENTORY(InventoryMenu.class, 176, 166, false),

    /** Creative inventory tab (CreativeModeInventoryScreen, inventory tab only). */
    CREATIVE_INVENTORY(InventoryMenu.class, 195, 136, true),

    /** Creative item grid tabs — Building Blocks, Search, Hotbar, etc. (NOT inventory tab). */
    CREATIVE_TABS(InventoryMenu.class, 195, 136, true),

    // ── Storage ──────────────────────────────────────────────────────────────

    /** Single chest (3 rows). */
    CHEST(ChestMenu.class, 176, 166, false),

    /** Double chest (6 rows). */
    DOUBLE_CHEST(ChestMenu.class, 176, 222, false),

    /** Ender chest. */
    ENDER_CHEST(ChestMenu.class, 176, 166, false),

    /** Barrel (same layout as single chest). */
    BARREL(ChestMenu.class, 176, 166, false),

    /** Shulker box. */
    SHULKER_BOX(ShulkerBoxMenu.class, 176, 166, false),

    /** Hopper. */
    HOPPER(HopperMenu.class, 176, 133, false),

    /** Dispenser and dropper. */
    DISPENSER(DispenserMenu.class, 176, 166, false),

    // ── Crafting ─────────────────────────────────────────────────────────────

    /** Crafting table. */
    CRAFTING_TABLE(CraftingMenu.class, 176, 166, false),

    /** Stonecutter. */
    STONECUTTER(StonecutterMenu.class, 176, 166, false),

    /** Smithing table. */
    SMITHING_TABLE(SmithingMenu.class, 176, 166, false),

    /** Loom. */
    LOOM(LoomMenu.class, 176, 166, false),

    /** Cartography table. */
    CARTOGRAPHY_TABLE(CartographyTableMenu.class, 176, 166, false),

    /** Grindstone. */
    GRINDSTONE(GrindstoneMenu.class, 176, 166, false),

    /** Crafter (1.21+). */
    CRAFTER(CrafterMenu.class, 176, 166, false),

    // ── Processing ───────────────────────────────────────────────────────────

    /** Furnace. */
    FURNACE(FurnaceMenu.class, 176, 166, false),

    /** Blast furnace. */
    BLAST_FURNACE(BlastFurnaceMenu.class, 176, 166, false),

    /** Smoker. */
    SMOKER(SmokerMenu.class, 176, 166, false),

    /** Brewing stand. */
    BREWING_STAND(BrewingStandMenu.class, 176, 166, false),

    // ── Special ──────────────────────────────────────────────────────────────

    /** Anvil. */
    ANVIL(AnvilMenu.class, 176, 166, false),

    /** Enchanting table. */
    ENCHANTING_TABLE(EnchantmentMenu.class, 176, 166, false),

    /** Beacon. */
    BEACON(BeaconMenu.class, 230, 219, false),

    /** Villager/wandering trader trading screen. */
    VILLAGER_TRADING(MerchantMenu.class, 276, 166, false),

    /** Horse/donkey/llama/camel inventory. */
    HORSE_INVENTORY(HorseInventoryMenu.class, 176, 166, false),

    /** Lectern (book reading). */
    LECTERN(LecternMenu.class, 176, 166, false);

    // ── Fields ───────────────────────────────────────────────────────────────

    private final Class<? extends AbstractContainerMenu> menuClass;
    private final int containerWidth;
    private final int containerHeight;
    private final boolean creative;

    MKContext(Class<? extends AbstractContainerMenu> menuClass,
              int containerWidth, int containerHeight, boolean creative) {
        this.menuClass = menuClass;
        this.containerWidth = containerWidth;
        this.containerHeight = containerHeight;
        this.creative = creative;
    }

    /** The vanilla menu class associated with this context. */
    public Class<? extends AbstractContainerMenu> menuClass() { return menuClass; }

    /** Container image width for this context. */
    public int containerWidth() { return containerWidth; }

    /** Container image height for this context. */
    public int containerHeight() { return containerHeight; }

    /** Whether this is a creative mode context. */
    public boolean isCreative() { return creative; }

    // ── Shortcut Groups ──────────────────────────────────────────────────────

    /** Survival + creative inventory (not item grid tabs). */
    public static final Set<MKContext> ALL_INVENTORIES = EnumSet.of(
            SURVIVAL_INVENTORY, CREATIVE_INVENTORY);

    /** All storage containers. */
    public static final Set<MKContext> ALL_STORAGE = EnumSet.of(
            CHEST, DOUBLE_CHEST, ENDER_CHEST, BARREL, SHULKER_BOX, HOPPER, DISPENSER);

    /** All crafting-type tables. */
    public static final Set<MKContext> ALL_CRAFTING = EnumSet.of(
            CRAFTING_TABLE, STONECUTTER, SMITHING_TABLE, LOOM,
            CARTOGRAPHY_TABLE, GRINDSTONE, CRAFTER);

    /** All processing blocks (furnaces + brewing). */
    public static final Set<MKContext> ALL_PROCESSING = EnumSet.of(
            FURNACE, BLAST_FURNACE, SMOKER, BREWING_STAND);

    /** All contexts where the player's inventory is visible at the bottom. */
    public static final Set<MKContext> ALL_WITH_PLAYER_INVENTORY = EnumSet.of(
            SURVIVAL_INVENTORY, CREATIVE_INVENTORY,
            CHEST, DOUBLE_CHEST, ENDER_CHEST, BARREL, SHULKER_BOX, HOPPER, DISPENSER,
            CRAFTING_TABLE, STONECUTTER, SMITHING_TABLE, LOOM,
            CARTOGRAPHY_TABLE, GRINDSTONE, CRAFTER,
            FURNACE, BLAST_FURNACE, SMOKER, BREWING_STAND,
            ANVIL, ENCHANTING_TABLE, VILLAGER_TRADING, HORSE_INVENTORY);

    /** Every context. */
    public static final Set<MKContext> ALL = EnumSet.allOf(MKContext.class);

    // ── Menu Class Resolution ─────────────────────────────────────────────────

    /**
     * Returns a default MKContext for the given menu class.
     * Used during menu construction when the exact context (e.g., CHEST vs DOUBLE_CHEST)
     * isn't known yet. Returns the first matching context.
     *
     * @param menuClass the menu's class
     * @return a context matching that menu class, or null if none
     */
    public static @Nullable MKContext defaultForMenuClass(Class<?> menuClass) {
        for (MKContext ctx : values()) {
            if (ctx.menuClass() == menuClass) return ctx;
        }
        return null;
    }

    // ── Screen Resolution ────────────────────────────────────────────────────

    /**
     * Determines the active MKContext from a container screen instance.
     * Returns null if the screen type is not recognized.
     *
     * <p>This is the single place where screen-to-context mapping happens,
     * replacing scattered menuClass + isCreative logic throughout the codebase.
     *
     * @param screen the current container screen
     * @return the matching MKContext, or null if not recognized
     */
    public static @Nullable MKContext fromScreen(AbstractContainerScreen<?> screen) {
        AbstractContainerMenu menu = screen.getMenu();

        // ── Creative mode (must check before InventoryMenu) ──────────────
        if (screen instanceof CreativeModeInventoryScreen creativeScreen) {
            return creativeScreen.isInventoryOpen() ? CREATIVE_INVENTORY : CREATIVE_TABS;
        }

        // ── Survival inventory ───────────────────────────────────────────
        if (screen instanceof InventoryScreen) {
            return SURVIVAL_INVENTORY;
        }

        // ── Storage ──────────────────────────────────────────────────────
        if (menu instanceof ChestMenu chestMenu) {
            int rows = chestMenu.getRowCount();
            // TODO: distinguish ENDER_CHEST and BARREL from regular CHEST
            // (would need block entity type inspection — for now treat as CHEST/DOUBLE_CHEST)
            return rows > 3 ? DOUBLE_CHEST : CHEST;
        }
        if (menu instanceof ShulkerBoxMenu) return SHULKER_BOX;
        if (menu instanceof HopperMenu) return HOPPER;
        if (menu instanceof DispenserMenu) return DISPENSER;

        // ── Crafting ─────────────────────────────────────────────────────
        if (menu instanceof CraftingMenu) return CRAFTING_TABLE;
        if (menu instanceof StonecutterMenu) return STONECUTTER;
        if (menu instanceof SmithingMenu) return SMITHING_TABLE;
        if (menu instanceof LoomMenu) return LOOM;
        if (menu instanceof CartographyTableMenu) return CARTOGRAPHY_TABLE;
        if (menu instanceof GrindstoneMenu) return GRINDSTONE;
        if (menu instanceof CrafterMenu) return CRAFTER;

        // ── Processing ───────────────────────────────────────────────────
        if (menu instanceof FurnaceMenu) return FURNACE;
        if (menu instanceof BlastFurnaceMenu) return BLAST_FURNACE;
        if (menu instanceof SmokerMenu) return SMOKER;
        if (menu instanceof BrewingStandMenu) return BREWING_STAND;

        // ── Special ──────────────────────────────────────────────────────
        if (menu instanceof AnvilMenu) return ANVIL;
        if (menu instanceof EnchantmentMenu) return ENCHANTING_TABLE;
        if (menu instanceof BeaconMenu) return BEACON;
        if (menu instanceof MerchantMenu) return VILLAGER_TRADING;
        if (menu instanceof HorseInventoryMenu) return HORSE_INVENTORY;
        if (menu instanceof LecternMenu) return LECTERN;

        // ── MenuKit standalone screens ───────────────────────────────────
        if (menu instanceof MKMenu) return null; // standalone screens don't use contexts

        return null;
    }
}
