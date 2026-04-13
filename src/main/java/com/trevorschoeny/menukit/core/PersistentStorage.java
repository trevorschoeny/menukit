package com.trevorschoeny.menukit.core;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A {@link Storage} that can serialize and deserialize its contents.
 *
 * <p>Implementations like {@link PlayerStorage} and {@link BlockEntityStorage}
 * need save/load. Implementations like {@link EphemeralStorage} and
 * {@link VirtualStorage} do not — they implement plain {@link Storage}.
 *
 * <p>This split keeps the base interface narrow. A consumer writing a
 * {@link VirtualStorage} is not forced to implement serialization it
 * doesn't need.
 */
public interface PersistentStorage extends Storage {

    /**
     * Serializes this storage's contents into the given output.
     * Items are written as a child list under the "Items" key.
     */
    void save(ValueOutput output);

    /**
     * Deserializes this storage's contents from the given input.
     * Expects the format written by {@link #save(ValueOutput)}.
     */
    void load(ValueInput input);
}
