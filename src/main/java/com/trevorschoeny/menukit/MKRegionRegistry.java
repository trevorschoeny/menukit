package com.trevorschoeny.menukit;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
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
 * <p>Layout data is defined in {@link MKContextLayout} (shared with
 * {@link MKContainerMapping}) and converted to {@link MKRegion}s here,
 * adding runtime Container references from the actual menu slots.
 *
 * <p>Regions are resolved once per menu construction and cached. They are
 * cleaned up when the menu is removed.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKRegionRegistry {

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
     * <p>After all regions are resolved, fires a
     * {@link MKSlotEvent.Type#REGION_POPULATED} event for each region
     * through the {@link MKEventBus}. This lets consumers react to
     * region resolution (e.g., "the chest region has 27 slots").
     *
     * @param menu    the menu instance
     * @param context the MKContext (determines which vanilla regions exist)
     * @param player  the player this menu belongs to (for event firing)
     * @return the resolved regions (also cached internally)
     */
    public static List<MKRegion> resolveForMenu(AbstractContainerMenu menu,
                                                  @Nullable MKContext context,
                                                  @Nullable Player player) {
        List<MKRegion> regions = new ArrayList<>();
        if (context == null) {
            menuRegions.put(System.identityHashCode(menu), regions);
            return regions;
        }

        // Resolve all layouts from the shared source of truth
        List<MKContextLayout.SlotLayout> layouts = MKContextLayout.resolveForMenu(context, menu);

        // Convert each resolved layout into an MKRegion, adding the runtime
        // Container reference from the actual Slot objects in the menu.
        for (MKContextLayout.SlotLayout layout : layouts) {
            MKRegion region = createRegionFromLayout(menu, layout);
            if (region != null) {
                regions.add(region);
            }
        }

        // Cache by menu identity
        menuRegions.put(System.identityHashCode(menu), regions);

        // ── Fire REGION_POPULATED for each resolved region ──────────────
        // One event per region, carrying the region reference so consumers
        // can inspect which region was populated, its size, its container, etc.
        // Player may be null during very early construction — skip events if so.
        if (player != null) {
            for (MKRegion region : regions) {
                MKSlotEvent event = MKSlotEvent.lifecycleWithRegion(
                        MKSlotEvent.Type.REGION_POPULATED, context, region, player);
                MKEventBus.fire(event);
            }
        }

        return regions;
    }

    /**
     * Backward-compatible overload — resolves regions without firing events.
     * Prefer {@link #resolveForMenu(AbstractContainerMenu, MKContext, Player)}
     * when a Player reference is available.
     */
    public static List<MKRegion> resolveForMenu(AbstractContainerMenu menu,
                                                  @Nullable MKContext context) {
        return resolveForMenu(menu, context, null);
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

    // ── Layout -> MKRegion conversion ────────────────────────────────────

    /**
     * Creates an MKRegion from a resolved SlotLayout, extracting the
     * Container reference from the menu's actual Slot objects.
     *
     * <p>Two paths:
     * <ul>
     *   <li><b>Explicit container indices</b> (survival/creative player inventory):
     *       The layout carries hardcoded containerStart/containerSize because
     *       container indices differ from menu slot indices in InventoryMenu.
     *       The Container reference comes from a representative slot.</li>
     *   <li><b>Derived container indices</b> (everything else):
     *       containerStart and size are derived from the Slot at menuSlotStart,
     *       which is what vanilla menus expect.</li>
     * </ul>
     *
     * @return the MKRegion, or null if the slot is out of bounds
     */
    private static @Nullable MKRegion createRegionFromLayout(
            AbstractContainerMenu menu, MKContextLayout.SlotLayout layout) {

        int menuSlotStart = layout.menuSlotStart();
        int menuSlotEnd = layout.menuSlotEnd();

        // Guard: menu slot must exist
        if (menuSlotStart < 0 || menuSlotStart >= menu.slots.size()) return null;

        if (layout.hasExplicitContainerIndices()) {
            // Survival/creative player inventory: container indices are explicit
            // because they don't match menu slot indices in InventoryMenu.
            // Use the Container from a representative slot (the first slot in range).
            Container container = getContainerForSlot(menu, menuSlotStart);
            if (container == null) return null;

            MKRegion region = new MKRegion(
                    layout.name(), container,
                    layout.containerStart(), layout.containerSize(),
                    layout.persistence(),
                    layout.shiftClickIn(), layout.shiftClickOut()
            );
            region.setMenuSlotRange(menuSlotStart, menuSlotEnd);
            return region;
        }

        // Standard path: derive container start + size from the actual Slot
        Slot firstSlot = menu.slots.get(menuSlotStart);
        Container container = firstSlot.container;
        int containerStart = firstSlot.getContainerSlot();
        int size = menuSlotEnd - menuSlotStart + 1;

        MKRegion region = new MKRegion(
                layout.name(), container, containerStart, size,
                layout.persistence(),
                layout.shiftClickIn(), layout.shiftClickOut()
        );
        region.setMenuSlotRange(menuSlotStart, menuSlotEnd);
        return region;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

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
     * Counts non-MKSlot (i.e., vanilla) slots in a menu.
     * Delegates to {@link MKContextLayout#countNonMKSlots(AbstractContainerMenu)}.
     */
    static int countNonMKSlots(AbstractContainerMenu menu) {
        return MKContextLayout.countNonMKSlots(menu);
    }

    /**
     * Returns all region names that exist in a given context (static lookup).
     * Useful for mod authors to discover available regions without a menu instance.
     *
     * <p>Delegates to {@link MKContextLayout#getNames(MKContext)}.
     */
    public static List<String> getRegionNames(MKContext context) {
        return MKContextLayout.getNames(context);
    }
}
