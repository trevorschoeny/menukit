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

        // Try to find the MKSlot — either from vanilla's slotId or our own hover detection
        MKSlot mkSlot = null;

        if (slotId >= 0 && slotId < menu.slots.size()) {
            // Vanilla found a slot — check if it's one of ours
            Slot slot = menu.slots.get(slotId);
            if (slot instanceof MKSlot mk) {
                mkSlot = mk;
            } else if (slot instanceof SlotWrapperAccessor w) {
                // Creative mode wraps slots — unwrap to find the MKSlot
                var target = w.menuKit$getTarget();
                if (target instanceof MKSlot mk) {
                    mkSlot = mk;
                }
            }
        }

        // If vanilla didn't find it (slotId == -1, outside container bounds),
        // fall back to MenuKit's hover detection from renderSlotBackgrounds
        if (mkSlot == null) {
            mkSlot = MenuKit.getHoveredMKSlot();
        }

        if (mkSlot == null) return;

        // Both the slot and the cursor must be empty
        if (mkSlot.hasItem()) return;
        if (!menu.getCarried().isEmpty()) return;

        // Fire the callback if one is registered
        var callback = mkSlot.getOnEmptyClick();
        if (callback != null) {
            callback.accept(mkSlot);
        }
    }
}
