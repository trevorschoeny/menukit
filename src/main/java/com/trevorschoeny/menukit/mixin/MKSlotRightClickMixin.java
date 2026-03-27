package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts right-clicks on ANY slot in ANY container screen and fires:
 * <ol>
 *   <li>Per-slot right-click handlers from {@link MKSlotState} (legacy API)</li>
 *   <li>A {@link MKEvent.Type#RIGHT_CLICK} event through the {@link MKEventBus}</li>
 * </ol>
 *
 * <p>If EITHER the per-slot handler OR the bus consumes the click, vanilla
 * behavior is cancelled. Per-slot handlers fire first (they're the older,
 * more specific API), then the bus fires (newer, global API).
 *
 * <p>{@link MKSlotClickBusMixin} explicitly skips RIGHT_CLICK to avoid
 * double-firing — this mixin is the sole owner of right-click bus dispatch
 * for non-creative screens.
 *
 * <p>Creative inventory has its own click handling that bypasses slotClicked
 * for some slots — that case is handled by {@link MKCreativeRightClickMixin}.
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

        boolean consumed = false;

        // ── Phase 1: Per-slot right-click handlers (legacy API) ─────────
        // These are the older, per-slot callbacks registered via
        // MKSlotState.addRightClickHandler(). Fire them first — they're
        // more specific and have been around longer.
        if (slot != null) {
            MKSlotState state = MKSlotStateRegistry.get(slot);
            if (state != null && state.hasRightClickHandlers()) {
                ItemStack stack = slot.getItem();
                if (state.fireRightClick(slot, stack)) {
                    consumed = true;
                }
            }
        }

        // ── Phase 2: Event bus (global API) ─────────────────────────────
        // Fire through the bus even if per-slot handlers already consumed.
        // Bus listeners may want to observe right-clicks regardless.
        // However, if the bus ALSO consumes, we still cancel vanilla.
        if (!consumed) {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
                MKSlotEvent event = MKEventHelper.buildSlotEvent(
                        MKEvent.Type.RIGHT_CLICK, slot, button, screen, player);
                if (MKEventBus.fire(event)) {
                    consumed = true;
                }
            }
        }

        // ── Cancel vanilla if either path consumed ──────────────────────
        if (consumed) {
            ci.cancel();
        }
    }
}
