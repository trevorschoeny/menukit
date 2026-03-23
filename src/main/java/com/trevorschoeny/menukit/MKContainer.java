package com.trevorschoeny.menukit;

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

    public MKContainer(int size) {
        super(size);
        // DEBUG: log identity so we can match containers across operations
        MenuKit.LOGGER.info(
            "[MKContainer] CREATED size={} id={}", size, System.identityHashCode(this));
    }

    @Override
    public void setItem(int slot, net.minecraft.world.item.ItemStack stack) {
        net.minecraft.world.item.ItemStack old = slot < getContainerSize() ? getItem(slot) : net.minecraft.world.item.ItemStack.EMPTY;
        // Log when items are CLEARED (non-empty → empty) to catch the persistence bug
        if (stack.isEmpty() && !old.isEmpty()) {
            MenuKit.LOGGER.warn(
                "[MKContainer] CLEARING slot={} was={} id={} thread={}",
                slot, old, System.identityHashCode(this), Thread.currentThread().getName());
            Thread.dumpStack();  // print stack trace to find WHO is clearing it
        } else if (!stack.isEmpty()) {
            MenuKit.LOGGER.info(
                "[MKContainer] setItem slot={} item={} id={}",
                slot, stack, System.identityHashCode(this));
        }
        super.setItem(slot, stack);
    }

    /** Called by SimpleContainer whenever items change. We forward to our callback. */
    @Override
    public void setChanged() {
        super.setChanged();
        if (onChange != null) onChange.run();
    }

    /** Sets a callback fired whenever any item in this container changes. */
    public MKContainer onChange(@Nullable Runnable onChange) {
        this.onChange = onChange;
        return this;
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
