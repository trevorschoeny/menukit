package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.MKEventBus;
import com.trevorschoeny.menukit.MKEventHelper;
import com.trevorschoeny.menukit.MKSlotEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fires ALL click event types through the {@link com.trevorschoeny.menukit.MKEventBus}
 * for every {@link AbstractContainerScreen}.
 *
 * <p>This is the primary click-to-bus wiring mixin. It has two injection points:
 *
 * <ol>
 *   <li>{@code slotClicked} — catches all click types that vanilla routes through
 *       the standard slot-click pipeline (LEFT_CLICK, SHIFT_CLICK, SWAP, MIDDLE_CLICK
 *       in creative, THROW, DOUBLE_CLICK).</li>
 *   <li>{@code mouseClicked} — catches middle-click (button=2) in survival mode,
 *       where vanilla never calls slotClicked because ClickType.CLONE is creative-only.
 *       Without this, middle-click would be a dead button for non-creative players.</li>
 * </ol>
 *
 * <p><b>RIGHT_CLICK is skipped here.</b> {@link MKSlotRightClickMixin} already
 * intercepts right-clicks to fire per-slot handlers AND the bus (after this
 * patch). Handling it here too would cause double-firing. All other click
 * types (LEFT_CLICK, SHIFT_CLICK, SWAP, MIDDLE_CLICK, THROW, DOUBLE_CLICK)
 * are handled exclusively by this mixin.
 *
 * <p><b>Ordering:</b> This mixin fires BEFORE vanilla behavior. If any bus
 * handler returns {@link com.trevorschoeny.menukit.MKEventResult#CONSUMED},
 * vanilla is cancelled via {@code ci.cancel()}.
 *
 * <p>Part of the <b>MenuKit</b> framework (mixin layer).
 */
@Mixin(AbstractContainerScreen.class)
public abstract class MKSlotClickBusMixin {

    // ── Shadow: hoveredSlot is needed for the mouseClicked fallback ──────
    // In survival mode, button=2 never reaches slotClicked, so we resolve
    // the target slot from hoveredSlot at the mouseClicked level.
    @Shadow protected Slot hoveredSlot;

    // ── slotClicked: primary click dispatch ──────────────────────────────
    //
    // Handles all vanilla ClickTypes that route through slotClicked.
    // This covers creative-mode middle-click (ClickType.CLONE) as well as
    // left-click, shift-click, swap, throw, and double-click in all modes.

    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void menuKit$fireClickBusEvent(Slot slot, int slotId, int button,
                                            ClickType clickType, CallbackInfo ci) {
        // ── Skip RIGHT_CLICK — handled by MKSlotRightClickMixin ─────────
        // That mixin fires per-slot handlers first, then the bus. We don't
        // want to double-fire RIGHT_CLICK events.
        if (clickType == ClickType.PICKUP && button == 1) return;

        // ── Resolve the player ──────────────────────────────────────────
        // slotClicked is client-side only (AbstractContainerScreen is a client class),
        // so Minecraft.getInstance().player is always the local player.
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        // ── Cast self to AbstractContainerScreen ────────────────────────
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;

        // ── Map, build, fire ────────────────────────────────────────────
        // MKEventHelper handles the ClickType-to-Type mapping, context resolution,
        // and bus dispatch in one call. Returns true if any handler consumed.
        if (MKEventHelper.fireClickEvent(clickType, slot, button, screen, player)) {
            // A bus handler consumed this click — cancel vanilla behavior
            ci.cancel();
        }
    }

    // ── mouseClicked: survival-mode middle-click fallback ────────────────
    //
    // Vanilla's AbstractContainerScreen.mouseClicked() only calls slotClicked
    // with ClickType.CLONE when the player has creative-mode infinite items
    // (hasInfiniteItems()). For survival/adventure players, button=2 is
    // silently ignored — slotClicked is never called.
    //
    // This injection catches button=2 at the mouseClicked level BEFORE
    // vanilla's creative check, ensuring MIDDLE_CLICK fires through the
    // event bus for ALL game modes. Consumers can use middle-click for
    // bookmarking, info lookup, tagging, etc. regardless of creative status.
    //
    // Guard: if the player IS in creative mode, slotClicked will fire
    // ClickType.CLONE which the injection above already handles. We skip
    // here to avoid double-firing.

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            at = @At("HEAD"), cancellable = true)
    private void menuKit$fireMiddleClickBusEvent(MouseButtonEvent event, boolean bl,
                                                   CallbackInfoReturnable<Boolean> cir) {
        // ── Only handle middle-click (button=2) ─────────────────────────
        if (event.button() != 2) return;

        // ── Need a hovered slot to fire a slot event ────────────────────
        if (this.hoveredSlot == null) return;

        // ── Skip creative mode — slotClicked already handles CLONE ──────
        // In creative, vanilla calls slotClicked(slot, slotId, 2, CLONE),
        // which the slotClicked injection above catches and fires as
        // MIDDLE_CLICK. Firing here too would double-fire.
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        if (player.getAbilities().instabuild) return;

        // ── Build and fire MIDDLE_CLICK event ───────────────────────────
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        MKSlotEvent mkEvent = MKEventHelper.buildSlotEvent(
                MKSlotEvent.Type.MIDDLE_CLICK, this.hoveredSlot, 2, screen, player);

        if (MKEventBus.fire(mkEvent)) {
            // A bus handler consumed — tell vanilla we handled the click
            cir.setReturnValue(true);
        }
    }
}
