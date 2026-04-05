package com.trevorschoeny.menukit;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import com.trevorschoeny.menukit.source.MKContainerSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOGGER = LoggerFactory.getLogger("menukit");

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

    /**
     * Moves items from {@code source} that match item types present in
     * {@code destRegion} directly into the destination region's slots.
     * Unlike {@link #moveMatching}, this does NOT use {@code quickMoveStack} —
     * it inserts items slot-by-slot into the target region, ensuring they
     * land in the intended container even when multiple containers share
     * the same group (e.g., chest + peek open simultaneously).
     *
     * <p>The destination group is still used for item type scanning (what
     * types to match), but the actual transfer targets only {@code destRegion}.
     *
     * @param menu       the player's open container menu
     * @param player     the player performing the move
     * @param source     the source region group (items move FROM here)
     * @param dest       the destination region group (item types scanned HERE)
     * @param destRegion the specific region to move items INTO
     * @return the number of items transferred
     */
    public static int moveMatchingDirect(AbstractContainerMenu menu, Player player,
                                          MKRegionGroup source, MKRegionGroup dest,
                                          MKRegion destRegion) {
        LOGGER.warn("[MKMoveMatching] moveMatchingDirect called: destRegion={} sourceRegions={}",
                destRegion.name(), source.regions().size());

        // Step 1: Collect unique item types present in the target region only
        // (not the whole group — we want to match what's in THIS container)
        Set<Item> destItemTypes = new HashSet<>();
        for (int i = 0; i < destRegion.size(); i++) {
            ItemStack stack = destRegion.getItem(i);
            if (!stack.isEmpty()) {
                destItemTypes.add(stack.getItem());
            }
        }

        if (destItemTypes.isEmpty()) return 0;

        // Step 2: Iterate source and transfer matching items directly
        // into the destination region's slots (no quickMoveStack).
        // Skip only the target region itself — other regions in the same
        // group (e.g., chest when targeting peek) are valid sources.
        int movedCount = 0;

        int destStart = destRegion.getMenuSlotStart();
        int destEnd = destRegion.getMenuSlotEnd();

        for (MKRegion region : source.regions()) {
            // Skip the target region itself
            if (region.name().equals(destRegion.name())) continue;

            int start = region.getMenuSlotStart();
            int end = region.getMenuSlotEnd();

            for (int menuSlot = end; menuSlot >= start; menuSlot--) {
                if (menuSlot < 0 || menuSlot >= menu.slots.size()) continue;
                // Skip slots that fall within the target region
                if (menuSlot >= destStart && menuSlot <= destEnd) continue;

                Slot slot = menu.slots.get(menuSlot);
                ItemStack stack = slot.getItem();
                if (stack.isEmpty()) continue;
                if (!destItemTypes.contains(stack.getItem())) continue;

                MKSlotState slotState = MKSlotStateRegistry.get(slot);
                if (slotState != null && (slotState.isLocked() || slotState.isSortLocked())) continue;

                // Direct transfer: try to insert into the destination region
                int transferred = insertIntoRegion(menu, destRegion, stack);
                if (transferred > 0) {
                    // Shrink the source by the amount transferred
                    stack.shrink(transferred);
                    slot.setChanged();
                    movedCount += transferred;
                }
            }
        }

        return movedCount;
    }

    /**
     * Inserts as much of {@code stack} as possible into the given region's slots.
     * First tries to merge into existing stacks of the same type, then fills
     * empty slots. Returns the number of items inserted (stack is NOT modified).
     *
     * <p>Respects source-level capacity limits (e.g., bundle weight). If the
     * region's container is an {@link MKContainer} with a bound source, the
     * source's {@code getMaxAcceptCount} caps total insertion so items beyond
     * the bundle's weight limit stay in the source instead of being lost.
     */
    private static int insertIntoRegion(AbstractContainerMenu menu, MKRegion region,
                                         ItemStack stack) {
        int remaining = stack.getCount();
        int startSlot = region.getMenuSlotStart();
        int endSlot = region.getMenuSlotEnd();

        // Check if the destination has a capacity-limited source (e.g., bundles).
        // If so, cap insertion to what the source can actually accept.
        LOGGER.warn("[MKMoveMatching] insertIntoRegion: container class={} isMKContainer={}",
                region.container().getClass().getSimpleName(),
                region.container() instanceof MKContainer);
        if (region.container() instanceof MKContainer mkc) {
            LOGGER.warn("[MKMoveMatching]   isBound={} source={}",
                    mkc.isBound(),
                    mkc.getSource() != null ? mkc.getSource().getClass().getSimpleName() : "null");
            if (mkc.isBound()) {
                MKContainerSource source = mkc.getSource();
                int maxAccept = source.getMaxAcceptCount(0, stack);
                LOGGER.warn("[MKMoveMatching]   maxAccept={} remaining={}", maxAccept, remaining);
                if (maxAccept <= 0) return 0;
                remaining = Math.min(remaining, maxAccept);
            }
        }

        int budget = remaining;

        // Pass 1: merge into existing stacks
        for (int i = startSlot; i <= endSlot && remaining > 0; i++) {
            if (i < 0 || i >= menu.slots.size()) continue;
            Slot destSlot = menu.slots.get(i);
            ItemStack destStack = destSlot.getItem();

            if (!destStack.isEmpty()
                    && ItemStack.isSameItemSameComponents(stack, destStack)) {
                int space = destStack.getMaxStackSize() - destStack.getCount();
                if (space > 0) {
                    int toMove = Math.min(remaining, space);
                    destStack.grow(toMove);
                    destSlot.setChanged();
                    remaining -= toMove;
                }
            }
        }

        // Pass 2: fill empty slots
        for (int i = startSlot; i <= endSlot && remaining > 0; i++) {
            if (i < 0 || i >= menu.slots.size()) continue;
            Slot destSlot = menu.slots.get(i);
            if (!destSlot.getItem().isEmpty()) continue;

            int toMove = Math.min(remaining, stack.getMaxStackSize());
            ItemStack newStack = stack.copyWithCount(toMove);
            destSlot.set(newStack);
            remaining -= toMove;
        }

        return budget - remaining;
    }
}
