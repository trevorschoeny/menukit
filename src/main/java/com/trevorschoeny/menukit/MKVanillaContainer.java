package com.trevorschoeny.menukit;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * An MKContainer that delegates all item access to any vanilla {@link Container}.
 *
 * <p>Used to wrap interaction screen containers: chest contents, furnace slots,
 * crafting grids, anvil inputs, etc. Like {@link MKInventoryContainer}, this is
 * a transparent lens — reads and writes go directly to the vanilla Container.
 *
 * <p>Created dynamically when an interaction screen opens. The vanilla Container
 * reference comes from the slot's container field in the menu.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKVanillaContainer extends MKContainer {

    private final String name;
    private final Container vanillaContainer;
    private final int startSlot;  // offset within the vanilla container (usually 0)

    /**
     * Creates a container that delegates to a vanilla Container.
     *
     * @param name             container name (e.g., "mk:chest", "mk:craft_3x3")
     * @param size             number of slots this MK container exposes
     * @param vanillaContainer the vanilla Container to delegate to
     * @param startSlot        offset within the vanilla container (usually 0)
     */
    public MKVanillaContainer(String name, int size, Container vanillaContainer, int startSlot) {
        super(size);
        this.name = name;
        this.vanillaContainer = vanillaContainer;
        this.startSlot = startSlot;
    }

    /**
     * Convenience constructor with startSlot=0.
     */
    /**
     * Convenience constructor with startSlot=0.
     */
    public MKVanillaContainer(String name, int size, Container vanillaContainer) {
        this(name, size, vanillaContainer, 0);
    }

    /** Returns the container name (e.g., "mk:chest"). */
    public String getName() {
        return name;
    }

    // ── Item access — delegates to vanilla Container ─────────────────────────

    @Override
    public ItemStack getItem(int slot) {
        return vanillaContainer.getItem(startSlot + slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return vanillaContainer.removeItem(startSlot + slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return vanillaContainer.removeItemNoUpdate(startSlot + slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        vanillaContainer.setItem(startSlot + slot, stack);
    }

    @Override
    public void setChanged() {
        vanillaContainer.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return vanillaContainer.stillValid(player);
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    /** Returns the vanilla Container this MK container delegates to. */
    public Container getVanillaContainer() {
        return vanillaContainer;
    }

    /** Returns the slot offset within the vanilla container. */
    public int getStartSlot() {
        return startSlot;
    }
}
