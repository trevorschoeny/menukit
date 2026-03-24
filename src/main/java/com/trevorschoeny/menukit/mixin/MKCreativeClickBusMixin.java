package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.MKEventHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Creative-mode click-to-bus wiring — handles all click types EXCEPT
 * RIGHT_CLICK in {@link CreativeModeInventoryScreen}.
 *
 * <p>Creative mode overrides {@code slotClicked()} with its own logic that
 * differs from vanilla AbstractContainerScreen. This mixin intercepts that
 * override and fires click events through the {@link com.trevorschoeny.menukit.MKEventBus}.
 *
 * <p><b>RIGHT_CLICK is skipped here.</b> {@link MKCreativeRightClickMixin}
 * already handles right-clicks in creative mode (both slotClicked and the
 * mouseClicked fallback for hotbar slots).
 *
 * <p><b>SlotWrapper unwrapping:</b> Creative mode wraps all slots in
 * SlotWrapper objects. Before state lookup, we unwrap via
 * {@link SlotWrapperAccessor} to get the real slot. The event is built with
 * the unwrapped slot so bus listeners see the actual MKSlot/vanilla slot,
 * not the creative wrapper.
 *
 * <p>Part of the <b>MenuKit</b> framework (mixin layer).
 */
@Mixin(CreativeModeInventoryScreen.class)
public class MKCreativeClickBusMixin {

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void menuKit$fireCreativeClickBusEvent(Slot slot, int slotId, int button,
                                                     ClickType clickType, CallbackInfo ci) {
        // ── Skip RIGHT_CLICK — handled by MKCreativeRightClickMixin ─────
        if (clickType == ClickType.PICKUP && button == 1) return;

        // ── Resolve the player ──────────────────────────────────────────
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        // ── Unwrap SlotWrapper for creative mode ────────────────────────
        // Creative mode wraps all slots in SlotWrapper. We need the real
        // slot underneath for MKSlotState lookup and region resolution.
        // The event is built with the unwrapped slot.
        Slot realSlot = slot;
        if (slot instanceof SlotWrapperAccessor wrapper) {
            realSlot = wrapper.menuKit$getTarget();
        }

        // ── Cast self to AbstractContainerScreen ────────────────────────
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;

        // ── Map, build, fire ────────────────────────────────────────────
        if (MKEventHelper.fireClickEvent(clickType, realSlot, button, screen, player)) {
            ci.cancel();
        }
    }
}
