package com.trevorschoeny.menukit.source;

import com.trevorschoeny.menukit.MKContainer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Bridges an external backing store to an {@link MKContainer}.
 *
 * <p>A container source lets MenuKit's slot/panel system interact with data
 * that lives outside MenuKit — item contents (shulker boxes), weight-based
 * item lists (bundles), or live vanilla containers (ender chests).
 *
 * <p>The lifecycle is:
 * <ol>
 *   <li>{@link MKContainer#bind(MKContainerSource)} calls {@link #populate}
 *       to fill the container from the backing store.</li>
 *   <li>Each time the container changes, {@link #sync} is called automatically
 *       to write changes back to the backing store.</li>
 *   <li>{@link MKContainer#unbind()} calls {@link #sync} one final time,
 *       then clears the container.</li>
 * </ol>
 *
 * <p>Use the factory methods to create sources for common backing stores:
 * <ul>
 *   <li>{@link #ofContainer(Container)} — live vanilla Container (ender chest, block entities)</li>
 *   <li>{@link #ofItemContainer(ItemStack)} — item's {@code DataComponents.CONTAINER} (shulker boxes)</li>
 *   <li>{@link #ofBundle(ItemStack)} — item's {@code DataComponents.BUNDLE_CONTENTS} (bundles)</li>
 * </ul>
 *
 * <p>Part of the <b>MenuKit</b> API.
 */
public interface MKContainerSource {

    /**
     * Reads items from the backing store into the container.
     * Called once when the source is bound.
     */
    void populate(MKContainer container);

    /**
     * Writes the container's current contents back to the backing store.
     * Called automatically on every container change and once on unbind.
     */
    void sync(MKContainer container);

    /**
     * How many slots this source provides. The container may be larger
     * (registered with a max size) — slots beyond this count stay empty.
     */
    int size();

    /**
     * Whether the given item can be placed into the given slot.
     * Checked by {@link com.trevorschoeny.menukit.MKSlot#mayPlace(ItemStack)}
     * in addition to the slot's own filter.
     *
     * <p>Use this for source-level constraints like bundle weight limits
     * or the shulker-in-shulker restriction.
     *
     * @param slot  the container slot index
     * @param stack the item being placed
     */
    boolean canAccept(int slot, ItemStack stack);

    /**
     * Checks if the backing store was modified externally (outside of
     * {@link #sync}). If so, re-populates the container from the backing
     * store and returns true.
     *
     * <p>Called on each {@code broadcastChanges} tick for bound containers.
     * Default returns false (no external change detection). Override for
     * sources that can be modified by vanilla mechanics (e.g., bundle pickup,
     * ender chest access from another player).
     *
     * @param container the MKContainer to re-populate if needed
     * @return true if external changes were detected and the container was updated
     */
    default boolean pollExternalChanges(MKContainer container) {
        return false;
    }

    /**
     * Whether this source defers sync to unbind-time only.
     *
     * <p>When true, {@link MKContainer#setChanged()} will NOT call
     * {@link #sync} on every item change. Instead, sync happens once
     * when the container is unbound. External change polling is also
     * skipped, since the container — not the backing store — is the
     * source of truth while bound.
     *
     * <p>Use this for component-based backing stores (like shulker box
     * {@code ItemContainerContents}) where rewriting the component on
     * every slot change causes unnecessary network traffic and visual
     * flashing. The final sync on unbind writes everything back atomically.
     *
     * <p>Default is {@code false} (continuous sync on every change).
     */
    default boolean defersSync() {
        return false;
    }

    /**
     * Returns how many MORE items of the given type this source can accept.
     * Used by {@link com.trevorschoeny.menukit.mixin.MKSlotMixin} to limit
     * {@code getMaxStackSize(ItemStack)} so vanilla's {@code safeInsert}
     * naturally performs partial insertion (leaving remainder on cursor).
     *
     * <p>Default returns {@code Integer.MAX_VALUE} (no source-level limit).
     * Override for weight-based sources like bundles.
     *
     * @param slot  the container slot index
     * @param stack the item being placed (type matters, count is ignored)
     */
    default int getMaxAcceptCount(int slot, ItemStack stack) {
        return Integer.MAX_VALUE;
    }

    // ── Factory Methods ──────────────────────────────────────────────────

    /**
     * Creates a source backed by a live vanilla {@link Container}.
     *
     * <p>Ideal for ender chests ({@code player.getEnderChestInventory()})
     * or any block entity that implements Container. Changes are copied
     * bidirectionally — the source container and the MKContainer stay in sync.
     */
    static MKContainerSource ofContainer(Container container) {
        return new LiveContainerSource(container);
    }

    /**
     * Creates a source backed by an item's {@code DataComponents.CONTAINER}.
     *
     * <p>For shulker boxes and other items that store inventory via
     * {@link net.minecraft.world.item.component.ItemContainerContents}.
     * The item's component is read on populate and rewritten on every sync.
     *
     * @param stack the item stack containing the CONTAINER component
     */
    static MKContainerSource ofItemContainer(ItemStack stack) {
        return new ItemContainerSource(stack);
    }

    /**
     * Creates a source backed by an item's {@code DataComponents.CONTAINER},
     * using a live supplier to resolve the current ItemStack.
     *
     * <p>Prefer this over the direct-reference overload when the ItemStack
     * in the inventory slot may be REPLACED by vanilla (e.g., during
     * interactions that rebuild the item's components).
     *
     * @param stackSupplier supplier returning the current live ItemStack
     */
    static MKContainerSource ofItemContainer(java.util.function.Supplier<ItemStack> stackSupplier) {
        return new ItemContainerSource(stackSupplier);
    }

    /**
     * Creates a source backed by an item's {@code DataComponents.BUNDLE_CONTENTS}.
     *
     * <p>For bundles. Items are spread across slots sequentially.
     * The {@link #canAccept} method enforces bundle weight limits.
     *
     * @param stack the bundle item stack
     */
    static MKContainerSource ofBundle(ItemStack stack) {
        return new BundleContainerSource(stack);
    }

    /**
     * Creates a source backed by an item's {@code DataComponents.BUNDLE_CONTENTS},
     * using a live supplier to resolve the current ItemStack.
     *
     * <p>Prefer this over the direct-reference overload when the ItemStack
     * may be REPLACED by vanilla during bundle interactions (scroll+right-click).
     *
     * @param stackSupplier supplier returning the current live ItemStack
     */
    static MKContainerSource ofBundle(java.util.function.Supplier<ItemStack> stackSupplier) {
        return new BundleContainerSource(stackSupplier);
    }
}
