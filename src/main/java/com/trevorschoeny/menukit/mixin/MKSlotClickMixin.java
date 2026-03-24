package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.MKSlot;
import com.trevorschoeny.menukit.MenuKit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Single handler for {@link MKSlot#getOnEmptyClick()} callbacks.
 *
 * <p>Intercepts {@code AbstractContainerMenu.clicked()} at HEAD to fire
 * the callback when the player left-clicks an empty MKSlot with an empty
 * cursor. Vanilla treats this as a no-op, so we're safe to act here.
 *
 * <p>Handles two cases in one place:
 * <ul>
 *   <li><b>Vanilla found the slot</b> ({@code slotId} is valid) — we check
 *       if it's an MKSlot and fire directly.</li>
 *   <li><b>Vanilla missed the slot</b> ({@code slotId == -1}, slot is outside
 *       the container bounds) — we fall back to MenuKit's own hover detection
 *       ({@link MenuKit#getHoveredMKSlot()}) which works everywhere.</li>
 * </ul>
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
@Mixin(AbstractContainerMenu.class)
public class MKSlotClickMixin {

    @Inject(method = "clicked", at = @At("HEAD"))
    private void trevorMod$onEmptySlotClick(int slotId, int button, ClickType clickType,
                                             Player player, CallbackInfo ci) {
        // Only intercept left-click PICKUP on the client (render) thread.
        // AbstractContainerMenu.clicked() fires on BOTH client and server;
        // the callback is a client-side UI action (e.g., toggling markers).
        if (clickType != ClickType.PICKUP || button != 0) return;
        if (!player.level().isClientSide()) return;

        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;

        // Try to find a MenuKit-managed slot — either from vanilla's slotId or hover detection
        Slot targetSlot = null;
        com.trevorschoeny.menukit.MKSlotState state = null;

        if (slotId >= 0 && slotId < menu.slots.size()) {
            Slot slot = menu.slots.get(slotId);
            // Unwrap SlotWrapper (creative mode) if needed
            if (slot instanceof SlotWrapperAccessor w) {
                slot = w.menuKit$getTarget();
            }
            state = com.trevorschoeny.menukit.MKSlotStateRegistry.get(slot);
            if (state != null && state.isMenuKitSlot()) {
                targetSlot = slot;
            }
        }

        // Fall back to MenuKit's hover detection from renderSlotBackgrounds
        if (targetSlot == null) {
            targetSlot = MenuKit.getHoveredMKSlot();
            if (targetSlot != null) {
                state = com.trevorschoeny.menukit.MKSlotStateRegistry.get(targetSlot);
            }
        }

        if (targetSlot == null || state == null) return;

        // Both the slot and the cursor must be empty
        if (targetSlot.hasItem()) return;
        if (!menu.getCarried().isEmpty()) return;

        // Fire the callback if one is registered
        var callback = state.getOnEmptyClick();
        if (callback != null) {
            callback.accept(targetSlot);
        }
    }
}
