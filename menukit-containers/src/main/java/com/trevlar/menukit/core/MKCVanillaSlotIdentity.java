package com.trevlar.menukit.core;

import com.trevlar.menukit.state.SlotStateRegistry;
import com.trevlar.menukit.window.VanillaSlotIdentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;

import java.util.Optional;

/**
 * MKC's §0050-backed implementation of MK's {@link VanillaSlotIdentity} port —
 * the menu-independent identity of a vanilla slot. Delegates to §0050's slot-aware
 * resolution ({@code SlotStateRegistry.resolve}), which already handles composite
 * double chests (the slot resolves to its owning half) and is side-agnostic for
 * block entities ({@code pos+dimension}). Registered via
 * {@code ServerTier.installIdentity(...)} at MKC init.
 *
 * <p>Containers §0050 cannot identify from the container alone (ender chest,
 * horse-family bag) return empty, so those slots fall back to menu-based
 * addressing and gracefully do not gate — a notable edge.
 */
public final class MKCVanillaSlotIdentity implements VanillaSlotIdentity {

    public static final MKCVanillaSlotIdentity INSTANCE = new MKCVanillaSlotIdentity();

    private MKCVanillaSlotIdentity() {}

    @Override
    public Optional<Resolved> identify(Container container, int containerSlotIndex) {
        return SlotStateRegistry.resolve(container, containerSlotIndex)
                .map(rs -> new Resolved(stableId(rs.key()), rs.localSlotIndex()));
    }

    // Menu-free seams (world pickup, hoppers) mint an address per slot per call, and
    // the id is the same string every time for the same container: intern it by key.
    // Modded keys hold a mutable tag, so they are never cached.
    // ponytail: cleared wholesale past MAX_IDS; bounded per-key eviction if it ever matters.
    private static final int MAX_IDS = 4096;
    private static final java.util.Map<PersistentContainerKey, String> IDS =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** A deterministic, side-stable string id for a persistent container key. */
    private static String stableId(PersistentContainerKey key) {
        if (key instanceof PersistentContainerKey.Modded) return buildId(key);
        String id = IDS.get(key);
        if (id != null) return id;
        if (IDS.size() >= MAX_IDS) IDS.clear();
        id = buildId(key);
        IDS.put(key, id);
        return id;
    }

    private static String buildId(PersistentContainerKey key) {
        return switch (key) {
            case PersistentContainerKey.PlayerInventory p -> "player:" + p.playerId();
            case PersistentContainerKey.EnderChest e -> "ender:" + e.playerId();
            case PersistentContainerKey.BlockEntityKey b -> {
                BlockPos pos = b.pos();
                yield "be:" + b.dimension() + ":"
                        + pos.getX() + "," + pos.getY() + "," + pos.getZ();
            }
            case PersistentContainerKey.EntityKey en -> "entity:" + en.entityId();
            case PersistentContainerKey.Modded m -> "modded:" + m.resolverId() + ":" + m.payload();
        };
    }
}
