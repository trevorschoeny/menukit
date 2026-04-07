package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.event.MKEvent;
import com.trevorschoeny.menukit.event.MKEventBus;
import com.trevorschoeny.menukit.event.MKEventHelper;
import com.trevorschoeny.menukit.event.MKSlotEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Fires {@link MKEvent.Type#ITEM_TRANSFER_IN ITEM_TRANSFER_IN} and
 * {@link MKEvent.Type#ITEM_TRANSFER_OUT ITEM_TRANSFER_OUT} events through
 * the {@link MKEventBus} whenever items move into or out of a slot.
 *
 * <p>Instead of trying to inject into the ~800-line {@code AbstractContainerMenu.doClick()},
 * this mixin targets the four {@link Slot} methods that actually move items:
 * <ul>
 *   <li>{@code safeInsert(ItemStack, int)} — items entering a slot</li>
 *   <li>{@code setByPlayer(ItemStack)} — direct slot setting by player action</li>
 *   <li>{@code safeTake(int, int, Player)} — items leaving a slot</li>
 *   <li>{@code tryRemove(int, int, Player)} — partial item extraction</li>
 * </ul>
 *
 * <p>This is cleaner and more robust than doClick injection because:
 * <ol>
 *   <li>These methods are small, well-defined, and stable across versions</li>
 *   <li>They capture ALL item movement, not just doClick paths (shift-click
 *       quickMoveStack, hopper transfers, mod-initiated moves, etc.)</li>
 *   <li>No risk of misidentifying injection points in a massive method</li>
 * </ol>
 *
 * <p><b>Cancellation:</b> If a bus handler returns
 * {@link com.trevorschoeny.menukit.MKEventResult#CONSUMED CONSUMED}, the
 * transfer is blocked — the item stays where it was.
 *
 * <p><b>Transformation:</b> If a handler calls
 * {@link MKSlotEvent#transformStack(ItemStack)}, the transformed stack is used
 * instead of the original for ITEM_TRANSFER_IN events. For ITEM_TRANSFER_OUT,
 * transformation is not applied (the handler should CONSUMED to block instead).
 *
 * <p><b>Runs on both client and server.</b> Item movement is authoritative on
 * the server, and these Slot methods execute on both sides. This mixin is
 * registered in the common (non-client) mixin section.
 *
 * <p>Part of the <b>MenuKit</b> framework (mixin layer).
 */
@Mixin(Slot.class)
public class MKTransferMixin {

    // ── ITEM_TRANSFER_IN — safeInsert ────────────────────────────────────────
    //
    // safeInsert(ItemStack stack, int increment) is the primary path for items
    // entering a slot. Called during left-click place, right-click place-one,
    // shift-click destination, and programmatic insertions.
    //
    // We inject at HEAD to fire the event BEFORE vanilla inserts the item.
    // If consumed, we return the original stack unchanged (nothing was inserted).
    // If transformed, we replace the stack parameter before vanilla sees it.

    /**
     * Fires ITEM_TRANSFER_IN before an item is inserted into this slot via
     * {@code safeInsert}. Handlers can block the insertion (CONSUMED) or
     * replace the incoming item (transformStack).
     *
     * <p>The event carries:
     * <ul>
     *   <li>slotStack = current slot contents (what's already in the slot)</li>
     *   <li>cursorStack = the incoming stack about to be inserted</li>
     * </ul>
     */
    @Inject(method = "safeInsert(Lnet/minecraft/world/item/ItemStack;I)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"), cancellable = true)
    private void menuKit$fireTransferIn(ItemStack stack, int increment,
                                         CallbackInfoReturnable<ItemStack> cir) {
        // Don't fire events for empty stacks — no item is actually moving
        if (stack.isEmpty()) return;

        Slot self = (Slot)(Object) this;

        // ── Resolve the player ──────────────────────────────────────────
        // safeInsert doesn't receive a Player parameter. We resolve it from
        // the slot's backing container (works for player inventory slots) or
        // from the active player context.
        Player player = MKEventHelper.resolvePlayerFromSlot(self);

        // ── Build the transfer event ────────────────────────────────────
        // slotStack = current slot contents; cursorStack = incoming item
        MKSlotEvent event = MKEventHelper.buildTransferEvent(
                MKEvent.Type.ITEM_TRANSFER_IN,
                self,
                self.getItem().copy(),  // snapshot current slot contents
                stack.copy(),           // snapshot the incoming item
                player
        );

        // If we couldn't resolve a player, skip the event — vanilla proceeds
        if (event == null) return;

        // ── Dispatch through the bus ────────────────────────────────────
        boolean consumed = MKEventBus.fire(event);

        if (consumed) {
            // Handler blocked the insertion — return the original stack
            // unchanged, as if the slot refused the item.
            cir.setReturnValue(stack);
            return;
        }

        // ── Check for stack transformation ──────────────────────────────
        // A handler may have called transformStack() to replace the incoming
        // item (e.g., ore -> ingot on insertion into a smelter slot).
        // We work with the transformed copy and only zero the original stack
        // AFTER confirming the insertion will succeed. This prevents data loss
        // if the slot rejects the transformed item (incompatible contents).
        if (event.hasTransform()) {
            ItemStack transformed = event.getTransformedStack();

            // We need to cancel and re-invoke with the transformed stack, but
            // that risks infinite recursion. Instead, directly set the slot
            // contents to the transformed item and return empty.
            int maxAccept = self.getMaxStackSize(transformed) - self.getItem().getCount();
            if (maxAccept <= 0) {
                // Slot is full for this item type — return original stack untouched
                cir.setReturnValue(stack);
                return;
            }
            int toInsert = Math.min(transformed.getCount(), maxAccept);
            if (self.getItem().isEmpty()) {
                // Slot is empty — set the transformed stack directly.
                // Insertion WILL succeed, so now we can zero the original.
                stack.setCount(0);
                self.setByPlayer(transformed.copyWithCount(toInsert));
            } else if (ItemStack.isSameItemSameComponents(self.getItem(), transformed)) {
                // Slot has compatible items — grow the count.
                // Insertion WILL succeed, so now we can zero the original.
                stack.setCount(0);
                self.getItem().grow(toInsert);
                self.setChanged();
            } else {
                // Slot has incompatible items — can't insert.
                // Do NOT zero the original stack — the caller still owns it.
                // Return the transformed stack so the caller knows what was
                // attempted (matches vanilla safeInsert return semantics).
                cir.setReturnValue(transformed);
                return;
            }
            // Return remainder (or empty if fully consumed)
            if (toInsert >= transformed.getCount()) {
                cir.setReturnValue(ItemStack.EMPTY);
            } else {
                cir.setReturnValue(transformed.copyWithCount(transformed.getCount() - toInsert));
            }
        }
    }

    // ── ITEM_TRANSFER_IN — setByPlayer ───────────────────────────────────────
    //
    // setByPlayer(ItemStack stack) is called for direct slot assignment during
    // player interactions — e.g., swap operations, creative mode placement.
    // Unlike safeInsert, this REPLACES the slot contents entirely rather than
    // merging stacks.
    //
    // We fire ITEM_TRANSFER_IN here too, since it's another path for items
    // entering a slot. The semantics are slightly different (replace vs merge)
    // but from the handler's perspective, an item is going into a slot.

    /**
     * Fires ITEM_TRANSFER_IN before a slot's contents are replaced via
     * {@code setByPlayer}. Handlers can block the replacement (CONSUMED)
     * or transform the incoming stack.
     *
     * <p>The event carries:
     * <ul>
     *   <li>slotStack = current slot contents (about to be replaced)</li>
     *   <li>cursorStack = the new stack about to be set</li>
     * </ul>
     */
    @Inject(method = "setByPlayer(Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"), cancellable = true)
    private void menuKit$fireTransferInViaSet(ItemStack stack, CallbackInfo ci) {
        Slot self = (Slot)(Object) this;

        // ── Resolve the player ──────────────────────────────────────────
        Player player = MKEventHelper.resolvePlayerFromSlot(self);

        // ── Build the transfer event ────────────────────────────────────
        // slotStack = current slot contents; cursorStack = replacement item
        MKSlotEvent event = MKEventHelper.buildTransferEvent(
                MKEvent.Type.ITEM_TRANSFER_IN,
                self,
                self.getItem().copy(),  // snapshot current slot contents
                stack.copy(),           // snapshot the replacement item
                player
        );

        if (event == null) return;

        // ── Dispatch through the bus ────────────────────────────────────
        boolean consumed = MKEventBus.fire(event);

        if (consumed) {
            // Handler blocked the replacement — slot stays as-is
            ci.cancel();
            return;
        }

        // ── Check for stack transformation ──────────────────────────────
        // If transformed, mutate the incoming stack parameter so vanilla's
        // setByPlayer logic uses the transformed version.
        if (event.hasTransform()) {
            ItemStack transformed = event.getTransformedStack();
            // Mutate the incoming stack to match the transformed version.
            // ItemStack.EMPTY check: if handler transforms to empty, let
            // vanilla handle the set (it'll clear the slot).
            stack.setCount(0);
            if (!transformed.isEmpty()) {
                // Replace stack contents — since we can't reassign the param,
                // cancel and do the set ourselves with the transformed stack.
                ci.cancel();
                // Call the vanilla set path directly on the slot's container.
                // This bypasses setByPlayer's callback but that's acceptable
                // since the handler already processed the event.
                self.set(transformed);
                self.setChanged();
            }
        }
    }

    // ── ITEM_TRANSFER_OUT — safeTake ─────────────────────────────────────────
    //
    // safeTake(int count, int decrement, Player player) is the primary path
    // for items leaving a slot. Called during left-click pickup,
    // right-click half-pickup, and shift-click source extraction.
    //
    // We inject at HEAD to fire the event BEFORE vanilla removes the item.
    // If consumed, we return EMPTY (nothing was taken).

    /**
     * Fires ITEM_TRANSFER_OUT before items are taken from this slot via
     * {@code safeTake}. Handlers can block the extraction (CONSUMED).
     *
     * <p>The event carries:
     * <ul>
     *   <li>slotStack = current slot contents (about to be taken)</li>
     *   <li>cursorStack = empty (nothing on cursor yet from this operation)</li>
     * </ul>
     */
    @Inject(method = "safeTake(IILnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"), cancellable = true)
    private void menuKit$fireTransferOut(int count, int decrement, Player player,
                                          CallbackInfoReturnable<ItemStack> cir) {
        Slot self = (Slot)(Object) this;

        // Don't fire events for empty slots — nothing to take
        if (self.getItem().isEmpty()) return;

        // ── Build the transfer event ────────────────────────────────────
        // slotStack = current contents (about to be taken); cursorStack = empty
        MKSlotEvent event = MKEventHelper.buildTransferEvent(
                MKEvent.Type.ITEM_TRANSFER_OUT,
                self,
                self.getItem().copy(),  // snapshot current slot contents
                ItemStack.EMPTY,        // nothing incoming (this is an extraction)
                player
        );

        if (event == null) return;

        // ── Dispatch through the bus ────────────────────────────────────
        boolean consumed = MKEventBus.fire(event);

        if (consumed) {
            // Handler blocked the extraction — return empty as if nothing was taken
            cir.setReturnValue(ItemStack.EMPTY);
        }
        // Note: transformStack() on ITEM_TRANSFER_OUT is ignored.
        // If a handler wants to change what's taken, it should manipulate the
        // slot contents directly in the handler and return CONSUMED.
    }

    // ── ITEM_TRANSFER_OUT — tryRemove ────────────────────────────────────────
    //
    // tryRemove(int count, int decrement, Player player) is the partial
    // extraction path. Returns Optional<ItemStack> — empty optional means
    // nothing was removed. Called by some vanilla paths as an alternative
    // to safeTake.

    /**
     * Fires ITEM_TRANSFER_OUT before items are partially removed from this
     * slot via {@code tryRemove}. Handlers can block the removal (CONSUMED).
     *
     * <p>Same event semantics as safeTake — slotStack is the current contents,
     * cursorStack is empty.
     */
    @Inject(method = "tryRemove(IILnet/minecraft/world/entity/player/Player;)Ljava/util/Optional;",
            at = @At("HEAD"), cancellable = true)
    private void menuKit$fireTransferOutViaRemove(int count, int decrement, Player player,
                                                    CallbackInfoReturnable<Optional<ItemStack>> cir) {
        Slot self = (Slot)(Object) this;

        // Don't fire events for empty slots — nothing to remove
        if (self.getItem().isEmpty()) return;

        // ── Build the transfer event ────────────────────────────────────
        MKSlotEvent event = MKEventHelper.buildTransferEvent(
                MKEvent.Type.ITEM_TRANSFER_OUT,
                self,
                self.getItem().copy(),  // snapshot current slot contents
                ItemStack.EMPTY,        // nothing incoming
                player
        );

        if (event == null) return;

        // ── Dispatch through the bus ────────────────────────────────────
        boolean consumed = MKEventBus.fire(event);

        if (consumed) {
            // Handler blocked the removal — return empty Optional (nothing removed)
            cir.setReturnValue(Optional.empty());
        }
    }
}
