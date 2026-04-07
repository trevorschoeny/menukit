package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.widget.MKSlotState;
import com.trevorschoeny.menukit.widget.MKSlotStateRegistry;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Prevents vanilla's {@code moveItemStackTo} from placing items into
 * sort-locked slots.
 *
 * <p>{@code moveItemStackTo} is the core item-transfer method used by
 * vanilla's {@code quickMoveStack} implementations (shift-click routing).
 * It iterates a slot range and calls {@code slot.mayPlace(stack)} to decide
 * whether each slot accepts the item. We redirect that call to also reject
 * sort-locked target slots.
 *
 * <p>This is separate from the {@code mayPlace} hook in {@code MKSlotMixin}
 * because sort-lock should only block automated transfers (sorting,
 * shift-click routing), NOT direct manual placement (player clicking an
 * item into the slot). {@code MKSlotMixin.mayPlace} is called for ALL
 * placement operations; this redirect only affects {@code moveItemStackTo}.
 *
 * <p>Part of the <b>MenuKit</b> framework (mixin layer).
 */
@Mixin(AbstractContainerMenu.class)
public class MKMoveItemSortLockMixin {

    /**
     * Redirects the {@code slot.mayPlace(stack)} call inside
     * {@code moveItemStackTo} to also check sort-lock state.
     *
     * <p>If the target slot is sort-locked, returns false so
     * {@code moveItemStackTo} skips it — the item stays in the
     * source slot or goes to the next eligible slot.
     */
    @Redirect(
        method = "moveItemStackTo",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;mayPlace(Lnet/minecraft/world/item/ItemStack;)Z")
    )
    private boolean menuKit$skipSortLockedInTransfer(Slot slot, ItemStack stack) {
        // Check sort-lock BEFORE delegating to vanilla's mayPlace.
        // Sort-locked slots should not receive items via automated transfer.
        MKSlotState state = MKSlotStateRegistry.get(slot);
        if (state != null && state.isSortLocked()) {
            return false;
        }

        // Delegate to the original mayPlace (which may also have MKSlotMixin
        // hooks for locked slots, filters, disabled state, etc.)
        return slot.mayPlace(stack);
    }
}
