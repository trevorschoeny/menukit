package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.MKSlotState;
import com.trevorschoeny.menukit.MKSlotStateRegistry;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts right-clicks on ANY slot in ANY container screen and fires
 * registered right-click handlers from {@link MKSlotState}.
 *
 * <p>This is the unified right-click callback system. Mod authors register
 * handlers via {@code MKSlotState.addRightClickHandler()}, and this mixin
 * ensures they fire consistently across all screen types (survival, creative,
 * interaction screens).
 *
 * <p>Creative inventory has its own click handling that bypasses slotClicked
 * for some slots — that case is handled by a separate creative-specific
 * mixin that also delegates to MKSlotState.
 *
 * <p>Part of the <b>MenuKit</b> framework (mixin layer).
 */
@Mixin(AbstractContainerScreen.class)
public class MKSlotRightClickMixin {

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void menuKit$onSlotRightClick(Slot slot, int slotId, int button,
                                           ClickType clickType, CallbackInfo ci) {
        // Only right-clicks (button 1) with PICKUP type
        if (button != 1 || clickType != ClickType.PICKUP) return;
        if (slot == null) return;

        MKSlotState state = MKSlotStateRegistry.get(slot);
        if (state == null || !state.hasRightClickHandlers()) return;

        ItemStack stack = slot.getItem();
        if (state.fireRightClick(slot, stack)) {
            // Handler consumed the click — cancel vanilla behavior
            ci.cancel();
        }
    }
}
