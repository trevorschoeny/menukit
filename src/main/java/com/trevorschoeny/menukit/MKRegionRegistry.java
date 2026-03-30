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

    // ── Region group definitions (registered at mod init) ─────────────────
    // Key: group name (e.g., "player_storage"). Immutable after startup.
    private static final Map<String, MKRegionGroupDef> groupDefs = new LinkedHashMap<>();

    // ── Live resolved groups per menu instance ────────────────────────────
    // Key: System.identityHashCode(menu). Resolved alongside regions.
    private static final Map<Integer, List<MKRegionGroup>> menuGroups = new HashMap<>();

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

    /**
     * Registers a region group definition. Called at mod init by
     * {@link RegionGroupBuilder#register()} via {@link MenuKit#registerRegionGroup}.
     * The actual MKRegionGroup is created at menu construction time.
     */
    public static void registerGroupDef(MKRegionGroupDef def) {
        groupDefs.put(def.name(), def);
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
     * {@link MKEvent.Type#REGION_POPULATED} event for each region
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
                        MKEvent.Type.REGION_POPULATED, context, region, player);
                MKEventBus.fire(event);
            }
        }

        // ── Resolve region groups for this menu ──────────────────────────
        // Groups reference regions by name — match each group def's members
        // against the actual resolved regions for this menu instance.
        resolveGroupsForMenu(menu, regions);

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

    // ── Dynamic Region Registration ─────────────────────────────────────

    /**
     * Registers a region for a dynamically-activated container (e.g., peek).
     *
     * <p>Called when a panel with container-backed slots becomes visible AFTER
     * initial {@link #resolveForMenu} has already run. Scans the menu's slot
     * list to find all slots backed by the given Container, determines their
     * menu slot range, creates an MKRegion, adds it to the menu's region cache,
     * and updates any region groups that reference this region name.
     *
     * <p>No-op if a region with the given name already exists for this menu.
     *
     * @param menu      the live menu instance
     * @param name      the region name (typically the container name, e.g., "peek")
     * @param container the Container backing the slots (MKContainer's delegate)
     * @param size      the container's total slot count
     * @param persistence the container's persistence type
     * @param shiftClickIn  whether shift-click INTO this region is allowed
     * @param shiftClickOut whether shift-click OUT OF this region is allowed
     */
    public static void registerDynamicRegion(AbstractContainerMenu menu,
                                              String name,
                                              Container container,
                                              int size,
                                              MKContainerDef.Persistence persistence,
                                              boolean shiftClickIn,
                                              boolean shiftClickOut) {
        // Skip if a region with this name already exists for this menu
        if (getRegion(menu, name) != null) return;

        // Scan the menu's slot list to find the first and last slots backed
        // by this Container. MKSlots for a container are added contiguously
        // by createSlotsForMenu(), so first/last gives us the full range.
        int firstMenuSlot = -1;
        int lastMenuSlot = -1;
        int containerStart = -1;

        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            // Match by identity — either the slot uses the container directly,
            // or it uses an MKContainer whose delegate IS the container.
            // (MKSlots use MKContainer as their container field, but regions
            // are registered with the MKContainer's delegate SimpleContainer.)
            boolean match = slot.container == container
                    || (slot.container instanceof MKContainer mkc
                        && mkc.getDelegate() == container);
            if (match) {
                if (firstMenuSlot < 0) {
                    firstMenuSlot = i;
                    containerStart = slot.getContainerSlot();
                }
                lastMenuSlot = i;
            }
        }

        // No matching slots found — container not yet in this menu
        if (firstMenuSlot < 0) return;

        // Look up the container def's type if registered, otherwise default to SIMPLE.
        // This ensures dynamic regions (like peek) inherit the type declared at registration.
        MKContainerType type = MKContainerType.SIMPLE;
        MKContainerDef def = MenuKit.getContainerDef(name);
        if (def != null) {
            type = def.containerType();
        }

        // Create the region with the discovered slot range
        MKRegion region = new MKRegion(
                name, container, containerStart, size,
                persistence, shiftClickIn, shiftClickOut, type
        );
        region.setMenuSlotRange(firstMenuSlot, lastMenuSlot);

        // Add to the menu's cached region list (which is a mutable ArrayList)
        int key = System.identityHashCode(menu);
        List<MKRegion> regions = menuRegions.get(key);
        if (regions == null) {
            regions = new ArrayList<>();
            menuRegions.put(key, regions);
        }
        regions.add(region);

        // Update any region groups that reference this region name.
        // Groups are already resolved — check each group def to see if it
        // lists this region as a member, and if so, add the region to the
        // live group (or create a new group if one didn't exist before).
        updateGroupsForDynamicRegion(menu, region);

        // Fire REGION_POPULATED for the new dynamic region so listeners
        // (e.g., dynamic sort button creation) can react to it. We need
        // a Player reference to resolve the context — the caller can use
        // the overload that accepts a Player + MKContext if available.
    }

    /**
     * Overload of {@link #registerDynamicRegion} that also fires a
     * {@link MKEvent.Type#REGION_POPULATED} event after the region is created.
     *
     * <p>Use this when the calling code has a Player and MKContext available
     * (e.g., from {@link MenuKit#showPanel}). The event lets listeners react
     * to dynamic container activation (e.g., creating sort buttons for peek).
     */
    public static void registerDynamicRegion(AbstractContainerMenu menu,
                                              String name,
                                              Container container,
                                              int size,
                                              MKContainerDef.Persistence persistence,
                                              boolean shiftClickIn,
                                              boolean shiftClickOut,
                                              @Nullable Player player,
                                              @Nullable MKContext context) {
        // Delegate to the base method for actual registration
        boolean existed = getRegion(menu, name) != null;
        registerDynamicRegion(menu, name, container, size, persistence,
                shiftClickIn, shiftClickOut);

        // Fire REGION_POPULATED if the region was actually created (didn't
        // exist before) and we have enough context for the event
        if (!existed && player != null && context != null) {
            MKRegion region = getRegion(menu, name);
            if (region != null) {
                MKSlotEvent event = MKSlotEvent.lifecycleWithRegion(
                        MKEvent.Type.REGION_POPULATED, context, region, player);
                MKEventBus.fire(event);

                // Re-evaluate conditional rules for the new region
                MenuKit.onRegionPopulated(menu);
            }
        }
    }

    /**
     * Updates resolved region groups to include a newly-added dynamic region.
     * Checks all registered group definitions — if any group references this
     * region by name, the region is added to that group's live member list.
     */
    private static void updateGroupsForDynamicRegion(AbstractContainerMenu menu,
                                                      MKRegion region) {
        int key = System.identityHashCode(menu);
        List<MKRegionGroup> groups = menuGroups.get(key);

        for (MKRegionGroupDef def : groupDefs.values()) {
            // Check if this group definition includes the new region's name
            boolean isMember = false;
            for (MKRegionGroupDef.MemberDef member : def.members()) {
                if (member.regionName().equals(region.name())) {
                    isMember = true;
                    break;
                }
            }
            if (!isMember) continue;

            // Find or create the live group for this menu
            MKRegionGroup existingGroup = null;
            if (groups != null) {
                for (MKRegionGroup g : groups) {
                    if (g.name().equals(def.name())) {
                        existingGroup = g;
                        break;
                    }
                }
            }

            if (existingGroup != null) {
                // Add the region to the existing group's member list
                existingGroup.addRegion(region);
            } else {
                // Create a new group with just this region
                List<MKRegion> groupRegions = new ArrayList<>();
                groupRegions.add(region);
                MKRegionGroup newGroup = new MKRegionGroup(def.name(), groupRegions);
                if (groups == null) {
                    groups = new ArrayList<>();
                    menuGroups.put(key, groups);
                }
                groups.add(newGroup);
            }
        }
    }

    // ── Dynamic Region Removal ──────────────────────────────────────────

    /**
     * Removes a dynamically-registered region from a menu's region cache
     * and any groups that contain it. Used when a dynamic container (e.g.,
     * peek) is deactivated and its region should no longer participate in
     * sorting, move-matching, or other region-based operations.
     *
     * <p>No-op if no region with the given name exists for this menu.
     *
     * @param menu the live menu instance
     * @param name the region name to remove (e.g., "peek")
     */
    public static void removeDynamicRegion(AbstractContainerMenu menu, String name) {
        int key = System.identityHashCode(menu);

        // Remove from the region list
        List<MKRegion> regions = menuRegions.get(key);
        if (regions != null) {
            regions.removeIf(r -> r.name().equals(name));
        }

        // Remove from any groups that contain this region
        List<MKRegionGroup> groups = menuGroups.get(key);
        if (groups != null) {
            for (MKRegionGroup g : groups) {
                g.removeRegion(name);
            }
        }
    }

    // ── Direct Region Management ─────────────────────────────────────────

    /**
     * Adds a pre-built region to a menu's region cache. Used when regions
     * can't be created via resolveForMenu or registerDynamicRegion (e.g.,
     * creative mode where vanilla slots share a container and slot indices
     * differ from the inventoryMenu layout).
     */
    public static void addRegion(AbstractContainerMenu menu, MKRegion region) {
        int key = System.identityHashCode(menu);
        List<MKRegion> regions = menuRegions.get(key);
        if (regions == null) {
            regions = new ArrayList<>();
            menuRegions.put(key, regions);
        }
        // Remove existing region with the same name (replace)
        regions.removeIf(r -> r.name().equals(region.name()));
        regions.add(region);

        // Update groups that reference this region
        updateGroupsForDynamicRegion(menu, region);
    }

    /**
     * Clears all regions and groups for a menu. Used before re-resolving
     * regions on creative tab switch so stale data from a previous tab
     * doesn't linger.
     */
    public static void clearRegions(AbstractContainerMenu menu) {
        int key = System.identityHashCode(menu);
        menuRegions.remove(key);
        menuGroups.remove(key);
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

    // ── Group Lookups ────────────────────────────────────────────────────

    /** Returns a resolved group by name for a menu, or null. */
    public static @Nullable MKRegionGroup getGroup(AbstractContainerMenu menu, String name) {
        List<MKRegionGroup> groups = menuGroups.getOrDefault(
                System.identityHashCode(menu), List.of());
        for (MKRegionGroup g : groups) {
            if (g.name().equals(name)) return g;
        }
        return null;
    }

    /** Returns all resolved groups for a menu, or empty list. */
    public static List<MKRegionGroup> getGroups(AbstractContainerMenu menu) {
        return menuGroups.getOrDefault(System.identityHashCode(menu), List.of());
    }

    /** Returns all groups that contain a given region name. */
    public static List<MKRegionGroup> getGroupsForRegion(AbstractContainerMenu menu,
                                                           String regionName) {
        List<MKRegionGroup> result = new ArrayList<>();
        for (MKRegionGroup g : getGroups(menu)) {
            if (g.contains(regionName)) result.add(g);
        }
        return result;
    }

    /** Finds the first group that contains a given menu slot index, or null. */
    public static @Nullable MKRegionGroup getGroupForSlot(AbstractContainerMenu menu,
                                                            int slotIndex) {
        for (MKRegionGroup g : getGroups(menu)) {
            if (g.containsMenuSlot(slotIndex)) return g;
        }
        return null;
    }

    /** Cleans up all region and group data for a closed menu. */
    public static void cleanup(AbstractContainerMenu menu) {
        int key = System.identityHashCode(menu);
        menuRegions.remove(key);
        menuGroups.remove(key);
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
                    layout.shiftClickIn(), layout.shiftClickOut(),
                    layout.containerType()
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
                layout.shiftClickIn(), layout.shiftClickOut(),
                layout.containerType()
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

    // ── Group Resolution ──────────────────────────────────────────────────

    /**
     * Resolves all registered group definitions against the actual regions
     * for a menu instance. Groups whose members don't exist in this menu
     * (e.g., armor group in a chest menu) are created with only the
     * members that are present — empty groups are skipped entirely.
     *
     * <p>Within each group, regions are sorted by their member priority
     * (ascending — lowest priority value first = fill first).
     */
    private static void resolveGroupsForMenu(AbstractContainerMenu menu,
                                              List<MKRegion> regions) {
        // Build a name -> region lookup for fast matching
        Map<String, MKRegion> regionsByName = new HashMap<>();
        for (MKRegion r : regions) {
            regionsByName.put(r.name(), r);
        }

        List<MKRegionGroup> resolved = new ArrayList<>();

        for (MKRegionGroupDef def : groupDefs.values()) {
            // Collect members that actually exist in this menu, sorted by priority
            List<MKRegionGroupDef.MemberDef> sortedMembers = new ArrayList<>(def.members());
            sortedMembers.sort(Comparator.comparingInt(MKRegionGroupDef.MemberDef::priority));

            List<MKRegion> groupRegions = new ArrayList<>();
            for (MKRegionGroupDef.MemberDef member : sortedMembers) {
                MKRegion region = regionsByName.get(member.regionName());
                if (region != null) {
                    groupRegions.add(region);
                }
            }

            // Only create the group if at least one member region exists
            if (!groupRegions.isEmpty()) {
                resolved.add(new MKRegionGroup(def.name(), groupRegions));
            }
        }

        menuGroups.put(System.identityHashCode(menu), resolved);
    }
}
