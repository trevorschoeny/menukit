package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.inject.ScreenPanelRegistry;

import net.minecraft.client.gui.GuiGraphics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Phase 14d-1 modal tooltip suppression — HEAD-cancellable mixin into
 * {@code GuiGraphics.setTooltipForNextFrameInternal}.
 *
 * <h3>Why this mixin (not the originally-planned queue-clear)</h3>
 *
 * Round-2 verdict on Finding C originally preferred a queue-clearing
 * approach over a tooltip-pipeline mixin: render path is library-owned,
 * mixin would be vanilla-code-path-flavored. Implementation surfaced that
 * the queue-clear approach didn't cover the full flow:
 *
 * <ul>
 *   <li>{@code GuiGraphics.deferredTooltip} is a single
 *       last-write-wins {@link Runnable} field, not a queue.</li>
 *   <li>{@code CreativeModeInventoryScreen.render} queues tab-hover
 *       tooltips AFTER {@code super.render()} returns — and thus AFTER
 *       our render-path clear (which fires inside super.render via the
 *       existing INVOKE renderCarriedItem injection point).</li>
 *   <li>Subsequent setTooltipForNextFrame calls overwrite our clear,
 *       so tab tooltips still render through the modal.</li>
 * </ul>
 *
 * <p>Suppressing at the queueing site — HEAD-cancellable on the private
 * {@code setTooltipForNextFrameInternal} method that all public
 * {@code setTooltipForNextFrame} overloads delegate to — is the robust
 * mechanism. Single mixin point catches every tooltip queue call.
 *
 * <h3>Library-not-platform check</h3>
 *
 * Same shape as the click-eat mixin: library-wide HEAD-cancellable
 * inject gated on per-Panel modal flag (consulted via
 * {@link ScreenPanelRegistry#hasAnyVisibleModal()}). Two mods both
 * shipping modal panels coexist independently; the mixin checks "any
 * visible modal panel" without taking ownership across mods. The mixin
 * is observational/dispatch-policy at a single hook point.
 *
 * <h3>Round-2 implementation finding (filed in DIALOGS.md §10)</h3>
 *
 * The advisor's preference for queue-clear over mixin was based on a
 * model where {@code deferredTooltip} could be cleared once and stay
 * cleared. Implementation revealed the field is overwriteable per-frame.
 * Switching to mixin-based suppression preserves the architectural
 * intent (modality is a per-Panel property; library-wide dispatch
 * mechanism makes it work) while delivering on the modal contract.
 */
@Mixin(GuiGraphics.class)
public abstract class MenuKitTooltipSuppressMixin {

    /**
     * Fires at HEAD of every tooltip-queue call. When a visible modal
     * panel is present on the current screen, cancel the call — the
     * tooltip never gets queued, so it never renders. Modal-internal
     * tooltips (e.g., button.tooltip on a dialog button) are also
     * suppressed by this v1 — modal dialogs don't typically have
     * hover tooltips on their elements; ConfirmDialog/AlertDialog
     * buttons are labeled rather than tooltipped. If smoke surfaces
     * a need for modal-internal tooltips, fold-on-evidence (e.g.,
     * coord-based suppression: only cancel when tooltip position is
     * outside modal bounds).
     */
    @Inject(
            method = "setTooltipForNextFrameInternal",
            at = @At("HEAD"),
            cancellable = true
    )
    private void menukit$suppressTooltipWhenModal(CallbackInfo ci) {
        if (ScreenPanelRegistry.hasAnyVisibleModal()) {
            ci.cancel();
        }
    }
}
