package com.trevorschoeny.menukit;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

/**
 * Moves items from a source region group into a destination region group,
 * but ONLY item types that already exist in the destination.
 *
 * <p>Use case: you open a chest that has some diamonds. You click "Move Matching"
 * and all diamonds from your inventory transfer into the chest. Items the chest
 * doesn't already contain stay in your inventory.
 *
 * <p>All logic is server-side. Uses {@code quickMoveStack} (vanilla's shift-click)
 * for each matching slot, so routing respects the menu's existing transfer rules.
 *
 * <p>Locked slots (via {@link MKSlotState}) are skipped -- items the player has
 * explicitly pinned in place are never moved.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class MKMoveMatching {

    /**
     * Moves all items from {@code source} that match item types present in
     * {@code dest}. Uses the menu's {@code quickMoveStack} for each transfer,
     * so vanilla's routing logic decides where items actually land.
     *
     * @param menu   the player's open container menu
     * @param player the player performing the move
     * @param source the source region group (items move FROM here)
     * @param dest   the destination region group (item types are scanned HERE)
     * @return the number of slots that were shift-clicked (not necessarily
     *         the number of items -- a partial move still counts as 1)
     */
    public static int moveMatching(AbstractContainerMenu menu, Player player,
                                    MKRegionGroup source, MKRegionGroup dest) {
        // Step 1: Collect unique item types present in the destination.
        // We use Item identity (not full component matching) so that e.g.
        // differently-enchanted picks of the same base item all match.
        // This matches the user's mental model: "the chest has diamonds,
        // move my diamonds into it."
        Set<Item> destItemTypes = new HashSet<>();
        for (MKRegion region : dest.regions()) {
            for (int i = 0; i < region.size(); i++) {
                ItemStack stack = region.getItem(i);
                if (!stack.isEmpty()) {
                    destItemTypes.add(stack.getItem());
                }
            }
        }

        // Nothing in the destination -- nothing to match against
        if (destItemTypes.isEmpty()) return 0;

        // Step 2: Iterate source regions and shift-click every matching slot.
        // We iterate backwards within each region so that items shifting down
        // (due to slot compaction) don't cause us to skip slots -- same
        // pattern as the bulk move handler in InventoryPlus.
        //
        // CRITICAL: Skip source slots that fall within any destination region.
        // Source and dest groups may overlap (e.g., player_all and player_storage
        // both contain mk:main_inventory). Shift-clicking a slot that is ALSO in
        // the destination would move items OUT of the destination via vanilla's
        // routing, which is the opposite of the intended direction. This caused
        // a critical item-loss bug where all inventory items were moved to the
        // chest instead of matching items being moved INTO the inventory.
        int movedCount = 0;

        for (MKRegion region : source.regions()) {
            // Skip this entire source region if it's also in the destination.
            // Moving items from a region that IS the destination would send
            // them in the wrong direction via quickMoveStack.
            if (dest.contains(region.name())) continue;

            int start = region.getMenuSlotStart();
            int end = region.getMenuSlotEnd();

            // Iterate backwards to avoid skipping slots when items shift
            for (int menuSlot = end; menuSlot >= start; menuSlot--) {
                // Bounds check -- defensive against dynamic slot removal
                if (menuSlot < 0 || menuSlot >= menu.slots.size()) continue;

                // Double-check: skip any slot that falls within a dest region,
                // even if the region name wasn't in dest (defensive against
                // partial overlaps or unnamed regions).
                if (dest.containsMenuSlot(menuSlot)) continue;

                Slot slot = menu.slots.get(menuSlot);
                ItemStack stack = slot.getItem();

                // Skip empty slots
                if (stack.isEmpty()) continue;

                // Only move items whose type exists in the destination
                if (!destItemTypes.contains(stack.getItem())) continue;

                // Respect locked and sort-locked slots -- never move pinned items
                MKSlotState slotState = MKSlotStateRegistry.get(slot);
                if (slotState != null && (slotState.isLocked() || slotState.isSortLocked())) continue;

                // quickMoveStack is vanilla's shift-click. It routes the item
                // to the correct target based on the menu's transfer rules.
                // The remainder (if any) stays in the source slot.
                menu.quickMoveStack(player, menuSlot);
                movedCount++;
            }
        }

        return movedCount;
    }
}
