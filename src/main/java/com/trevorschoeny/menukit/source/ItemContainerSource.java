package com.trevorschoeny.menukit.source;

import com.trevorschoeny.menukit.container.MKContainer;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.function.Supplier;

/**
 * Source backed by an item's {@code DataComponents.CONTAINER} component.
 *
 * <p>Uses a {@code Supplier<ItemStack>} to resolve the backing item live.
 * This is critical because vanilla may REPLACE the ItemStack in the inventory
 * slot during interactions. A direct reference would go stale.
 *
 * <p>Enforces vanilla's shulker-in-shulker restriction.
 */
class ItemContainerSource implements MKContainerSource {

    private static final int SHULKER_SIZE = 27;
    private final Supplier<ItemStack> stackSupplier;

    ItemContainerSource(Supplier<ItemStack> stackSupplier) {
        this.stackSupplier = stackSupplier;
    }

    /** Convenience constructor for a fixed ItemStack reference. */
    ItemContainerSource(ItemStack stack) {
        this(() -> stack);
    }

    private ItemStack stack() {
        return stackSupplier.get();
    }

    @Override
    public void populate(MKContainer container) {
        ItemStack stack = stack();
        ItemContainerContents contents = stack.isEmpty() ? ItemContainerContents.EMPTY
                : stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);

        NonNullList<ItemStack> items = NonNullList.withSize(SHULKER_SIZE, ItemStack.EMPTY);
        contents.copyInto(items);

        int count = Math.min(items.size(), container.getContainerSize());
        for (int i = 0; i < count; i++) {
            container.setItem(i, items.get(i));
        }

    }

    @Override
    public void sync(MKContainer container) {
        ItemStack stack = stack();
        if (stack.isEmpty()) return;

        NonNullList<ItemStack> items = NonNullList.withSize(SHULKER_SIZE, ItemStack.EMPTY);
        int count = Math.min(SHULKER_SIZE, container.getContainerSize());
        for (int i = 0; i < count; i++) {
            items.set(i, container.getItem(i).copy());
        }

        ItemContainerContents newContents = ItemContainerContents.fromItems(items);
        stack.set(DataComponents.CONTAINER, newContents);
    }

    @Override
    public boolean defersSync() {
        // Component-based backing store: rewriting ItemContainerContents on
        // every slot change causes broadcastChanges to re-send the entire
        // shulker ItemStack each tick, producing visual flashing. Deferring
        // sync to unbind means the component is written once, atomically.
        return true;
    }

    // No pollExternalChanges override — with deferred sync, the container
    // is the source of truth while bound. The tracking mixin skips polling
    // for sources that defer sync.

    @Override
    public int size() {
        return SHULKER_SIZE;
    }

    @Override
    public boolean canAccept(int slot, ItemStack candidate) {
        return !isShulkerBox(candidate);
    }

    private static boolean isShulkerBox(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof BlockItem bi
                && bi.getBlock() instanceof ShulkerBoxBlock;
    }
}
