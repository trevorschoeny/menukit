package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.MKSlotState;
import com.trevorschoeny.menukit.MKSlotStateRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Slot mixin layer — adds per-slot behavioral features to ALL vanilla Slots.
 *
 * <p>Hooks into five Slot methods:
 * <ul>
 *   <li>{@code isActive()} — disabled slots return false (hidden, no interaction)</li>
 *   <li>{@code mayPlace(ItemStack)} — OUTPUT persistence, filter, and disabled checks block placement</li>
 *   <li>{@code getMaxStackSize()} — wrapper delegation and per-slot override</li>
 *   <li>{@code getMaxStackSize(ItemStack)} — wrapper delegation for item-specific limits</li>
 *   <li>{@code getNoItemIcon()} — ghost icon shows when slot is empty</li>
 * </ul>
 *
 * <p>All checks consult {@link MKSlotStateRegistry}: if no state exists for a
 * slot, vanilla behavior is unchanged. This ensures zero overhead for slots
 * that MenuKit doesn't know about.
 *
 * <p>Part of the <b>MenuKit</b> framework (mixin layer).
 */
@Mixin(Slot.class)
public class MKSlotMixin {

    /**
     * If this slot has MKSlotState, use the full activity check:
     * disabled OR panel hidden → return false.
     */
    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void menuKit$checkDisabled(CallbackInfoReturnable<Boolean> cir) {
        Slot self = (Slot)(Object) this;
        MKSlotState state = MKSlotStateRegistry.get(self);
        // With universal state, this should never be null. Safety net only.
        if (state != null && !state.isSlotActive()) {
            cir.setReturnValue(false);
        }
    }

    /**
     * If this slot has MKSlotState, enforce:
     * 1. OUTPUT persistence blocks all placement (take-only)
     * 2. Disabled slots reject all items
     * 3. Custom filter rejects non-matching items
     */
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void menuKit$checkFilter(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        Slot self = (Slot)(Object) this;
        MKSlotState state = MKSlotStateRegistry.get(self);
        // With universal state, this should never be null. Safety net only.
        if (state == null) return;

        // OUTPUT slots never accept items — take-only, like crafting result
        if (state.isPersistenceOutput()) {
            cir.setReturnValue(false);
            return;
        }

        // Disabled slots reject everything
        if (state.isDisabled()) {
            cir.setReturnValue(false);
            return;
        }

        // Panel visibility check — hidden panel slots reject items
        if (!state.isSlotActive()) {
            cir.setReturnValue(false);
            return;
        }

        // Locked slots reject all placement — the player has explicitly
        // pinned this slot's contents. This protects against shift-click
        // routing items INTO a locked slot and against double-click
        // collection pulling FROM other slots into a locked target.
        if (state.isLocked()) {
            cir.setReturnValue(false);
            return;
        }

        // NOTE: ShiftClickIn gate was removed from here. mayPlace() is a general
        // item-acceptance method called for ALL placement operations (left-click,
        // shift-click, hopper insertion). A shift-click restriction must NOT live
        // here — it blocks all placement types, not just shift-clicks. Shift-click
        // routing is handled in MKDoClickMixin where it belongs.

        // Custom filter check
        if (!state.passesFilter(stack)) {
            cir.setReturnValue(false);
            return;
        }

        // Source constraint check (e.g., bundle weight limits)
        if (self.container instanceof com.trevorschoeny.menukit.MKContainer mkc && mkc.isBound()) {
            if (!mkc.getSource().canAccept(self.getContainerSlot(), stack)) {
                cir.setReturnValue(false);
            }
        }
    }

    /**
     * If this slot wraps a vanilla slot, delegate to the wrapped slot's
     * max stack size (e.g., armor slots have custom limits).
     * Otherwise, use the per-slot max stack size override if set.
     */
    @Inject(method = "getMaxStackSize()I", at = @At("HEAD"), cancellable = true)
    private void menuKit$maxStackSize(CallbackInfoReturnable<Integer> cir) {
        Slot self = (Slot)(Object) this;
        MKSlotState state = MKSlotStateRegistry.get(self);
        // With universal state, this should never be null. Safety net only.
        if (state == null) return;

        // Wrapper delegation — preserve the wrapped slot's custom max stack
        Slot wrapped = state.getWrappedSlot();
        if (wrapped != null) {
            cir.setReturnValue(wrapped.getMaxStackSize());
            return;
        }

        // Per-slot override from state
        if (state.getMaxStackSize() > 0) {
            cir.setReturnValue(state.getMaxStackSize());
        }
    }

    /**
     * If this slot wraps a vanilla slot, delegate to the wrapped slot's
     * item-specific max stack size. Some vanilla slots (e.g., armor, offhand)
     * limit stack size based on the item type.
     *
     * <p>Also enforces source-level capacity limits (e.g., bundle weight).
     * When a slot is backed by an MKContainer with a bound source, the
     * source's {@code getMaxAcceptCount} limits how many items can be
     * inserted. This makes vanilla's {@code safeInsert} naturally perform
     * partial insertion, leaving excess items on the cursor.
     */
    @Inject(method = "getMaxStackSize(Lnet/minecraft/world/item/ItemStack;)I", at = @At("HEAD"), cancellable = true)
    private void menuKit$maxStackSizeForItem(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        Slot self = (Slot)(Object) this;
        MKSlotState state = MKSlotStateRegistry.get(self);
        // With universal state, this should never be null. Safety net only.
        if (state == null) return;

        // Wrapper delegation — preserve the wrapped slot's item-specific max stack
        Slot wrapped = state.getWrappedSlot();
        if (wrapped != null) {
            cir.setReturnValue(wrapped.getMaxStackSize(stack));
            return;
        }

        // Source capacity limit — backed by a bound MKContainer (bundle, shulker, etc.)
        if (self.container instanceof com.trevorschoeny.menukit.MKContainer mkc && mkc.isBound()) {
            int maxAccept = mkc.getSource().getMaxAcceptCount(self.getContainerSlot(), stack);
            if (maxAccept < Integer.MAX_VALUE) {
                // Effective max = what's already in the slot + how many more the source can take
                int current = self.getItem().isEmpty() ? 0 : self.getItem().getCount();
                int sourceLimit = current + maxAccept;
                // Also respect the item's own max stack size and per-slot override
                int itemLimit = stack.getMaxStackSize();
                int slotLimit = (state.getMaxStackSize() > 0) ? state.getMaxStackSize() : 64;
                cir.setReturnValue(Math.min(sourceLimit, Math.min(itemLimit, slotLimit)));
            }
        }
    }

    /**
     * If this slot has a ghost icon set in MKSlotState, return null to
     * suppress vanilla's sprite rendering. MenuKit renders its own
     * ghost icons (pixel-art outlines) in renderPanelBackgrounds().
     *
     * <p>Returning the Identifier here would cause vanilla's blitSprite
     * to look for a sprite in the atlas — which doesn't exist for our
     * custom pixel-art icons, resulting in broken/missing textures.
     */
    @Inject(method = "getNoItemIcon", at = @At("HEAD"), cancellable = true)
    private void menuKit$ghostIcon(CallbackInfoReturnable<@Nullable Identifier> cir) {
        Slot self = (Slot)(Object) this;
        MKSlotState state = MKSlotStateRegistry.get(self);
        // With universal state, state should never be null. The null check
        // is a safety net — the real gate is getGhostIcon() != null.
        if (state != null && state.getGhostIcon() != null) {
            // Suppress vanilla rendering — MenuKit renders its own ghost icon
            cir.setReturnValue(null);
        }
    }
}
