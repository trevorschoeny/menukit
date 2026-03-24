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
 * <p>Hooks into three Slot methods:
 * <ul>
 *   <li>{@code isActive()} — disabled slots return false (hidden, no interaction)</li>
 *   <li>{@code mayPlace(ItemStack)} — filter and disabled checks block placement</li>
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
        if (state != null && !state.isSlotActive()) {
            cir.setReturnValue(false);
        }
    }

    /**
     * If this slot has MKSlotState, enforce:
     * 1. Disabled slots reject all items
     * 2. Custom filter rejects non-matching items
     */
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void menuKit$checkFilter(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        Slot self = (Slot)(Object) this;
        MKSlotState state = MKSlotStateRegistry.get(self);
        if (state == null) return;

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

        // Custom filter check
        if (!state.passesFilter(stack)) {
            cir.setReturnValue(false);
            return;
        }

        // Source constraint check (e.g., bundle weight limits)
        Slot self2 = (Slot)(Object) this;
        if (self2.container instanceof com.trevorschoeny.menukit.MKContainer mkc && mkc.isBound()) {
            if (!mkc.getSource().canAccept(self2.getContainerSlot(), stack)) {
                cir.setReturnValue(false);
            }
        }
    }

    /**
     * If this slot has a per-slot max stack size override, use it.
     */
    @Inject(method = "getMaxStackSize()I", at = @At("HEAD"), cancellable = true)
    private void menuKit$maxStackSize(CallbackInfoReturnable<Integer> cir) {
        Slot self = (Slot)(Object) this;
        MKSlotState state = MKSlotStateRegistry.get(self);
        if (state != null && state.getMaxStackSize() > 0) {
            cir.setReturnValue(state.getMaxStackSize());
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
        if (state != null && state.getGhostIcon() != null) {
            // Suppress vanilla rendering — MenuKit renders its own ghost icon
            cir.setReturnValue(null);
        }
    }
}
