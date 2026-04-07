package com.trevorschoeny.menukit.source;

import com.trevorschoeny.menukit.container.MKContainer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Source backed by a live vanilla {@link Container}.
 *
 * <p>Both populate and sync copy items between the two containers.
 * Copies are used in both directions to avoid aliasing — each container
 * owns its own ItemStack instances.
 *
 * <p>Typical use: ender chest ({@code player.getEnderChestInventory()})
 * or any block entity that implements Container.
 *
 * <p>External changes are detected by comparing the backing container's
 * items against the MKContainer's items on each poll tick.
 */
class LiveContainerSource implements MKContainerSource {

    private final Container backing;

    LiveContainerSource(Container backing) {
        this.backing = backing;
    }

    @Override
    public void populate(MKContainer container) {
        // Copy items from backing container into MKContainer
        int count = Math.min(backing.getContainerSize(), container.getContainerSize());
        for (int i = 0; i < count; i++) {
            container.setItem(i, backing.getItem(i).copy());
        }
    }

    @Override
    public void sync(MKContainer container) {
        // Write MKContainer contents back to backing container
        int count = Math.min(backing.getContainerSize(), container.getContainerSize());
        for (int i = 0; i < count; i++) {
            backing.setItem(i, container.getItem(i).copy());
        }
        backing.setChanged();
    }

    @Override
    public boolean pollExternalChanges(MKContainer container) {
        // Compare backing container items against the MKContainer.
        // If any slot differs, something external modified the backing
        // container (e.g., another player accessed the ender chest,
        // or a hopper inserted/extracted items).
        int count = Math.min(backing.getContainerSize(), container.getContainerSize());
        boolean changed = false;
        for (int i = 0; i < count; i++) {
            if (!ItemStack.matches(backing.getItem(i), container.getItem(i))) {
                changed = true;
                break;
            }
        }

        if (changed) {
            // Suppress sync so setItem calls during populate don't write
            // the MKContainer's stale state back to the backing container
            // (which would undo the external change and cause duplication).
            container.withSyncSuppressed(() -> populate(container));
        }
        return changed;
    }

    @Override
    public int size() {
        return backing.getContainerSize();
    }

    @Override
    public boolean canAccept(int slot, ItemStack stack) {
        // Live containers have no special restrictions beyond the slot's own filter
        return true;
    }
}
