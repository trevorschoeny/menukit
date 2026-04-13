package com.trevorschoeny.menukit.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.function.Supplier;

/**
 * Storage tied to a block position. Resolves the block entity on each
 * access rather than caching the reference, which is safer across chunk
 * unloads and block replacements.
 *
 * <p>Implements {@link PersistentStorage} because block-entity-bound
 * containers need save/load for the world's SavedData.
 *
 * <p>Replaces the old {@code MKContainerDef.BindingType.INSTANCE} +
 * {@code MKWorldData} pattern.
 */
public class BlockEntityStorage implements PersistentStorage {

    private final int size;
    private final BlockPos pos;
    private final Supplier<Level> levelSupplier;
    private final ItemStack[] fallback; // used when block entity is unavailable

    /**
     * @param size          number of slots
     * @param pos           the block position to resolve
     * @param levelSupplier supplier for the current level (avoids holding a Level reference)
     */
    public BlockEntityStorage(int size, BlockPos pos, Supplier<Level> levelSupplier) {
        this.size = size;
        this.pos = pos;
        this.levelSupplier = levelSupplier;
        this.fallback = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            fallback[i] = ItemStack.EMPTY;
        }
    }

    /** Factory method for readability. */
    public static BlockEntityStorage of(int size, BlockPos pos, Supplier<Level> levelSupplier) {
        return new BlockEntityStorage(size, pos, levelSupplier);
    }

    /** Returns the block position this storage is bound to. */
    public BlockPos getPos() {
        return pos;
    }

    @Override
    public ItemStack getStack(int localIndex) {
        // Fallback storage — block entity resolution happens at the handler
        // level during binding. Direct block entity access will be wired
        // in Phase 3 when the handler owns the binding lifecycle.
        return fallback[localIndex];
    }

    @Override
    public void setStack(int localIndex, ItemStack stack) {
        fallback[localIndex] = stack;
        markDirty();
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public void markDirty() {
        // Phase 3: will mark the block entity dirty via the level
    }

    @Override
    public void save(ValueOutput output) {
        ValueOutput.ValueOutputList list = output.childrenList("Items");
        for (int i = 0; i < size; i++) {
            if (!fallback[i].isEmpty()) {
                ValueOutput entry = list.addChild();
                entry.putInt("Slot", i);
                entry.store("Item", ItemStack.CODEC, fallback[i].copy());
            }
        }
        output.putInt("Size", size);
        output.putInt("X", pos.getX());
        output.putInt("Y", pos.getY());
        output.putInt("Z", pos.getZ());
    }

    @Override
    public void load(ValueInput input) {
        for (int i = 0; i < size; i++) {
            fallback[i] = ItemStack.EMPTY;
        }
        for (ValueInput entry : input.childrenListOrEmpty("Items")) {
            int slot = entry.getInt("Slot").orElse(-1);
            if (slot >= 0 && slot < size) {
                entry.read("Item", ItemStack.CODEC).ifPresent(stack -> {
                    if (!stack.isEmpty()) {
                        fallback[slot] = stack;
                    }
                });
            }
        }
    }
}
