package com.trevorschoeny.menukit;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * An MKContainer that delegates all item access to vanilla's {@link Inventory}.
 *
 * <p>This is a transparent lens — reads and writes go directly to the vanilla
 * Inventory object. Other mods that access the Inventory directly see the exact
 * same data because it IS the same data. No copies, no sync needed.
 *
 * <p>Four instances are auto-created per player by MenuKit:
 * <ul>
 *   <li>{@code mk:hotbar} — inventory positions 0-8 (9 slots)</li>
 *   <li>{@code mk:main_inventory} — inventory positions 9-35 (27 slots)</li>
 *   <li>{@code mk:armor} — inventory positions 36-39 (4 slots)</li>
 *   <li>{@code mk:offhand} — inventory position 40 (1 slot)</li>
 * </ul>
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKInventoryContainer extends MKContainer {

    private final String name;
    private final Inventory inventory;
    private final int startSlot;  // first vanilla inventory index this container maps to

    /**
     * Creates a container that delegates to a range of vanilla Inventory slots.
     *
     * @param name      container name (e.g., "mk:hotbar")
     * @param size      number of slots in this container
     * @param inventory the vanilla Inventory to delegate to
     * @param startSlot the first vanilla inventory index (e.g., 0 for hotbar, 9 for main)
     */
    public MKInventoryContainer(String name, int size, Inventory inventory, int startSlot) {
        super(size);
        this.name = name;
        this.inventory = inventory;
        this.startSlot = startSlot;
    }

    // ── Item access — delegates to vanilla Inventory ─────────────────────────

    @Override
    public ItemStack getItem(int slot) {
        return inventory.getItem(startSlot + slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return inventory.removeItem(startSlot + slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return inventory.removeItemNoUpdate(startSlot + slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        inventory.setItem(startSlot + slot, stack);
    }

    @Override
    public void setChanged() {
        inventory.setChanged();
    }

    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player player) {
        return inventory.stillValid(player);
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    /** Returns the container name (e.g., "mk:hotbar"). */
    public String getName() {
        return name;
    }

    /** Returns the vanilla Inventory this container delegates to. */
    public Inventory getInventory() {
        return inventory;
    }

    /** Returns the first vanilla inventory index this container maps to. */
    public int getStartSlot() {
        return startSlot;
    }

    /**
     * Translates a container-local slot index to a vanilla inventory index.
     * E.g., for mk:hotbar with startSlot=0, slot 3 maps to inventory index 3.
     * For mk:main_inventory with startSlot=9, slot 0 maps to inventory index 9.
     */
    public int toInventoryIndex(int containerSlot) {
        return startSlot + containerSlot;
    }
}
