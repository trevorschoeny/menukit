package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.MKContext;
import com.trevorschoeny.menukit.MKSlot;
import com.trevorschoeny.menukit.MenuKit;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * MenuKit internal mixin — injects MKSlots into menus during construction.
 *
 * <p>Targets {@link InventoryMenu} specifically because its constructor provides
 * the {@link Player} reference needed for per-player container management.
 * Other menu types can be added with additional injection points.
 *
 * <p>Runs on BOTH client and server — slot counts must match for network sync.
 * Uses SURVIVAL_INVENTORY context since InventoryMenu is always constructed
 * with survival dimensions. Creative mode re-positions slots separately via
 * MKCreativeMixin.
 *
 * <p>Part of the <b>MenuKit</b> framework internals. Users never see this.
 */
@Mixin(InventoryMenu.class)
public class MKMenuMixin {


    @Inject(method = "<init>", at = @At("TAIL"))
    private void menuKit$createSlots(Inventory inventory, boolean active,
                                      Player owner, CallbackInfo ci) {
        // Ask MenuKit for all slots registered to InventoryMenu via SURVIVAL_INVENTORY context.
        // Not creative context here: InventoryMenu constructor runs for both modes,
        // and creative mode fixes positions later via MKCreativeMixin.
        java.util.List<MKSlot> mkSlots = MenuKit.createSlotsForMenu(
                (net.minecraft.world.inventory.AbstractContainerMenu)(Object) this,
                MKContext.SURVIVAL_INVENTORY, owner);

        for (MKSlot slot : mkSlots) {
            ((AbstractContainerMenuInvoker) this).trevorMod$addSlot(slot);
        }

        // DEBUG: log slot count + side
        AbstractContainerMenu menu = (AbstractContainerMenu)(Object) this;
        String side = (owner instanceof net.minecraft.server.level.ServerPlayer) ? "SERVER" : "CLIENT";
        com.trevorschoeny.menukit.MenuKit.LOGGER.info(
            "[MenuKit] MKMenuMixin {} added {} MKSlots, total menu slots={}",
            side, mkSlots.size(), menu.slots.size());
    }

    /**
     * Intercepts shift-click (quick-move) to route items to MKSlots.
     *
     * <p>When a player shift-clicks an item from a vanilla slot, vanilla's
     * {@code quickMoveStack} doesn't know about MKSlots. This injection
     * checks if any MKSlot accepts the item (via its filter) and routes
     * there first. If no MKSlot accepts it, vanilla handles it normally.
     *
     * <p>When shift-clicking FROM an MKSlot, routes the item to the
     * main inventory (slots 9-45), matching vanilla's armor slot behavior.
     */
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void menuKit$quickMoveToMKSlots(Player player, int slotIndex,
                                             CallbackInfoReturnable<ItemStack> cir) {
        AbstractContainerMenu menu = (AbstractContainerMenu)(Object) this;
        Slot sourceSlot = menu.slots.get(slotIndex);

        if (!sourceSlot.hasItem()) return;

        ItemStack sourceStack = sourceSlot.getItem();

        // ── Case 1: Shift-click FROM an MKSlot → move to main inventory ──
        if (sourceSlot instanceof MKSlot) {
            ItemStack original = sourceStack.copy();
            // Try to move to main inventory (9-45 covers inventory + hotbar)
            if (!((AbstractContainerMenuInvoker) this).trevorMod$moveItemStackTo(sourceStack, 9, 45, false)) {
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
            sourceSlot.setByPlayer(sourceStack, original);
            sourceSlot.setChanged();
            cir.setReturnValue(original);
            return;
        }

        // ── Case 2: Shift-click FROM a vanilla slot → try MKSlots first ──
        // Only try MKSlots if the item would match a filter.
        // Iterate all slots looking for MKSlots that accept this item.
        for (Slot targetSlot : menu.slots) {
            if (!(targetSlot instanceof MKSlot mkSlot)) continue;
            if (!mkSlot.isActive()) continue;
            if (!mkSlot.mayPlace(sourceStack)) continue;

            // Found a matching MKSlot — try to move the item there
            ItemStack original = sourceStack.copy();
            ItemStack targetItem = mkSlot.getItem();

            if (targetItem.isEmpty()) {
                // Empty slot — place up to max stack size
                int toPlace = Math.min(sourceStack.getCount(), mkSlot.getMaxStackSize());
                mkSlot.setByPlayer(sourceStack.split(toPlace), original);
                mkSlot.setChanged();
                sourceSlot.setChanged();
                cir.setReturnValue(original);
                return;
            } else if (ItemStack.isSameItemSameComponents(sourceStack, targetItem)
                    && targetItem.getCount() < mkSlot.getMaxStackSize()) {
                // Same item, not full — add to existing stack
                int space = mkSlot.getMaxStackSize() - targetItem.getCount();
                int toAdd = Math.min(sourceStack.getCount(), space);
                sourceStack.shrink(toAdd);
                targetItem.grow(toAdd);
                mkSlot.setChanged();
                sourceSlot.setChanged();
                cir.setReturnValue(original);
                return;
            }
        }

        // No MKSlot accepted the item — let vanilla handle it normally
    }
}
