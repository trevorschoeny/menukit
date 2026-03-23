package com.trevorschoeny.menukit.source;

import com.trevorschoeny.menukit.MKContainer;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/**
 * Source backed by an item's {@code DataComponents.CONTAINER} component.
 *
 * <p>For shulker boxes and any other items that store inventory via
 * {@link ItemContainerContents}. Reads the immutable snapshot on populate,
 * rebuilds and writes it back on every sync.
 *
 * <p>Enforces vanilla's shulker-in-shulker restriction: shulker box items
 * cannot be placed into a container backed by another shulker box.
 */
class ItemContainerSource implements MKContainerSource {

    private static final int SHULKER_SIZE = 27;
    private final ItemStack stack;

    ItemContainerSource(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public void populate(MKContainer container) {
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null) return;

        // copyInto fills the list with items at their original slot positions
        NonNullList<ItemStack> items = NonNullList.withSize(SHULKER_SIZE, ItemStack.EMPTY);
        contents.copyInto(items);

        int count = Math.min(items.size(), container.getContainerSize());
        for (int i = 0; i < count; i++) {
            container.setItem(i, items.get(i));
        }
    }

    @Override
    public void sync(MKContainer container) {
        // Collect all items and rebuild the immutable component
        NonNullList<ItemStack> items = NonNullList.withSize(SHULKER_SIZE, ItemStack.EMPTY);
        int count = Math.min(SHULKER_SIZE, container.getContainerSize());
        for (int i = 0; i < count; i++) {
            items.set(i, container.getItem(i));
        }
        stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
    }

    @Override
    public int size() {
        return SHULKER_SIZE;
    }

    @Override
    public boolean canAccept(int slot, ItemStack candidate) {
        // Vanilla restriction: shulker boxes cannot be nested inside shulker boxes
        return !isShulkerBox(candidate);
    }

    private static boolean isShulkerBox(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof BlockItem bi
                && bi.getBlock() instanceof ShulkerBoxBlock;
    }
}
