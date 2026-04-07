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

            // Drop or retain items from all containers at this position.
            // Per-group break behavior determines whether items spill or are
            // retained in the dropped block's item NBT.
            for (var entry : containers.entrySet()) {
                String containerName = entry.getKey();
                MKContainer container = entry.getValue();

                // Check if this container's slot group has retained-on-break behavior
                MKSlotGroupDef groupDef = MenuKit.getSlotGroupDef(containerName);
                if (groupDef != null && groupDef.breakBehavior() == MKSlotGroupDef.BreakBehavior.RETAINED_ON_BREAK) {
                    // Retained-on-break: items transfer to the dropped item's NBT.
                    // This is a generalization of shulker box behavior.
                    // TODO: Implement NBT transfer to dropped block item.
                    // For now, log and skip — items will be lost. This is a
                    // placeholder for future implementation per SPEC-REFACTOR.md.
                    MenuKit.LOGGER.info("[MenuKit] Container '{}' at {} has retained-on-break — NBT transfer not yet implemented",
                            containerName, pos);
                    continue;
                }

                // Default: drop items into the world (chest behavior)
                for (int i = 0; i < container.getContainerSize(); i++) {
                    ItemStack stack = container.getItem(i);
                    if (!stack.isEmpty()) {
                        Containers.dropItemStack(
                                world,
                                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                stack);
                    }
                }
                MenuKit.LOGGER.info("[MenuKit] Dropped instance-bound container '{}' at {}",
                        containerName, pos);
            }
        });
    }
}
