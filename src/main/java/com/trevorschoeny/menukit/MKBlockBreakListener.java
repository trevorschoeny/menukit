package com.trevorschoeny.menukit;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;

/**
 * Listens for block break events and drops items from any instance-bound
 * MenuKit containers at the broken block's position.
 *
 * <p>Uses Fabric's {@link PlayerBlockBreakEvents#AFTER} event — fires
 * after the block is successfully broken (server-side only). Queries
 * {@link MKWorldData} for containers at that position, drops all items
 * as entities in the world, and removes the storage entries.
 *
 * <p>Part of the <b>MenuKit</b> framework internals.
 */
public class MKBlockBreakListener {

    /**
     * Registers the block break listener. Called from {@link MenuKit#init()}.
     */
    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(world instanceof ServerLevel level)) return;

            MKWorldData data = MKWorldData.get(level);
            var containers = data.removeContainersAt(pos);

            if (containers.isEmpty()) return;

            // Drop all items from all containers at this position
            for (var entry : containers.entrySet()) {
                MKContainer container = entry.getValue();
                for (int i = 0; i < container.getContainerSize(); i++) {
                    ItemStack stack = container.getItem(i);
                    if (!stack.isEmpty()) {
                        // Drop the item as an entity at the block's position
                        Containers.dropItemStack(
                                world,
                                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                stack);
                    }
                }
                MenuKit.LOGGER.info("[MenuKit] Dropped instance-bound container '{}' at {}",
                        entry.getKey(), pos);
            }
        });
    }
}
