package com.trevlar.menukit.mixin;

import com.trevlar.menukit.inject.ScreenPanelRegistry;
import com.trevlar.menukit.api.panel.Focus;

import com.trevlar.menukit.api.element.MKTooltip;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.joml.Vector2ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Phase 14d-1 modal tooltip suppression — HEAD-cancellable mixin into
 * {@code GuiGraphicsExtractor.setTooltipForNextFrameInternal}.
 *
 * <h3>Why this mixin (not the originally-planned queue-clear)</h3>
 *
 * Round-2 verdict on Finding C originally preferred a queue-clearing
 * approach over a tooltip-pipeline mixin: render path is library-owned,
 * mixin would be vanilla-code-path-flavored. Implementation surfaced that
 * the queue-clear approach didn't cover the full flow:
 *
 * <ul>
 *   <li>{@code GuiGraphicsExtractor.deferredTooltip} is a single
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
 * <h3>The other tooltip seams on this class</h3>
 *
 * This class is MenuKit's one mixin on vanilla's tooltip pipeline (§0064), so the
 * scroll seam lives here too: {@code tooltip} (the method that places and draws every
 * queued tooltip) is wrapped to add {@link MKTooltip#scrollBy}'s offset and pin the
 * title, and {@code extractDeferredElements} (where a screen frame draws its
 * tooltip) marks the frame boundary MKTooltip uses to tell a new hover from the same one.
 *
 * <h3>Library-not-platform check</h3>
 *
 * Same shape as the click-eat mixin: library-wide HEAD-cancellable
 * inject gated on per-Panel opacity (consulted via
 * {@link ScreenPanelRegistry#anyPanelCoversCursor()}). Two
 * mods both shipping opaque panels coexist independently; the mixin
 * checks "any visible opaque panel covers the cursor" without taking
 * ownership across mods. The mixin is observational/dispatch-policy
 * at a single hook point.
 *
 * <p>M9 generalization: scope changed from "any modal visible" (global
 * suppression) to "cursor inside any opaque panel" (bounds-localized).
 * Modal dialogs still suppress correctly because they're opaque and
 * cover their bounds; non-modal opaque panels (popovers, dropdowns)
 * also suppress within their bounds. See M9 §4.7.
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
@ApiStatus.Internal
@Mixin(GuiGraphicsExtractor.class)
public abstract class MKTooltipSuppressMixin {

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
    private void mk$suppressTooltipWhenOpaque(CallbackInfo ci) {
        // M9: pointer-driven tooltip suppression honors both panel scopes:
        //   - tracksAsModal panel visible → suppress GLOBALLY (modal
        //     claims the whole screen; tooltips behind don't render per
        //     Trevor's principle, including for vanilla widgets like
        //     creative tabs that lie outside the modal's bounds).
        //   - else if cursor inside any visible opaque panel → suppress
        //     LOCALLY (bounds-local for non-modal opaque panels — items
        //     outside the popover still tooltip normally).
        //   - else → don't suppress; vanilla tooltip queues normally.
        // See M9 §4.7 for the scope-asymmetry framing.
        // Post-Phase 18r-5: the predicate includes ACTIVE ELEMENT OVERLAYS
        // (Dropdown popovers, etc.) in addition to panel bounds. Now routed
        // through the single inertness predicate (modal-global OR covered by an
        // opaque panel/element/overlay) — the same question slot hover, tab
        // hover, widget hover, list hover, and the click-eat all ask.
        // A tooltip queued while the pointer's owner renders is that panel's own
        // content (an element tooltip, the panel tooltip): it passes, even over the
        // panel's own claim. Everything else under a claim is suppressed.
        // A mod asked for no tooltips at all right now (MKTooltip.hideWhen, e.g.
        // Inventory Plus while Ctrl is held). Asked first, so a live panel's own
        // element tooltips hide too: the player asked for none.
        if (com.trevlar.menukit.api.element.MKTooltip.hidden()) {
            ci.cancel();
            return;
        }
        if (com.trevlar.menukit.inject.PanelHost.renderingLivePanel()) return;
        if (Focus.isInertUnderPanelAtCursor()) {
            ci.cancel();
        }
    }

    // ── Scrolling (MKTooltip.scrollBy / onWheel) ─────────────────────────

    /** Inside a tooltip's draw: a bundle draws its hovered item's tooltip from inside its own. */
    @Unique private static boolean mk$drawingTooltip;

    /** Each screen frame draws its tooltip here, once: the boundary between one frame's tooltip and the next. */
    @Inject(method = "extractDeferredElements", at = @At("HEAD"))
    private void mk$tooltipFrame(int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        MKTooltip.frameStarts();
    }

    /**
     * Places the tooltip where vanilla's positioner says, plus the scroll offset, by
     * handing vanilla a positioner that adds it; vanilla then draws the background,
     * text and images there as always. When scrolled with its top above the screen,
     * the first line is drawn again on a background of its own at the top edge, one
     * stratum up, so the body scrolls under a fixed title. A tooltip drawn inside
     * another (a bundle's item) moves with its parent and is otherwise left alone.
     */
    @WrapMethod(method = "tooltip")
    private void mk$scrollTooltip(Font font, java.util.List<ClientTooltipComponent> lines, int mouseX, int mouseY,
                                  ClientTooltipPositioner positioner, @Nullable Identifier sprite,
                                  Operation<Void> original) {
        if (mk$drawingTooltip || lines.isEmpty()) {
            original.call(font, lines, mouseX, mouseY, positioner, sprite);
            return;
        }
        mk$drawingTooltip = true;
        try {
            MKTooltip.tooltipStarts(MKTooltip.contentKey(font, lines));
            int[] drawn = new int[3];  // x, y, width, as vanilla placed them after the offset
            ClientTooltipPositioner scrolled = (screenW, screenH, x, y, w, h) -> {
                Vector2ic at = MKTooltip.scrolled(positioner.positionTooltip(screenW, screenH, x, y, w, h), w, h, screenW, screenH);
                drawn[0] = at.x();
                drawn[1] = at.y();
                drawn[2] = w;
                return at;
            };
            original.call(font, lines, mouseX, mouseY, scrolled, sprite);
            if (lines.size() > 1 && MKTooltip.pinsTitle(drawn[1])) {
                GuiGraphicsExtractor self = (GuiGraphicsExtractor) (Object) this;
                ClientTooltipComponent title = lines.get(0);
                self.nextStratum();  // above the body's text, which draws over anything in its own stratum
                TooltipRenderUtil.extractTooltipBackground(self, drawn[0], MKTooltip.EDGE, drawn[2], title.getHeight(font), sprite);
                title.extractText(self, font, drawn[0], MKTooltip.EDGE);
            }
        } finally {
            mk$drawingTooltip = false;
        }
    }
}
