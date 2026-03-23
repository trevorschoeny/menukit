package com.trevorschoeny.menukit;

import com.trevorschoeny.menukit.source.MKContainerSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * A vanilla-compatible item storage extending {@link SimpleContainer}.
 *
 * <p>Works exactly like vanilla's containers (chests, barrels, etc.):
 * <ul>
 *   <li>{@link net.minecraft.world.inventory.Slot}s backed by this container
 *       participate in vanilla's click handling, sync, and rendering natively.</li>
 *   <li>{@code broadcastChanges()} detects changes and syncs to the client
 *       via standard slot packets — no custom networking needed.</li>
 *   <li>Items are serialized with the server's registry and deserialized with
 *       the client's registry automatically — the enchantment Holder.Reference
 *       crash is impossible.</li>
 * </ul>
 *
 * <p>Adds two features on top of SimpleContainer:
 * <ol>
 *   <li><b>onChange callback</b> — notified whenever items change (e.g., trigger persistence)</li>
 *   <li><b>NBT persistence</b> — {@link #saveToNbt}/{@link #loadFromNbt} for player data save/load</li>
 * </ol>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * MKContainer storage = new MKContainer(2);
 * menu.addSlot(new MKSlot(storage, 0, x, y));
 * // Vanilla handles clicks, sync, rendering from here.
 * }</pre>
 *
 * <p>Part of the <b>MenuKit</b> API.
 */
public class MKContainer extends SimpleContainer {

    private @Nullable Runnable onChange;
    private @Nullable MKContainerSource source;
    private boolean syncing; // re-entrancy guard for source sync

    public MKContainer(int size) {
        super(size);
    }

    /** Called by SimpleContainer whenever items change. Forwards to onChange callback
     *  and syncs to bound source if present. */
    @Override
    public void setChanged() {
        super.setChanged();
        if (onChange != null) onChange.run();
        // Sync to bound source with re-entrancy guard — prevents infinite loops
        // if source.sync() triggers setChanged() on another container
        if (source != null && !syncing) {
            syncing = true;
            try {
                source.sync(this);
            } finally {
                syncing = false;
            }
        }
    }

    /** Sets a callback fired whenever any item in this container changes. */
    public MKContainer onChange(@Nullable Runnable onChange) {
        this.onChange = onChange;
        return this;
    }

    // ── Source Binding ─────────────────────────────────────────────────────
    // Ephemeral containers can be bound to an external source (item contents,
    // live container, bundle) so changes sync bidirectionally.

    /**
     * Binds this container to an external source. Populates the container
     * from the source immediately. After binding, every change to this
     * container is automatically synced back to the source.
     *
     * @param source the backing store to bind to
     */
    public void bind(MKContainerSource source) {
        this.source = source;
        // Populate without triggering sync-back (we're reading FROM the source)
        syncing = true;
        try {
            source.populate(this);
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

    // ── NBT Persistence ─────────────────────────────────────────────────────
    // Called from ServerPlayer save/load hooks (server thread, server registry).
    // ValueOutput/ValueInput carry the server's RegistryOps automatically.

    /**
     * Saves non-empty items as slot-indexed entries under the given key.
     *
     * <p>Each entry is a compound with "slot" (int) and "item" (ItemStack).
     * Empty slots are skipped — ItemStack.CODEC rejects air/empty stacks.
     * Slot indices are preserved so items load back to the correct positions.
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
     * Clears existing items first. Each entry has "slot" and "item" fields.
     * Items go into their original slot positions.
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
