package com.trevorschoeny.menukit;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Unified slot translation layer over ALL vanilla containers.
 *
 * <p>Every slot in every vanilla menu maps to a named MK container and a
 * position within that container. This class provides the translation in
 * both directions, and gives mod authors a consistent API to access items
 * regardless of which screen is open.
 *
 * <p><b>Unified inventory positions</b> for the player's own inventory
 * are always consistent:
 * <ul>
 *   <li>Hotbar: 0–8</li>
 *   <li>Main inventory: 9–35</li>
 *   <li>Armor: 36–39 (boots=36, leggings=37, chestplate=38, helmet=39)</li>
 *   <li>Offhand: 40</li>
 * </ul>
 * These never change, regardless of which menu is open.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKInventory {

    // ── Unified player inventory position constants ────────────────────────
    public static final int HOTBAR_START = 0;
    public static final int HOTBAR_END = 8;
    public static final int HOTBAR_SIZE = 9;
    public static final int MAIN_START = 9;
    public static final int MAIN_END = 35;
    public static final int MAIN_SIZE = 27;
    public static final int ARMOR_START = 36;
    public static final int ARMOR_END = 39;
    public static final int ARMOR_SIZE = 4;
    public static final int OFFHAND = 40;
    public static final int TOTAL_PLAYER_SLOTS = 41;

    // ── Per-menu cache of resolved slot groups ────────────────────────────
    // Key: System.identityHashCode(menu). Rebuilt when menu changes.
    private static final Map<Integer, List<MKContainerMapping.SlotGroup>> menuGroupCache = new WeakHashMap<>();

    // ── Slot Resolution ───────────────────────────────────────────────────

    /**
     * Resolves a raw menu slot index to its MK container name and position
     * within that container.
     *
     * @param menu    the active menu
     * @param context the MKContext (determines which containers exist)
     * @param rawSlotIndex the raw slot index in the menu
     * @return a {@link SlotRef} with container name + local position, or null
     *         if the slot doesn't map to any known container
     */
    public static @Nullable SlotRef fromMenuSlot(AbstractContainerMenu menu,
                                                   @Nullable MKContext context,
                                                   int rawSlotIndex) {
        List<MKContainerMapping.SlotGroup> groups = getGroups(menu, context);
        for (MKContainerMapping.SlotGroup group : groups) {
            if (rawSlotIndex >= group.menuSlotStart() && rawSlotIndex <= group.menuSlotEnd()) {
                int localPos = rawSlotIndex - group.menuSlotStart();
                return new SlotRef(group.name(), localPos, group.persistence());
            }
        }
        return null;
    }

    /**
     * Resolves an MK container name + local position back to a raw menu
     * slot index in the given menu.
     *
     * @param menu          the active menu
     * @param context       the MKContext
     * @param containerName the MK container name (e.g., "mk:hotbar")
     * @param localPos      position within the container (0-based)
     * @return the raw menu slot index, or -1 if not found
     */
    public static int toMenuSlot(AbstractContainerMenu menu,
                                   @Nullable MKContext context,
                                   String containerName,
                                   int localPos) {
        List<MKContainerMapping.SlotGroup> groups = getGroups(menu, context);
        for (MKContainerMapping.SlotGroup group : groups) {
            if (group.name().equals(containerName)) {
                int rawIndex = group.menuSlotStart() + localPos;
                if (rawIndex <= group.menuSlotEnd()) {
                    return rawIndex;
                }
            }
        }
        return -1;
    }

    /**
     * Converts a raw menu slot index to a unified player inventory position
     * (0-40). Returns -1 if the slot is not a player inventory slot.
     *
     * <p>This works across all menu types: InventoryMenu, ChestMenu,
     * CraftingMenu, etc. The returned position is always consistent:
     * hotbar 0-8, main 9-35, armor 36-39, offhand 40.
     */
    public static int toUnifiedPlayerPos(AbstractContainerMenu menu,
                                           @Nullable MKContext context,
                                           int rawSlotIndex) {
        SlotRef ref = fromMenuSlot(menu, context, rawSlotIndex);
        if (ref == null) return -1;

        return switch (ref.containerName()) {
            case "mk:hotbar" -> HOTBAR_START + ref.localPos();
            case "mk:main_inventory" -> MAIN_START + ref.localPos();
            case "mk:armor" -> ARMOR_START + ref.localPos();
            case "mk:offhand" -> OFFHAND;
            default -> -1;  // not a player inventory slot
        };
    }

    /**
     * Converts a unified player inventory position (0-40) to a raw menu
     * slot index in the given menu. Returns -1 if not present in this menu
     * (e.g., armor/offhand in a chest menu).
     */
    public static int fromUnifiedPlayerPos(AbstractContainerMenu menu,
                                             @Nullable MKContext context,
                                             int unifiedPos) {
        String containerName;
        int localPos;

        if (unifiedPos >= HOTBAR_START && unifiedPos <= HOTBAR_END) {
            containerName = "mk:hotbar";
            localPos = unifiedPos - HOTBAR_START;
        } else if (unifiedPos >= MAIN_START && unifiedPos <= MAIN_END) {
            containerName = "mk:main_inventory";
            localPos = unifiedPos - MAIN_START;
        } else if (unifiedPos >= ARMOR_START && unifiedPos <= ARMOR_END) {
            containerName = "mk:armor";
            localPos = unifiedPos - ARMOR_START;
        } else if (unifiedPos == OFFHAND) {
            containerName = "mk:offhand";
            localPos = 0;
        } else {
            return -1;
        }

        return toMenuSlot(menu, context, containerName, localPos);
    }

    // ── Direct Item Access ────────────────────────────────────────────────

    /**
     * Gets an item from the player's inventory by unified position (0-40).
     * Bypasses the menu slot system entirely — reads directly from the
     * player's Inventory object. Works in any context.
     */
    public static ItemStack getPlayerItem(Player player, int unifiedPos) {
        return player.getInventory().getItem(unifiedPos);
    }

    /**
     * Sets an item in the player's inventory by unified position (0-40).
     * Bypasses the menu slot system entirely.
     */
    public static void setPlayerItem(Player player, int unifiedPos, ItemStack stack) {
        player.getInventory().setItem(unifiedPos, stack);
    }

    /**
     * Gets an item from any container by MK container name and local position.
     * Uses the menu's slot list to find the backing container.
     *
     * @return the item, or {@link ItemStack#EMPTY} if the container/slot
     *         is not found
     */
    public static ItemStack getItem(AbstractContainerMenu menu,
                                      @Nullable MKContext context,
                                      String containerName,
                                      int localPos) {
        int rawIndex = toMenuSlot(menu, context, containerName, localPos);
        if (rawIndex >= 0 && rawIndex < menu.slots.size()) {
            return menu.slots.get(rawIndex).getItem();
        }
        return ItemStack.EMPTY;
    }

    /**
     * Sets an item in any container by MK container name and local position.
     */
    public static void setItem(AbstractContainerMenu menu,
                                 @Nullable MKContext context,
                                 String containerName,
                                 int localPos,
                                 ItemStack stack) {
        int rawIndex = toMenuSlot(menu, context, containerName, localPos);
        if (rawIndex >= 0 && rawIndex < menu.slots.size()) {
            menu.slots.get(rawIndex).set(stack);
        }
    }

    // ── Container Queries ─────────────────────────────────────────────────

    /**
     * Returns all slot groups in the current menu. This gives the mod author
     * a view of every container present in the screen.
     */
    public static List<MKContainerMapping.SlotGroup> getSlotGroups(
            AbstractContainerMenu menu, @Nullable MKContext context) {
        return Collections.unmodifiableList(getGroups(menu, context));
    }

    /**
     * Returns the slot group for a specific container name, or null if
     * not present in the current menu.
     */
    public static MKContainerMapping.@Nullable SlotGroup getSlotGroup(
            AbstractContainerMenu menu, @Nullable MKContext context,
            String containerName) {
        for (MKContainerMapping.SlotGroup group : getGroups(menu, context)) {
            if (group.name().equals(containerName)) {
                return group;
            }
        }
        return null;
    }

    /**
     * Checks if a raw menu slot belongs to the player's inventory
     * (hotbar, main, armor, or offhand).
     */
    public static boolean isPlayerSlot(AbstractContainerMenu menu,
                                         @Nullable MKContext context,
                                         int rawSlotIndex) {
        return toUnifiedPlayerPos(menu, context, rawSlotIndex) >= 0;
    }

    /**
     * Returns the size of a container in the current menu, or 0 if not present.
     */
    public static int getContainerSize(AbstractContainerMenu menu,
                                         @Nullable MKContext context,
                                         String containerName) {
        MKContainerMapping.SlotGroup group = getSlotGroup(menu, context, containerName);
        return group != null ? group.size() : 0;
    }

    // ── Vanilla Slot → MK Container Wrapper ───────────────────────────────

    /**
     * Creates an {@link MKContainer} proxy for a non-player container
     * in the current menu. The proxy delegates to the vanilla slot's
     * backing Container via a region.
     *
     * @param menu          the active menu
     * @param context       the MKContext
     * @param containerName the MK container name (e.g., "mk:chest")
     * @return the proxy, or null if the container is not found or is a
     *         player inventory container (use {@link #createPlayerWrapper} for those)
     */
    public static @Nullable MKContainer createVanillaWrapper(
            AbstractContainerMenu menu, @Nullable MKContext context,
            String containerName) {
        // Don't wrap player inventory containers this way
        if (containerName.startsWith("mk:hotbar") || containerName.equals("mk:main_inventory")
                || containerName.equals("mk:armor") || containerName.equals("mk:offhand")) {
            return null;
        }

        MKContainerMapping.SlotGroup group = getSlotGroup(menu, context, containerName);
        if (group == null) return null;

        // Get the vanilla Container from the first slot in the group
        int firstSlotIndex = group.menuSlotStart();
        if (firstSlotIndex >= menu.slots.size()) return null;

        Slot firstSlot = menu.slots.get(firstSlotIndex);
        Container vanillaContainer = firstSlot.container;

        // Create a region for this container group
        int containerStartSlot = firstSlot.getContainerSlot();
        MKRegion region = new MKRegion(containerName, vanillaContainer,
                containerStartSlot, group.size(), group.persistence(),
                true, true);  // default shift-click enabled
        region.setMenuSlotRange(group.menuSlotStart(),
                group.menuSlotStart() + group.size() - 1);

        return new MKContainer(vanillaContainer, region);
    }

    /**
     * Creates an {@link MKContainer} proxy for a player inventory group.
     *
     * @param player        the player
     * @param containerName one of "mk:hotbar", "mk:main_inventory",
     *                      "mk:armor", "mk:offhand"
     * @return the proxy, or null if the name is not a player inventory container
     */
    public static @Nullable MKContainer createPlayerWrapper(
            Player player, String containerName) {
        Inventory inv = player.getInventory();
        return switch (containerName) {
            case "mk:hotbar" -> {
                MKRegion r = new MKRegion("mk:hotbar", inv, HOTBAR_START, HOTBAR_SIZE,
                        MKContainerDef.Persistence.PERSISTENT, true, true,
                        MKContainerType.HOTBAR);
                yield new MKContainer(inv, r);
            }
            case "mk:main_inventory" -> {
                MKRegion r = new MKRegion("mk:main_inventory", inv, MAIN_START, MAIN_SIZE,
                        MKContainerDef.Persistence.PERSISTENT, true, true,
                        MKContainerType.SIMPLE);
                yield new MKContainer(inv, r);
            }
            case "mk:armor" -> {
                MKRegion r = new MKRegion("mk:armor", inv, ARMOR_START, ARMOR_SIZE,
                        MKContainerDef.Persistence.PERSISTENT, true, true,
                        MKContainerType.EQUIPMENT);
                yield new MKContainer(inv, r);
            }
            case "mk:offhand" -> {
                MKRegion r = new MKRegion("mk:offhand", inv, OFFHAND, 1,
                        MKContainerDef.Persistence.PERSISTENT, true, true,
                        MKContainerType.EQUIPMENT);
                yield new MKContainer(inv, r);
            }
            default -> null;
        };
    }

    // ── Cache ─────────────────────────────────────────────────────────────

    private static List<MKContainerMapping.SlotGroup> getGroups(
            AbstractContainerMenu menu, @Nullable MKContext context) {
        // Use menu identity hash + context as cache key
        int key = System.identityHashCode(menu);
        List<MKContainerMapping.SlotGroup> cached = menuGroupCache.get(key);
        if (cached != null) return cached;

        List<MKContainerMapping.SlotGroup> groups = MKContainerMapping.getSlotGroups(menu, context);
        menuGroupCache.put(key, groups);
        return groups;
    }

    /**
     * Clears the cache for a menu that's being closed.
     * Called from menu removal hooks.
     */
    public static void clearCache(AbstractContainerMenu menu) {
        menuGroupCache.remove(System.identityHashCode(menu));
    }

    // ── SlotRef record ────────────────────────────────────────────────────

    /**
     * A resolved slot reference: which MK container it belongs to and its
     * position within that container.
     *
     * @param containerName the MK container name (e.g., "mk:hotbar", "mk:chest")
     * @param localPos      0-based position within the container
     * @param persistence   how items in this slot behave
     */
    public record SlotRef(
            String containerName,
            int localPos,
            MKContainerDef.Persistence persistence
    ) {
        /** Whether this slot is in the player's inventory. */
        public boolean isPlayerSlot() {
            return containerName.equals("mk:hotbar")
                    || containerName.equals("mk:main_inventory")
                    || containerName.equals("mk:armor")
                    || containerName.equals("mk:offhand");
        }

        /** Whether items in this slot persist when the screen closes. */
        public boolean isPersistent() {
            return persistence == MKContainerDef.Persistence.PERSISTENT;
        }

        /** Whether this is an output-only slot (can take but not place). */
        public boolean isOutput() {
            return persistence == MKContainerDef.Persistence.OUTPUT;
        }

        /** Whether items are ejected when the screen closes. */
        public boolean isTransient() {
            return persistence == MKContainerDef.Persistence.TRANSIENT;
        }

        /**
         * Converts to a unified player inventory position (0-40).
         * Returns -1 if not a player slot.
         */
        public int toUnifiedPlayerPos() {
            return switch (containerName) {
                case "mk:hotbar" -> MKInventory.HOTBAR_START + localPos;
                case "mk:main_inventory" -> MKInventory.MAIN_START + localPos;
                case "mk:armor" -> MKInventory.ARMOR_START + localPos;
                case "mk:offhand" -> MKInventory.OFFHAND;
                default -> -1;
            };
        }
    }
}
