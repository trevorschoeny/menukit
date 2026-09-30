package com.trevlar.menukit.mixin;

import com.trevlar.menukit.api.element.ControlStyle;
import com.trevlar.menukit.core.PressedTracker;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.input.MouseButtonEvent;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <b>MenuKit's one styling exception to §0019, recorded in §0068.</b>
 *
 * <p>Applies MenuKit's vanilla-style pressed visual (inverted bevel and a dark
 * overlay) to every vanilla {@link AbstractButton} in the game (title screen,
 * Options, Pause, world select, Controls), so a vanilla button looks pushed in
 * while pressed, the same as MenuKit's own VANILLA-styled controls. With the two
 * YACL mixins it is library-wide and always on, and it changes how buttons MenuKit
 * did not create look. §0068 keeps it (Trev, 2026-09-27: players see it as part of
 * MenuKit's feel) and records it so it stays the only one: a second styling change
 * to vanilla needs its own record.
 *
 * <p>It is visual only. It reads existing widget state ({@code isHovered},
 * {@code active}, a press-tracker flag) and draws over vanilla's own draw;
 * pressing a button does exactly what it did before. A change here that
 * intercepted input or changed behaviour would fall outside the exception.
 *
 * <h3>Known costs we accept</h3>
 *
 * <ol>
 *   <li>Consumers can't opt out (no toggle exposed). MK-using mods
 *       carry the visual to every screen.</li>
 *   <li>Multi-mod coexistence: if another mod also overlays vanilla
 *       AbstractButton, our overlay and theirs may both paint. Both
 *       being purely visual, the result is layered overlays — visually
 *       weird but non-functional.</li>
 *   <li>Vanilla AbstractButton.renderWidget is the injection target;
 *       Mojang refactoring there breaks the mixin (loud failure at
 *       load, not silent — easy to detect on a vanilla update).</li>
 * </ol>
 *
 * <h3>Mechanism</h3>
 *
 * Per-instance press tracking via a {@link Unique} field
 * {@code mk$pressed}, set on {@code onClick} (when the press
 * originates on THIS button via vanilla's dispatch) and cleared in
 * the next render frame after the mouse is released. The render-time
 * draw is gated on hover so dragging off the button while holding
 * removes the visual (matching vanilla button behavior); dragging
 * back over re-shows it.
 *
 * <p>The earlier GLFW-poll-only approach had a false positive: click
 * elsewhere, drag over a button while holding → pressed visual fired
 * even though click didn't originate on the button. Press-state
 * tracking via onClick fixes that — the flag only sets when vanilla's
 * own dispatch routed the click to this button.
 */
@ApiStatus.Internal
@Mixin(AbstractButton.class)
public abstract class MKVanillaButtonPressedMixin {

    @Inject(method = "onClick", at = @At("TAIL"))
    private void mk$markPressed(MouseButtonEvent event, boolean alreadyHandled,
                                      CallbackInfo ci) {
        // Vanilla's dispatch only calls onClick when isMouseOver is true,
        // so reaching here means the press originated on this button.
        PressedTracker.markPressed(this);
    }

    @Inject(method = "extractWidgetRenderState", at = @At("TAIL"))
    private void mk$drawVanillaPressedOverlay(GuiGraphicsExtractor graphics, int mouseX,
                                                    int mouseY, float partialTick,
                                                    CallbackInfo ci) {
        // Press tracking via shared PressedTracker — the same
        // tracker the YACL mixins use, so all "vanilla-style pressed
        // visual" code paths share one source of truth.
        // isPressedAndCheckRelease auto-clears the whole map when
        // GLFW reports mouse released, so stale entries drain on the
        // next render frame (sub-perceptible).
        if (!PressedTracker.isPressedAndCheckRelease(this)) return;

        // Don't draw the overlay when the user has dragged off the
        // button (mouse still held but no longer over us). Matches
        // vanilla button behavior — dragging off mid-press removes
        // the hover visual; dragging back re-applies it.
        AbstractButton self = (AbstractButton) (Object) this;
        if (!self.active) return;
        if (!self.isHovered()) return;

        ControlStyle.renderVanillaPressedOverlay(graphics,
                self.getX(), self.getY(),
                self.getWidth(), self.getHeight());
    }
}
