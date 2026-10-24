package com.trevlar.menukit.mixin;

import com.trevlar.menukit.inject.PresentedSlots;
import com.trevlar.menukit.inject.SlotClips;
import com.trevlar.menukit.inject.ScreenPanelRegistry;
import com.trevlar.menukit.api.host.SlotHoverResult;
import com.trevlar.menukit.inject.SlotScreenDispatcher;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Input dispatch for registered slots. Rendering needs no slot mixin: vanilla's
 * own slot pass draws a registered slot once its panel has written its
 * {@code Slot.x/y} (see {@code ContainerScreenLayers}). Targets
 * {@code AbstractContainerScreen} so it covers every container screen; creative
 * routes its {@code mouseClicked} and slot hover through the inherited
 * {@code AbstractContainerScreen} code.
 *
 * <h3>Hover ({@code getHoveredSlot})</h3>
 *
 * First the claim: when a panel claims the point and the claimant's own slot is
 * not what is there ({@code ScreenPanelRegistry.slotInertAt}), no slot is hovered.
 * This replaces the separate modal-hover mixin, so one hook decides slot hover.
 *
 * Registered slots are appended <em>last</em> to {@code menu.slots}, so where a
 * registered slot sits over a vanilla slot, vanilla's first-hit
 * {@code getHoveredSlot} would return the covered vanilla slot. The hook resolves
 * the point against the revealed registered slots first and, when one wins, returns
 * <b>the slot that is in {@code menu.slots}</b>: the raw {@code CreatedSlot} on a
 * survival inventory, or the creative {@code SlotWrapper} around it on the creative
 * screen. Creative needs the in-menu slot because its click path hard-casts the
 * hovered slot to {@code SlotWrapper}.
 * A {@code null} return for an in-panel gap makes the covered vanilla slot inert.
 *
 * <h3>Click ({@code mouseClicked})</h3>
 *
 * Eats clicks that land in a revealed panel's empty space so a carried item
 * can't drop through to the covered vanilla slot. A click on a revealed
 * <em>slot</em> is not eaten here, because {@code getHoveredSlot} above already
 * routes it to the slot.
 *
 * <p>Both forward to {@link SlotScreenDispatcher}, which does nothing until a slot
 * library installs its {@code SlotScreenHook}.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class MKSlotInputMixin {

    @Inject(method = "getHoveredSlot", at = @At("HEAD"), cancellable = true)
    private void mk$slotHover(double mouseX, double mouseY,
                                    CallbackInfoReturnable<Slot> cir) {
        // A panel claims the point: a modal anywhere, an opaque panel's whole
        // rectangle, a transparent panel's solid elements. Unless the claimant's own
        // slot is what sits there, no slot is hovered, so vanilla draws no highlight,
        // routes no click and queues no tooltip for whatever the panel covers. Asked
        // first, in this one hook, so no second getHoveredSlot injector can race it.
        if (ScreenPanelRegistry.slotInertAt((AbstractContainerScreen<?>) (Object) this, mouseX, mouseY)) {
            cir.setReturnValue(null);
            return;
        }
        SlotHoverResult result = SlotScreenDispatcher.fireResolveHover(
                (AbstractContainerScreen<?>) (Object) this, mouseX, mouseY);
        if (result.handled()) {
            // A slot claims the point: return its in-menu slot, or null for an
            // in-panel gap (covered vanilla slot inert).
            Slot slot = result.slot();
            cir.setReturnValue(PresentedSlots.underWidget((AbstractContainerScreen<?>) (Object) this, slot, mouseX, mouseY)
                    ? null : slot);
        }
    }

    /**
     * Vanilla's own answer, when nothing above decided: a background panel's slot under one
     * of the screen's widgets is not hovered (7.4.0). It draws under the widget, so the
     * point is the widget's: no highlight, tooltip or wheel for the slot it hides.
     */
    @Inject(method = "getHoveredSlot", at = @At("RETURN"), cancellable = true)
    private void mk$slotUnderWidget(double mouseX, double mouseY, CallbackInfoReturnable<Slot> cir) {
        // And a slot shown in part (across a scroll edge) only where it is shown: with no slot
        // library's hook, vanilla's own answer is the one that needs the clip (SlotClips).
        Slot slot = cir.getReturnValue();
        if (PresentedSlots.underWidget((AbstractContainerScreen<?>) (Object) this, slot, mouseX, mouseY)
                || SlotClips.hides(slot, mouseX, mouseY)) {
            cir.setReturnValue(null);
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
