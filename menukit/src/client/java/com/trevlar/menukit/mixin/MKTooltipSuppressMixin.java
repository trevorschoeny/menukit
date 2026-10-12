package com.trevlar.menukit.mixin;

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
 * MenuKit's one mixin on vanilla's tooltip pipeline. It suppresses tooltips under
 * MenuKit panels and adds MKTooltip's placement and scrolling.
 *
 * <p>Suppression cancels {@code setTooltipForNextFrameInternal} at HEAD, the private
 * method every public {@code setTooltipForNextFrame} overload calls, so one hook
 * catches every tooltip. 26.3 added a shorter overload that only forwards to the
 * full one, so the hook names the full one. Clearing the tooltip from the render path instead does not
 * work:
 *
 * <ul>
 *   <li>{@code GuiGraphicsExtractor.deferredTooltip} is a single last-write-wins
 *       {@link Runnable} field, not a queue.</li>
 *   <li>{@code CreativeModeInventoryScreen.render} sets tab tooltips after
 *       {@code super.render()} returns, which is after any clear made inside
 *       super.render.</li>
 *   <li>So later setTooltipForNextFrame calls overwrite the clear, and tab tooltips
 *       still show through a modal.</li>
 * </ul>
 *
 * <p>Suppression is scoped by the pointer: a visible modal suppresses tooltips
 * everywhere, and an opaque non-modal panel (a popover, a dropdown) only within its
 * bounds. Like the click-eating mixin it only reads per-panel flags, so two mods
 * with opaque panels coexist without either owning the hook.
 *
 * <p>The other tooltip hooks live here too. {@code tooltip}, which places and draws
 * every queued tooltip, is wrapped to dock it ({@link MKTooltip#avoidCoverWhen}),
 * keep it on screen ({@link MKTooltip#wrapWhen},
 * {@link MKTooltip#clampWhen}), add {@link MKTooltip#scrollBy}'s offset and pin the
 * title. {@code extractDeferredElements}, where a screen frame draws its tooltip,
 * marks the frame boundary MKTooltip uses to tell a new hover from the same one.
 */
@ApiStatus.Internal
@Mixin(GuiGraphicsExtractor.class)
public abstract class MKTooltipSuppressMixin {

    /**
     * Cancels a tooltip before it is queued when the pointer is inert under a
     * panel. A tooltip queued while the panel that owns the pointer is rendering is
     * that panel's own content and is let through.
     */
    @Inject(
            method = "setTooltipForNextFrameInternal(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;ZZ)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void mk$suppressTooltipWhenOpaque(CallbackInfo ci) {
        // A visible tracksAsModal panel claims the whole screen, so nothing behind
        // it shows a tooltip, including vanilla widgets outside its bounds such as
        // creative tabs. An opaque non-modal panel or an active element overlay
        // (a Dropdown popover) only suppresses within its bounds. This is the same
        // inertness check slot, tab, widget and list hover and click-eating use.
        // A tooltip queued while the pointer's owner renders is that panel's own
        // content (an element tooltip, the panel tooltip): it passes, even over the
        // panel's own claim.
        // A mod can ask for no tooltips at all right now (MKTooltip.hideWhen, such
        // as Inventory Plus while Ctrl is held). That is checked first, so a live
        // panel's own element tooltips hide too.
        if (com.trevlar.menukit.api.element.MKTooltip.hidden()) {
            ci.cancel();
            return;
        }
        if (com.trevlar.menukit.inject.PanelHost.renderingLivePanel()) return;
        if (Focus.isInertUnderPanelAtCursor()) {
            ci.cancel();
        }
    }

    // Scrolling (MKTooltip.scrollBy / onWheel)

    // Set while drawing a tooltip: a bundle draws its hovered item's tooltip from inside its own.
    @Unique private static boolean mk$drawingTooltip;

    /** Each screen frame draws its tooltip here, once: the boundary between one frame's tooltip and the next. */
    @Inject(method = "extractDeferredElements", at = @At("HEAD"))
    private void mk$tooltipFrame(int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        MKTooltip.frameStarts();
    }

    /**
     * Wraps the tooltip's text to the screen ({@link MKTooltip#wrapWhen}), then places it
     * where vanilla's positioner says (or docked, {@link MKTooltip#avoidCoverWhen}), kept on screen ({@link MKTooltip#clampWhen}), plus
     * the scroll offset, by handing vanilla a positioner that adds it. Vanilla then draws
     * the background, text and images there as usual. When scrolled with its top above the screen,
     * the first line is drawn again on a background of its own at the top edge, one
     * stratum up, so the body scrolls under a fixed title. A tooltip drawn inside
     * another (a bundle's item) moves with its parent and is otherwise left alone.
     */
    @WrapMethod(method = "tooltip")
    private void mk$scrollTooltip(Font font, java.util.List<ClientTooltipComponent> lines, int mouseX, int mouseY,
                                  ClientTooltipPositioner positioner, @Nullable Identifier sprite,
                                  boolean extraSpaceAfterFirstLine, Operation<Void> original) {
        if (mk$drawingTooltip || lines.isEmpty()) {
            original.call(font, lines, mouseX, mouseY, positioner, sprite, extraSpaceAfterFirstLine);
            return;
        }
        mk$drawingTooltip = true;
        try {
            // Keyed on what the tooltip says, before wrapping: a resize re-wraps the same tooltip.
            MKTooltip.tooltipStarts(MKTooltip.contentKey(font, lines));
            GuiGraphicsExtractor self = (GuiGraphicsExtractor) (Object) this;
            // MKTooltip.wrapWhen: lines wider than the screen re-split before vanilla measures
            // and places them; a wrapped line stays one component, so the title is still lines[0].
            java.util.List<ClientTooltipComponent> shown = MKTooltip.wraps() ? MKTooltip.wrapped(font, lines, self.guiWidth()) : lines;
            // MKTooltip.avoidCoverWhen: a beside-the-cursor tooltip docks at the bottom (or top) centre.
            boolean dock = MKTooltip.docks(positioner);
            int[] drawn = new int[3];  // x, y, width, as vanilla placed them after the offset
            ClientTooltipPositioner scrolled = (screenW, screenH, x, y, w, h) -> {
                Vector2ic placed = dock ? MKTooltip.docked(screenW, screenH, x, y, w, h)
                        : positioner.positionTooltip(screenW, screenH, x, y, w, h);
                Vector2ic at = MKTooltip.scrolled(placed, w, h, screenW, screenH);
                drawn[0] = at.x();
                drawn[1] = at.y();
                drawn[2] = w;
                return at;
            };
            original.call(font, shown, mouseX, mouseY, scrolled, sprite, extraSpaceAfterFirstLine);
            if (shown.size() > 1 && MKTooltip.pinsTitle(drawn[1])) {
                ClientTooltipComponent title = shown.get(0);
                self.nextStratum();  // above the body's text, which draws over anything in its own stratum
                TooltipRenderUtil.extractTooltipBackground(self, drawn[0], MKTooltip.EDGE, drawn[2], title.getHeight(font), sprite);
                title.extractText(self, font, drawn[0], MKTooltip.EDGE);
            }
        } finally {
            mk$drawingTooltip = false;
        }
    }
}
