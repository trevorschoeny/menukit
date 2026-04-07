package com.trevorschoeny.menukit.data;

import com.trevorschoeny.menukit.MenuKit;

import com.trevorschoeny.menukit.MKAccessors;

import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Extracts a {@link BlockPos} from a vanilla {@link AbstractContainerMenu}.
 * Used by MenuKit to resolve instance-bound containers to the correct block.
 *
 * <p>Two strategies, tried in order:
 * <ol>
 *   <li><b>Block-entity menus</b> (furnace, chest, barrel, etc.): The menu's
 *       slots are backed by the block entity's Container. We find the first
 *       slot whose container is a BlockEntity and return its position.</li>
 *   <li><b>Stateless-block menus</b> (enchanting, crafting, anvil, etc.): These
 *       use ContainerLevelAccess stored in a private field. We use accessor
 *       mixins (which handle name remapping safely) to read it.</li>
 * </ol>
 *
 * <p>Part of the <b>MenuKit</b> framework internals.
 */
public class MKBlockPosExtractor {

    /**
     * Attempts to extract a BlockPos from a menu.
     *
     * @param menu the container menu to extract from
     * @return the block position, or null if not extractable
     */
    public static @Nullable BlockPos fromMenu(AbstractContainerMenu menu) {
        // Strategy 1: Check if any slot's container is a BlockEntity.
        // Covers furnaces, chests, barrels, hoppers, dispensers, brewing stands, etc.
        for (var slot : menu.slots) {
            if (slot.container instanceof BlockEntity be) {
                return be.getBlockPos();
            }
        }

        // Strategy 2: Check for ContainerLevelAccess via accessor mixins.
        // Covers enchanting tables, crafting tables, anvils, grindstones,
        // looms, stonecutters, smithing tables, and cartography tables.
        if (menu instanceof MKAccessors.HasContainerLevelAccess accessor) {
            try {
                var access = accessor.menuKit$getAccess();
                Optional<BlockPos> pos = access.evaluate((level, blockPos) -> blockPos);
                if (pos.isPresent()) {
                    return pos.get();
                }
            } catch (Exception e) {
                MenuKit.LOGGER.debug("[MenuKit] ContainerLevelAccess extraction failed for {}",
                        menu.getClass().getSimpleName());
            }
        }

        return null;
    }
}
