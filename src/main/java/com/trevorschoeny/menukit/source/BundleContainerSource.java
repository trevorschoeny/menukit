package com.trevorschoeny.menukit.source;

import com.trevorschoeny.menukit.MKContainer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

import java.util.ArrayList;
import java.util.List;

/**
 * Source backed by an item's {@code DataComponents.BUNDLE_CONTENTS} component.
 *
 * <p>Bundles use weight-based storage rather than fixed slots. Each item costs
 * {@code 64 / maxStackSize} weight units, with a total capacity of 64 units.
 * This source spreads bundle items across sequential container slots and
 * enforces weight limits via {@link #canAccept}.
 *
 * <p>On populate, items from the bundle are placed into slots 0, 1, 2, etc.
 * On sync, non-empty slots are collected and rebuilt into a {@link BundleContents}.
 */
class BundleContainerSource implements MKContainerSource {

    // Reasonable max slots for a bundle grid — bundles can hold many small stacks
    // but rarely exceed ~16 distinct item types in practice
    private static final int MAX_BUNDLE_SLOTS = 64;

    private final ItemStack stack;

    BundleContainerSource(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public void populate(MKContainer container) {
        BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (contents == null) return;

        // Spread bundle items across sequential slots
        int slot = 0;
        for (ItemStack item : contents.items()) {
            if (slot >= container.getContainerSize()) break;
            if (!item.isEmpty()) {
                container.setItem(slot++, item.copy());
            }
        }
    }

    @Override
    public void sync(MKContainer container) {
        // Collect non-empty items, consolidating matching stacks (auto-stack)
        List<ItemStack> items = new ArrayList<>();
        int count = Math.min(MAX_BUNDLE_SLOTS, container.getContainerSize());
        for (int i = 0; i < count; i++) {
            ItemStack item = container.getItem(i);
            if (item.isEmpty()) continue;

            // Try to merge with an existing stack in the collected list
            boolean merged = false;
            for (ItemStack existing : items) {
                if (ItemStack.isSameItemSameComponents(existing, item)
                        && existing.getCount() < existing.getMaxStackSize()) {
                    int space = existing.getMaxStackSize() - existing.getCount();
                    int toAdd = Math.min(item.getCount(), space);
                    existing.grow(toAdd);
                    item.shrink(toAdd);
                    if (item.isEmpty()) {
                        merged = true;
                        break;
                    }
                }
            }
            // If not fully merged (or no match), add as a new entry
            if (!merged && !item.isEmpty()) {
                items.add(item.copy());
            }
        }
        stack.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(items));

        // Compact the container slots — remove gaps so items are packed
        // sequentially from slot 0. This makes the panel visually consistent
        // (no empty slots in the middle, just one trailing empty slot).
        for (int i = 0; i < items.size(); i++) {
            container.setItem(i, items.get(i));
        }
        // Clear any remaining slots beyond the compacted items
        for (int i = items.size(); i < count; i++) {
            container.setItem(i, ItemStack.EMPTY);
        }
    }

    @Override
    public int size() {
        // Report current item count + a few empty slots for insertion.
        // Capped at MAX_BUNDLE_SLOTS.
        BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (contents == null) return 4; // empty bundle, show a few slots
        int itemCount = 0;
        for (ItemStack item : contents.items()) {
            if (!item.isEmpty()) itemCount++;
        }
        // Show current items + up to 4 empty slots for adding more
        return Math.min(itemCount + 4, MAX_BUNDLE_SLOTS);
    }

    @Override
    public boolean canAccept(int slot, ItemStack candidate) {
        if (candidate.isEmpty()) return true;

        // Build the current bundle state from the backing item (not the container,
        // since the container might be mid-modification) and check if the candidate fits.
        BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (contents == null) {
            contents = BundleContents.EMPTY;
        }

        // Use Mutable.tryInsert to check weight limits — it returns 0 if no room
        BundleContents.Mutable mutable = new BundleContents.Mutable(contents);
        return mutable.tryInsert(candidate.copyWithCount(1)) > 0;
    }
}
