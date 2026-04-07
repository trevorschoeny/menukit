package com.trevorschoeny.menukit.container;

import com.trevorschoeny.menukit.MenuKit;

import com.trevorschoeny.menukit.region.MKRegion;

import com.trevorschoeny.menukit.source.MKContainerSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Proxy wrapper around a vanilla {@link Container}. Implements the Container
 * interface by delegating all calls to the underlying delegate.
 *
 * <p><b>Design philosophy:</b> MKContainer is a convenience API for mod
 * authors — it wraps any vanilla Container and adds MenuKit features on top.
 * The delegate Container is always the real data store. Other mods can
 * interact with the delegate directly via {@link #getDelegate()} and
 * everything stays in sync.
 *
 * <p><b>For custom containers:</b> The delegate is a {@link SimpleContainer}
 * (or any other Container if explicitly specified). Created via:
 * <pre>{@code
 * MenuKit.slotGroup("my_storage").slots(27).register();
 * }</pre>
 *
 * <p><b>For vanilla containers:</b> The delegate is the actual vanilla
 * Container (player Inventory, chest block entity, etc.). Created
 * automatically when menus open.
 *
 * <p><b>Region-aware:</b> When wrapping a specific region of a larger
 * container (e.g., hotbar = indices 0-8 of player Inventory), the proxy
 * remaps indices so the mod author sees a clean 0-based container.
 *
 * <p>Part of the <b>MenuKit</b> API (proxy layer).
 */
public class MKContainer implements Container {

    private final Container delegate;
    private final @Nullable MKRegion region;
    private @Nullable Runnable onChange;
    private @Nullable MKContainerSource source;
    private boolean syncing; // re-entrancy guard for source sync

    // ── Constructors ─────────────────────────────────────────────────────

    /**
     * Creates a proxy with a new SimpleContainer as delegate.
     * Used for custom mod containers.
     */
    public MKContainer(int size) {
        this(new SimpleContainer(size), null);
    }

    /**
     * Creates a proxy wrapping an existing Container.
     * Used for vanilla containers and custom delegates.
     */
    public MKContainer(Container delegate) {
        this(delegate, null);
    }

    /**
     * Creates a proxy wrapping a specific region of a Container.
     * Index remapping: proxy index 0 maps to region.startIndex().
     */
    public MKContainer(Container delegate, @Nullable MKRegion region) {
        this.delegate = delegate;
        this.region = region;
    }

    // ── Delegate Access ──────────────────────────────────────────────────

    /** Returns the underlying vanilla Container. */
    public Container getDelegate() { return delegate; }

    /** Returns the region this proxy covers, or null if it covers the whole container. */
    public @Nullable MKRegion getRegion() { return region; }

    // ── Index Remapping ──────────────────────────────────────────────────
    // When wrapping a region, proxy indices are 0-based within the region.
    // toReal(0) maps to the region's startIndex in the delegate.

    /** Converts a proxy-local index to a real delegate index. */
    private int toReal(int proxyIndex) {
        if (region == null) return proxyIndex;
        return region.toContainerIndex(proxyIndex);
    }

    // ── Container Interface (delegated) ──────────────────────────────────

    @Override
    public int getContainerSize() {
        return region != null ? region.size() : delegate.getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < getContainerSize(); i++) {
            if (!getItem(i).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return delegate.getItem(toReal(slot));
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = delegate.removeItem(toReal(slot), amount);
        if (!removed.isEmpty()) this.setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return delegate.removeItemNoUpdate(toReal(slot));
        // No setChanged — "NoUpdate" variants intentionally skip notifications
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        delegate.setItem(toReal(slot), stack);
        // Trigger source sync so bound backing stores (bundle BundleContents,
        // shulker ItemContainerContents, ender chest) update immediately.
        // The syncing re-entrancy guard in setChanged() prevents infinite
        // recursion when sync() writes items back to this container.
        this.setChanged();
    }

    @Override
    public void setChanged() {
        delegate.setChanged();
        // Fire onChange callback
        if (onChange != null) onChange.run();
        // Sync to bound source with re-entrancy guard.
        // Sources that defer sync (e.g., shulker ItemContainerContents) skip
        // continuous sync here — they write back once on unbind() instead.
        if (source != null && !syncing && !source.defersSync()) {
            syncing = true;
            try {
                source.sync(this);
            } finally {
                syncing = false;
            }
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return delegate.stillValid(player);
    }

    @Override
    public void clearContent() {
        if (region != null) {
            // Only clear this region's indices
            for (int i = 0; i < region.size(); i++) {
                delegate.setItem(toReal(i), ItemStack.EMPTY);
            }
        } else {
            delegate.clearContent();
        }
    }

    // ── onChange Callback ─────────────────────────────────────────────────

    /** Sets a callback fired whenever any item in this container changes. */
    public MKContainer onChange(@Nullable Runnable onChange) {
        this.onChange = onChange;
        return this;
    }

    // ── Source Binding ────────────────────────────────────────────────────
    // Ephemeral containers can be bound to an external source (item contents,
    // live container, bundle) so changes sync bidirectionally.

    /**
     * Binds this container to an external source. Populates the container
     * from the source immediately. After binding, every change to this
     * container is automatically synced back to the source.
     */
    public void bind(MKContainerSource source) {
        this.source = source;
        syncing = true;
        try {
            source.populate(this);
        } finally {
            syncing = false;
        }
    }

    /**
     * Runs an action with source sync suppressed. Any {@code setItem} calls
     * during the action will update the container but NOT trigger
     * {@code source.sync()}. Used by {@code pollExternalChanges} to
     * re-populate from the backing store without the populate's setItem
     * calls writing intermediate state back to the store.
     */
    public void withSyncSuppressed(Runnable action) {
        syncing = true;
        try {
            action.run();
        } finally {
            syncing = false;
        }
    }

    /**
     * Unbinds from the current source. Performs a final sync to write any
     * pending changes back, then clears this container.
     */
    public void unbind() {
        if (source != null) {
            source.sync(this);
            source = null;
            clearContent();
        }
    }

    /** Returns true if this container is currently bound to an external source. */
    public boolean isBound() {
        return source != null;
    }

    /** Returns the current source, or null if not bound. */
    public @Nullable MKContainerSource getSource() {
        return source;
    }

    // ── NBT Persistence ──────────────────────────────────────────────────
    // Called from ServerPlayer save/load hooks (server thread, server registry).

    /**
     * Saves non-empty items as slot-indexed entries under the given key.
     * Uses proxy-local indices (0-based) regardless of region offset.
     */
    public void saveToNbt(String key, ValueOutput output) {
        ValueOutput.ValueOutputList list = output.childrenList(key);
        for (int i = 0; i < getContainerSize(); i++) {
            ItemStack stack = getItem(i);
            if (!stack.isEmpty()) {
                ValueOutput entry = list.addChild();
                entry.putInt("slot", i);
                entry.store("item", ItemStack.CODEC, stack.copy());
            }
        }
    }

    /**
     * Loads items from slot-indexed entries under the given key.
     * Clears existing items first. Uses proxy-local indices.
     */
    public void loadFromNbt(String key, ValueInput input) {
        clearContent();
        for (ValueInput entry : input.childrenListOrEmpty(key)) {
            Optional<Integer> slotOpt = entry.getInt("slot");
            if (slotOpt.isEmpty()) continue;
            int slot = slotOpt.get();
            if (slot < 0 || slot >= getContainerSize()) continue;

            entry.read("item", ItemStack.CODEC).ifPresent(stack -> {
                if (!stack.isEmpty()) {
                    setItem(slot, stack);
                }
            });
        }
    }
}
