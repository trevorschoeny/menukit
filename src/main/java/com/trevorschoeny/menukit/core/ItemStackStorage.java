package com.trevorschoeny.menukit.core;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.function.Supplier;

/**
 * Storage that reads and writes items from/to an {@code ItemStack}'s
 * {@code DataComponents.CONTAINER} component. This is how shulker boxes,
 * bundles, and other item-based containers work in 1.20.5+ component-land.
 *
 * <p>Uses a live supplier to resolve the current ItemStack, since the
 * stack in an inventory slot may be replaced by vanilla during interactions.
 *
 * <p>Implements {@link PersistentStorage} because item component data
 * persists with the item.
 *
 * <p>Absorbs the old {@code ItemContainerSource} and {@code BundleContainerSource}
 * patterns.
 */
public class ItemStackStorage implements PersistentStorage {

    private final int size;
    private final Supplier<ItemStack> stackSupplier;
    private final NonNullList<ItemStack> items; // working copy

    /**
     * @param size          number of slots to expose
     * @param stackSupplier live supplier for the backing ItemStack
     */
    public ItemStackStorage(int size, Supplier<ItemStack> stackSupplier) {
        this.size = size;
        this.stackSupplier = stackSupplier;
        this.items = NonNullList.withSize(size, ItemStack.EMPTY);
    }

    /** Factory with a fixed stack reference. */
    public static ItemStackStorage of(int size, ItemStack stack) {
        return new ItemStackStorage(size, () -> stack);
    }

    /** Factory with a live supplier. */
    public static ItemStackStorage of(int size, Supplier<ItemStack> stackSupplier) {
        return new ItemStackStorage(size, stackSupplier);
    }

    /**
     * Populates the working copy from the item's CONTAINER component.
     * Call this when binding the storage to read the current state.
     */
    public void populate() {
        ItemStack stack = stackSupplier.get();
        if (stack.isEmpty()) return;

        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null) return;

        // Clear working copy
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }

        // Read from component
        contents.copyInto(items);
    }

    /**
     * Writes the working copy back to the item's CONTAINER component.
     * Call this on every change or on unbind.
     */
    public void syncToItem() {
        ItemStack stack = stackSupplier.get();
        if (stack.isEmpty()) return;

        stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
    }

    @Override
    public ItemStack getStack(int localIndex) {
        return items.get(localIndex);
    }

    @Override
    public void setStack(int localIndex, ItemStack stack) {
        items.set(localIndex, stack);
        markDirty();
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public void markDirty() {
        // Write back to the item component immediately.
        // Phase 3 may add a deferred-sync option for performance.
        syncToItem();
    }

    @Override
    public void save(ValueOutput output) {
        // Item component data persists with the item — this serialize is
        // for cases where the storage is saved independently.
        ValueOutput.ValueOutputList list = output.childrenList("Items");
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).isEmpty()) {
                ValueOutput entry = list.addChild();
                entry.putInt("Slot", i);
                entry.store("Item", ItemStack.CODEC, items.get(i).copy());
            }
        }
        output.putInt("Size", size);
    }

    @Override
    public void load(ValueInput input) {
        for (int i = 0; i < items.size(); i++) {
            items.set(i, ItemStack.EMPTY);
        }
        for (ValueInput entry : input.childrenListOrEmpty("Items")) {
            int slot = entry.getInt("Slot").orElse(-1);
            if (slot >= 0 && slot < items.size()) {
                entry.read("Item", ItemStack.CODEC).ifPresent(stack -> {
                    if (!stack.isEmpty()) {
                        items.set(slot, stack);
                    }
                });
            }
        }
    }
}
