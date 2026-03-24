package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.MKContext;
import com.trevorschoeny.menukit.MKSlot;
import com.trevorschoeny.menukit.MenuKit;
import net.minecraft.world.Container;
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
 * MenuKit internal mixin — adds MKSlots to ANY container menu and handles
 * directional shift-click routing for all menu types.
 *
 * <p><b>Slot injection:</b> Targets {@code addStandardInventorySlots()} which
 * covers virtually every vanilla container with player inventory: chests,
 * furnaces, crafting tables, anvils, etc. MKSlots are added AFTER vanilla
 * finishes adding its own slots, ensuring slot indices are consistent.
 *
 * <p>InventoryMenu is SKIPPED for slot injection — it's handled separately by
 * {@link MKMenuMixin} because it has unique constructor parameters and
 * creative mode handling.
 *
 * <p><b>Shift-click:</b> Intercepts {@code quickMoveStack()} on ALL menu types
 * (including InventoryMenu) to enforce directional shift-click flags. MKSlots
 * only participate in shift-click routing when their panel has the appropriate
 * {@code shiftClickIn} or {@code shiftClickOut} flag set and the panel is visible.
 *
 * <p>Part of the <b>MenuKit</b> framework internals. Users never see this.
 */
@Mixin(AbstractContainerMenu.class)
public class MKGenericMenuMixin {

    // ── Slot Injection ───────────────────────────────────────────────────────

    @Inject(method = "addStandardInventorySlots", at = @At("TAIL"))
    private void menuKit$addSlotsToAnyMenu(Container container, int x, int y, CallbackInfo ci) {
        AbstractContainerMenu menu = (AbstractContainerMenu)(Object) this;

        // Skip InventoryMenu — handled by MKMenuMixin with creative mode support
        if (menu instanceof InventoryMenu) return;

        // Need a Player reference for per-player container management
        if (!(container instanceof Inventory inventory)) return;

        // Find a default context for this menu class
        MKContext context = MKContext.defaultForMenuClass(menu.getClass());
        if (context == null) return;

        // Check if any panels are registered for this context
        if (!MenuKit.hasPanelsForContext(context)) return;

        // Create and add MKSlots
        java.util.List<MKSlot> mkSlots = MenuKit.createSlotsForMenu(menu, context, inventory.player);
        for (MKSlot slot : mkSlots) {
            ((AbstractContainerMenuInvoker) menu).trevorMod$addSlot(slot);
        }
    }

    // ── Directional Shift-Click Routing ──────────────────────────────────────

    /**
     * Intercepts shift-click (quick-move) on ALL menu types to enforce
     * directional shift-click flags on MKSlots.
     *
     * <p><b>From MKSlot:</b> Only allows shift-click out if the panel has
     * {@code shiftClickOut=true}. Routes items to vanilla inventory range.
     *
     * <p><b>From vanilla slot:</b> Tries MKSlots with {@code shiftClickIn=true}
     * first (partial stacks, then empty slots). Falls through to vanilla if
     * no MKSlot accepts the item.
     *
     * <p>This runs on ALL menus (InventoryMenu, ChestMenu, CraftingMenu, etc.)
     * ensuring consistent behavior regardless of which screen is open.
     */
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void menuKit$directionalShiftClick(Player player, int slotIndex,
                                                CallbackInfoReturnable<ItemStack> cir) {
        AbstractContainerMenu menu = (AbstractContainerMenu)(Object) this;

        if (slotIndex < 0 || slotIndex >= menu.slots.size()) return;
        Slot sourceSlot = menu.slots.get(slotIndex);
        if (!sourceSlot.hasItem()) return;

        ItemStack sourceStack = sourceSlot.getItem();

        // ── Case 1: Shift-click FROM an MKSlot ──────────────────────────────
        // Block the move if shiftClickOut is false. If allowed, route to
        // vanilla inventory slots (find the range by scanning for non-MK slots).
        if (sourceSlot instanceof MKSlot mkSource) {
            if (!mkSource.canShiftClickOut()) {
                // Block — this panel doesn't allow shift-clicking out
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }

            // Find vanilla inventory slot range (non-MKSlots)
            int vanillaStart = -1, vanillaEnd = -1;
            for (int i = 0; i < menu.slots.size(); i++) {
                if (!(menu.slots.get(i) instanceof MKSlot)) {
                    if (vanillaStart < 0) vanillaStart = i;
                    vanillaEnd = i + 1;
                }
            }
            if (vanillaStart < 0) {
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }

            ItemStack original = sourceStack.copy();
            if (!((AbstractContainerMenuInvoker) menu).trevorMod$moveItemStackTo(
                    sourceStack, vanillaStart, vanillaEnd, false)) {
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
            sourceSlot.setByPlayer(sourceStack, original);
            sourceSlot.setChanged();
            cir.setReturnValue(original);
            return;
        }

        // ── Case 2: Shift-click FROM a vanilla slot → try MKSlots first ─────
        // Only route to MKSlots that have shiftClickIn=true, are active, and
        // accept the item via mayPlace(). Two passes: partial stacks first,
        // then empty slots.

        ItemStack original = sourceStack.copy();
        boolean moved = false;

        // Pass 1: Fill existing partial stacks in shiftClickIn-enabled MKSlots
        for (Slot targetSlot : menu.slots) {
            if (sourceStack.isEmpty()) break;
            if (!(targetSlot instanceof MKSlot mkTarget)) continue;
            if (!mkTarget.canShiftClickIn()) continue;
            if (!mkTarget.mayPlace(sourceStack)) continue;

            ItemStack targetItem = mkTarget.getItem();
            if (!targetItem.isEmpty()
                    && ItemStack.isSameItemSameComponents(sourceStack, targetItem)
                    && targetItem.getCount() < mkTarget.getMaxStackSize()) {
                int space = mkTarget.getMaxStackSize() - targetItem.getCount();
                int toAdd = Math.min(sourceStack.getCount(), space);
                sourceStack.shrink(toAdd);
                targetItem.grow(toAdd);
                mkTarget.setChanged();
                moved = true;
            }
        }

        // Pass 2: Place into empty shiftClickIn-enabled MKSlots
        for (Slot targetSlot : menu.slots) {
            if (sourceStack.isEmpty()) break;
            if (!(targetSlot instanceof MKSlot mkTarget)) continue;
            if (!mkTarget.canShiftClickIn()) continue;
            if (!mkTarget.mayPlace(sourceStack)) continue;

            if (mkTarget.getItem().isEmpty()) {
                int toPlace = Math.min(sourceStack.getCount(), mkTarget.getMaxStackSize());
                mkTarget.setByPlayer(sourceStack.split(toPlace), original);
                mkTarget.setChanged();
                moved = true;
            }
        }

        if (moved) {
            sourceSlot.setChanged();
            cir.setReturnValue(original);
            return;
        }

        // No MKSlot accepted the item — let vanilla handle it normally
    }
}
