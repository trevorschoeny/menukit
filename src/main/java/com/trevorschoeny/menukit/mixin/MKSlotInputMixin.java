package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.inject.SlotHoverResult;
import com.trevorschoeny.menukit.inject.SlotScreenDispatcher;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Library-owned registered-slot input dispatch — the input half of inventory-screen
 * parity, the counterpart to {@link MKSlotRenderMixin}. Targets
 * {@code AbstractContainerScreen} so it covers every container screen (creative
 * routes its {@code mouseClicked} and slot-hover through the inherited
 * {@code AbstractContainerScreen} machinery — see the parity build notes).
 *
 * <h3>Hover ({@code getHoveredSlot})</h3>
 *
 * Vanilla appends registered slots <em>last</em> and (in survival) parks their
 * {@code Slot.x/y} off-screen, so its first-hit {@code getHoveredSlot} never
 * returns a slot. The hook resolves the point against the revealed slots and,
 * when one wins, returns <b>the slot that is in {@code menu.slots}</b> — the raw
 * {@code MKCSlot} on a survival inventory, the creative {@code SlotWrapper}
 * that wraps it on the creative screen. Returning the in-menu slot is load-bearing
 * in creative: its click path hard-casts the hovered slot to {@code SlotWrapper}.
 * A {@code null} return for an in-panel gap makes the covered vanilla slot inert.
 *
 * <h3>Click ({@code mouseClicked})</h3>
 *
 * Lets a slot's interactive decoration (resize buttons, etc.) consume the click,
 * and eats clicks that land in a revealed panel's empty space so a carried item
 * can't drop through to the covered vanilla slot. A click on a revealed slot
 * <em>slot</em> is not eaten here — {@code getHoveredSlot} above already routes it
 * to the slot.
 *
 * <p>Both forward to {@link SlotScreenDispatcher}, which no-ops without
 * MenuKit-Containers (§0042).
 */
@Mixin(AbstractContainerScreen.class)
public abstract class MKSlotInputMixin {

    @Inject(method = "getHoveredSlot", at = @At("HEAD"), cancellable = true)
    private void mk$slotHover(double mouseX, double mouseY,
                                    CallbackInfoReturnable<Slot> cir) {
        SlotHoverResult result = SlotScreenDispatcher.fireResolveHover(
                (AbstractContainerScreen<?>) (Object) this, mouseX, mouseY);
        if (result.handled()) {
            // A slot claims the point: return its in-menu slot, or null for an
            // in-panel gap (covered vanilla slot inert).
            cir.setReturnValue(result.slot());
        }
    }

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            at = @At("HEAD"), cancellable = true)
    private void mk$slotClick(MouseButtonEvent event, boolean doubleClick,
                                    CallbackInfoReturnable<Boolean> cir) {
        boolean consumed = SlotScreenDispatcher.fireMouseClicked(
                (AbstractContainerScreen<?>) (Object) this,
                event.x(), event.y(), event.button());
        if (consumed) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void mk$slotScroll(double mouseX, double mouseY,
                                     double scrollX, double scrollY,
                                     CallbackInfoReturnable<Boolean> cir) {
        boolean consumed = SlotScreenDispatcher.fireMouseScrolled(
                (AbstractContainerScreen<?>) (Object) this, mouseX, mouseY, scrollX, scrollY);
        if (consumed) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z",
            at = @At("HEAD"), cancellable = true)
    private void mk$slotRelease(MouseButtonEvent event,
                                      CallbackInfoReturnable<Boolean> cir) {
        boolean consumed = SlotScreenDispatcher.fireMouseReleased(
                (AbstractContainerScreen<?>) (Object) this,
                event.x(), event.y(), event.button());
        if (consumed) {
            cir.setReturnValue(true);
        }
    }
}
