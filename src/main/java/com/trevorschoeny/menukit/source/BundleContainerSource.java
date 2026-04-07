package com.trevorschoeny.menukit.source;

import com.trevorschoeny.menukit.container.MKContainer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Source backed by an item's {@code DataComponents.BUNDLE_CONTENTS} component.
 *
 * <p>Uses a {@code Supplier<ItemStack>} to resolve the backing item live.
 * This is critical because vanilla may REPLACE the ItemStack in the inventory
 * slot (e.g., when popping items via bundle scroll+right-click). A direct
 * reference would go stale; the supplier always returns the current live item.
 *
 * <p>On populate, items from the bundle are placed into slots 0, 1, 2, etc.
 * On sync, non-empty slots are collected and rebuilt into a {@link BundleContents}.
 */
class BundleContainerSource implements MKContainerSource {

    private static final int MAX_BUNDLE_SLOTS = 64;

    // Supplier that returns the CURRENT ItemStack from the inventory slot.
    // Vanilla may replace the ItemStack object, so we can't hold a direct ref.
    private final Supplier<ItemStack> stackSupplier;

    // Snapshot of the BundleContents after the last sync or populate.
    private BundleContents lastSyncedContents;

    BundleContainerSource(Supplier<ItemStack> stackSupplier) {
        this.stackSupplier = stackSupplier;
    }

    /** Convenience constructor for a fixed ItemStack reference (non-live). */
    BundleContainerSource(ItemStack stack) {
        this(() -> stack);
    }

    /** Returns the current live backing ItemStack. */
    private ItemStack stack() {
        return stackSupplier.get();
    }

    @Override
    public void populate(MKContainer container) {
        ItemStack stack = stack();
        BundleContents contents = stack.isEmpty() ? BundleContents.EMPTY
                : stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);

        // Clear all slots first so stale items from a previous bind don't linger
        for (int i = 0; i < container.getContainerSize(); i++) {
            container.setItem(i, ItemStack.EMPTY);
        }

        // Spread bundle items across sequential slots
        int slot = 0;
        for (ItemStack item : contents.items()) {
            if (slot >= container.getContainerSize()) break;
            if (!item.isEmpty()) {
                container.setItem(slot++, item.copy());
            }
        }

        lastSyncedContents = contents;
    }

    @Override
    public void sync(MKContainer container) {
        ItemStack stack = stack();
        // Skip sync if the backing item is empty (count 0, on cursor)
        if (stack.isEmpty()) return;

        // Collect non-empty items, consolidating matching stacks (auto-stack).
        // Does NOT compact container slots — items shouldn't shift mid-interaction.
        List<ItemStack> items = new ArrayList<>();
        int count = Math.min(MAX_BUNDLE_SLOTS, container.getContainerSize());
        for (int i = 0; i < count; i++) {
            ItemStack item = container.getItem(i);
            if (item.isEmpty()) continue;

            // Work with a COPY — never modify the live container item.
            ItemStack itemCopy = item.copy();

            boolean merged = false;
            for (ItemStack existing : items) {
                if (ItemStack.isSameItemSameComponents(existing, itemCopy)
                        && existing.getCount() < existing.getMaxStackSize()) {
                    int space = existing.getMaxStackSize() - existing.getCount();
                    int toAdd = Math.min(itemCopy.getCount(), space);
                    existing.grow(toAdd);
                    itemCopy.shrink(toAdd);
                    if (itemCopy.isEmpty()) {
                        merged = true;
                        break;
                    }
                }
            }
            if (!merged && !itemCopy.isEmpty()) {
                items.add(itemCopy);
            }
        }

        BundleContents newContents = new BundleContents(items);
        stack.set(DataComponents.BUNDLE_CONTENTS, newContents);
        lastSyncedContents = newContents;
    }

    @Override
    public boolean pollExternalChanges(MKContainer container) {
        ItemStack stack = stack();
        // Skip if backing item is empty (count 0, on cursor)
        if (stack.isEmpty()) return false;

        BundleContents current = stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);

        // Fast path: same reference
        if (current == lastSyncedContents) return false;

        // Deep compare
        if (contentsMatch(current, lastSyncedContents)) {
            lastSyncedContents = current;
            return false;
        }

        // External change — re-populate with sync suppressed
        container.withSyncSuppressed(() -> populate(container));
        return true;
    }

    private static boolean contentsMatch(BundleContents a, BundleContents b) {
        if (a == b) return true;
        if (a == null || b == null) return false;

        List<ItemStack> aItems = new ArrayList<>();
        a.items().forEach(aItems::add);
        List<ItemStack> bItems = new ArrayList<>();
        b.items().forEach(bItems::add);

        if (aItems.size() != bItems.size()) return false;
        for (int i = 0; i < aItems.size(); i++) {
            if (!ItemStack.matches(aItems.get(i), bItems.get(i))) return false;
        }
        return true;
    }

    @Override
    public int size() {
        ItemStack stack = stack();
        BundleContents contents = stack.isEmpty() ? null : stack.get(DataComponents.BUNDLE_CONTENTS);
        if (contents == null) return 4;
        int itemCount = 0;
        for (ItemStack item : contents.items()) {
            if (!item.isEmpty()) itemCount++;
        }
        return Math.min(itemCount + 4, MAX_BUNDLE_SLOTS);
    }

    @Override
    public boolean canAccept(int slot, ItemStack candidate) {
        if (candidate.isEmpty()) return true;
        ItemStack stack = stack();
        BundleContents contents = stack.isEmpty() ? BundleContents.EMPTY
                : stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        BundleContents.Mutable mutable = new BundleContents.Mutable(contents);
        return mutable.tryInsert(candidate.copyWithCount(1)) > 0;
    }

    @Override
    public int getMaxAcceptCount(int slot, ItemStack candidate) {
        if (candidate.isEmpty()) return 0;
        ItemStack stack = stack();
        BundleContents contents = stack.isEmpty() ? BundleContents.EMPTY
                : stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        BundleContents.Mutable mutable = new BundleContents.Mutable(contents);
        return mutable.tryInsert(candidate.copyWithCount(candidate.getMaxStackSize()));
    }
}
