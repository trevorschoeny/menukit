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
}
