package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.core.ControlStyle;
import com.trevorschoeny.menukit.core.MKPressedTracker;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.input.MouseButtonEvent;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * <b>ACCEPTED aesthetic-only exception to §0019 (library-not-platform).</b>
 *
 * <p>Applies MenuKit's vanilla-style pressed visual (inverted bevel +
 * dark overlay) to EVERY vanilla {@link AbstractButton} in the game —
 * title screen, Options, Pause, world-select, Controls, etc. — so
 * vanilla Minecraft picks up the "button feels like it's being pushed
 * in" affordance MenuKit's own VANILLA-styled controls have.
 *
 * <h3>Why this is acceptable despite §0019</h3>
 *
 * §0019 forbids ambient consumer-facing policy defaults: MK shouldn't
 * impose behavior on consumers' UIs that they can't opt out of.
 * Forcing a pressed visual on every vanilla button is exactly that
 * kind of ambient change. Trev's carve-out (2026-05-24): the rule
 * relaxes for changes that are <b>purely aesthetic</b> — they modify
 * what gets drawn but don't intercept input, change behavior, expose
 * new APIs, or alter any callback. This mixin satisfies that test:
 * it reads existing widget state ({@code isHovered}, {@code active},
 * a press-tracker flag) and overlays a sprite on top of vanilla's
 * own draw. Nothing functional changes — pressing a button does
 * exactly what it did before, just looks slightly different mid-press.
 *
 * <p>If a future mixin under this exception starts intercepting
 * input, modifying behavior, or adding feature surface, the
 * exception no longer applies and the §0019 concerns reactivate.
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
        MKPressedTracker.markPressed(this);
    }

    @Inject(method = "renderWidget", at = @At("TAIL"))
    private void mk$drawVanillaPressedOverlay(GuiGraphics graphics, int mouseX,
                                                    int mouseY, float partialTick,
                                                    CallbackInfo ci) {
        // Press tracking via shared MKPressedTracker — the same
        // tracker the YACL mixins use, so all "vanilla-style pressed
        // visual" code paths share one source of truth.
        // isPressedAndCheckRelease auto-clears the whole map when
        // GLFW reports mouse released, so stale entries drain on the
        // next render frame (sub-perceptible).
        if (!MKPressedTracker.isPressedAndCheckRelease(this)) return;

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
