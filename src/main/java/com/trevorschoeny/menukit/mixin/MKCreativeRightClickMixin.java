package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.*;
import com.trevorschoeny.menukit.event.*;
import com.trevorschoeny.menukit.widget.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Creative inventory right-click handler — catches clicks that bypass
 * {@code slotClicked} (especially hotbar slots in creative mode).
 *
 * <p>Fires two paths for each right-click:
 * <ol>
 *   <li>Per-slot handlers from {@link MKSlotState} (legacy API)</li>
 *   <li>{@link MKEvent.Type#RIGHT_CLICK} through the {@link MKEventBus}</li>
 * </ol>
 *
 * <p>If either consumes, vanilla behavior is cancelled.
 *
 * <p>{@link MKCreativeClickBusMixin} explicitly skips RIGHT_CLICK to avoid
 * double-firing — this mixin is the sole owner of right-click bus dispatch
 * for creative screens.
 *
 * <p><b>SlotWrapper unwrapping:</b> Creative mode wraps slots in SlotWrapper.
 * We unwrap via {@link SlotWrapperAccessor} before state lookup so the bus
 * event carries the real slot, not the wrapper.
 *
 * <p>Part of the <b>MenuKit</b> framework (mixin layer).
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class MKCreativeRightClickMixin
        extends AbstractContainerScreen<net.minecraft.world.inventory.AbstractContainerMenu> {

    // Dummy constructor required by the mixin compiler — AbstractContainerScreen
    // demands a super() call, but mixin classes are never actually instantiated.
    // The null arguments are never used at runtime.
    protected MKCreativeRightClickMixin() { super(null, null, null); }

    // ── slotClicked: catches most creative inventory clicks ──────────────

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void menuKit$onCreativeSlotRightClick(Slot slot, int slotId, int button,
                                                    ClickType clickType, CallbackInfo ci) {
        if (button != 1 || clickType != ClickType.PICKUP) return;
        if (slot == null) return;

        // Unwrap SlotWrapper to get the real slot for state lookup
        Slot realSlot = slot;
        if (slot instanceof SlotWrapperAccessor wrapper) {
            realSlot = wrapper.menuKit$getTarget();
        }

        boolean consumed = false;

        // ── Phase 1: Per-slot right-click handlers (legacy API) ─────────
        MKSlotState state = MKSlotStateRegistry.get(realSlot);
        if (state != null && state.hasRightClickHandlers()) {
            if (state.fireRightClick(realSlot, realSlot.getItem())) {
                consumed = true;
            }
        }

        // ── Phase 2: Event bus (global API) ─────────────────────────────
        if (!consumed) {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
                MKSlotEvent event = MKEventHelper.buildSlotEvent(
                        MKEvent.Type.RIGHT_CLICK, realSlot, button, screen, player);
                if (MKEventBus.fire(event)) {
                    consumed = true;
                }
            }
        }

        if (consumed) {
            ci.cancel();
        }
    }

    // ── mouseClicked: catches hotbar clicks that bypass slotClicked ──────

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            at = @At("HEAD"), cancellable = true)
    private void menuKit$onCreativeMouseRightClick(MouseButtonEvent event, boolean bl,
                                                     CallbackInfoReturnable<Boolean> cir) {
        if (event.button() != 1) return;
        if (this.hoveredSlot == null) return;

        // Unwrap SlotWrapper to get the real slot
        Slot hovered = this.hoveredSlot;
        Slot realSlot = hovered;
        if (hovered instanceof SlotWrapperAccessor wrapper) {
            realSlot = wrapper.menuKit$getTarget();
        }

        boolean consumed = false;

        // ── Phase 1: Per-slot right-click handlers (legacy API) ─────────
        MKSlotState state = MKSlotStateRegistry.get(realSlot);
        if (state != null && state.hasRightClickHandlers()) {
            if (state.fireRightClick(realSlot, realSlot.getItem())) {
                consumed = true;
            }
        }

        // ── Phase 2: Event bus (global API) ─────────────────────────────
        if (!consumed) {
            Player player = Minecraft.getInstance().player;
            if (player != null) {
                AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
                MKSlotEvent event2 = MKEventHelper.buildSlotEvent(
                        MKEvent.Type.RIGHT_CLICK, realSlot, 1, screen, player);
                if (MKEventBus.fire(event2)) {
                    consumed = true;
                }
            }
        }

        if (consumed) {
            cir.setReturnValue(true);
        }
    }
}
