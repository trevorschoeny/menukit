package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.event.MKEventHelper;
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
 * <p>SlotWrapper unwrapping is handled centrally by {@link MKEventHelper} —
 * this mixin passes the raw hovered slot through and the helper unwraps at
 * event-construction time to enforce the slot-identity invariant.
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

        // ── Cast self to AbstractContainerScreen ────────────────────────
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;

        // ── Map, build, fire ────────────────────────────────────────────
        // MKEventHelper unwraps the creative SlotWrapper at event construction,
        // so bus listeners always see the real underlying slot.
        if (MKEventHelper.fireClickEvent(clickType, slot, button, screen, player)) {
            ci.cancel();
        }
    }
}
