package com.trevorschoeny.menukit.data;

import com.trevorschoeny.menukit.MenuKit;

import com.trevorschoeny.menukit.container.MKContainer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;
import java.util.stream.Collectors;

/**
 * World-level persistence for instance-bound MenuKit containers.
 *
 * <p>Stores container data keyed by block position + container name.
 * Each block can have multiple containers (from different panels).
 * Data is saved to {@code <world>/data/trevormod_menukit.dat} and
 * persists independently of chunk load state.
 *
 * <p>Uses 1.21.11's Codec-based SavedData system — serialization is
 * handled by the CODEC, not by manual CompoundTag read/write.
 *
 * <p>Part of the <b>MenuKit</b> framework internals.
 */
public class MKWorldData extends SavedData {

    // ── Serialization records ───────────────────────────────────────────────
    // These exist solely for Codec serialization — the live data uses MKContainer.

    /** One item in a slot: index + stack. */
    private record SlotEntry(int slot, ItemStack item) {
        static final Codec<SlotEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.INT.fieldOf("slot").forGetter(SlotEntry::slot),
                ItemStack.CODEC.fieldOf("item").forGetter(SlotEntry::item)
        ).apply(inst, SlotEntry::new));
    }

    /** One container entry: key (pos|name), size, and items. */
    private record ContainerEntry(String key, int size, List<SlotEntry> items) {
        static final Codec<ContainerEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.STRING.fieldOf("key").forGetter(ContainerEntry::key),
                Codec.INT.fieldOf("size").forGetter(ContainerEntry::size),
                SlotEntry.CODEC.listOf().fieldOf("items").forGetter(ContainerEntry::items)
        ).apply(inst, ContainerEntry::new));
    }

    /** The full world data: a list of container entries. */
    private record WorldDataState(List<ContainerEntry> containers) {
        static final Codec<WorldDataState> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                ContainerEntry.CODEC.listOf().fieldOf("containers").forGetter(WorldDataState::containers)
        ).apply(inst, WorldDataState::new));
    }

    // ── Codec that bridges SavedData lifecycle ──────────────────────────────

    private static final Codec<MKWorldData> CODEC = WorldDataState.CODEC.xmap(
            // Decode: WorldDataState → MKWorldData
            state -> {
                MKWorldData data = new MKWorldData();
                for (ContainerEntry entry : state.containers()) {
                    MKContainer container = new MKContainer(entry.size());
                    for (SlotEntry se : entry.items()) {
                        if (se.slot() >= 0 && se.slot() < entry.size() && !se.item().isEmpty()) {
                            container.setItem(se.slot(), se.item().copy());
                        }
                    }
                    container.onChange(data::setDirty);
                    data.containers.put(entry.key(), container);
                }
                MenuKit.LOGGER.info("[MenuKit] Loaded {} instance-bound containers from world data",
                        data.containers.size());
                return data;
            },
            // Encode: MKWorldData → WorldDataState
            worldData -> {
                List<ContainerEntry> entries = new ArrayList<>();
                for (var entry : worldData.containers.entrySet()) {
                    MKContainer container = entry.getValue();
                    List<SlotEntry> items = new ArrayList<>();
                    for (int i = 0; i < container.getContainerSize(); i++) {
                        ItemStack stack = container.getItem(i);
                        if (!stack.isEmpty()) {
                            items.add(new SlotEntry(i, stack.copy()));
                        }
                    }
                    entries.add(new ContainerEntry(entry.getKey(), container.getContainerSize(), items));
                }
                return new WorldDataState(entries);
            }
    );

    private static final SavedDataType<MKWorldData> TYPE = new SavedDataType<>(
            "trevormod_menukit",
            MKWorldData::new,
            CODEC,
            null  // no DataFixTypes needed
    );

    // ── Live data ───────────────────────────────────────────────────────────

    /** Key: "x,y,z|containerName" → MKContainer */
    private final Map<String, MKContainer> containers = new HashMap<>();

    public MKWorldData() {}

    /** Gets the MKWorldData for a server level, creating it if needed. */
    public static MKWorldData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * Gets or creates an instance-bound container at the given position.
     * Attaches an onChange listener that marks this SavedData as dirty
     * so it gets saved on the next autosave cycle.
     */
    public MKContainer getOrCreate(String containerName, BlockPos pos, int size) {
        String key = makeKey(pos, containerName);
        return containers.computeIfAbsent(key, k -> {
            MKContainer c = new MKContainer(size);
            c.onChange(this::setDirty);
            return c;
        });
    }

    /** Returns all containers at a given block position. */
    public Map<String, MKContainer> getContainersAt(BlockPos pos) {
        Map<String, MKContainer> result = new HashMap<>();
        String prefix = pos.getX() + "," + pos.getY() + "," + pos.getZ() + "|";
        for (var entry : containers.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                String containerName = entry.getKey().substring(prefix.length());
                result.put(containerName, entry.getValue());
            }
        }
        return result;
    }

    /** Removes all containers at a given block position. Returns the removed containers. */
    public Map<String, MKContainer> removeContainersAt(BlockPos pos) {
        Map<String, MKContainer> removed = getContainersAt(pos);
        String prefix = pos.getX() + "," + pos.getY() + "," + pos.getZ() + "|";
        containers.keySet().removeIf(key -> key.startsWith(prefix));
        if (!removed.isEmpty()) setDirty();
        return removed;
    }

    private static String makeKey(BlockPos pos, String containerName) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ() + "|" + containerName;
    }
}
