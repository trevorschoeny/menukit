package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.inject.ScreenPanelRegistry;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Phase 14d-1 modal input pre-emption — KeyboardHandler half.
 *
 * <p>Same architectural shape as {@link MenuKitModalMouseHandlerMixin}: when
 * a modal panel is visible, eat keyboard events at the input dispatch root
 * before they route to screen-specific keyPressed handlers. Modal blocks
 * underlying screen keyboard interaction.
 *
 * <h3>Escape key — allowed through</h3>
 *
 * Round-2 advisor verdict: v1 ships modal without keyboard suppression of
 * Escape — "Escape closing the underlying screen is acceptable v1
 * behavior." Initial round-3 implementation eat-all-keys was wrong on
 * this point; smoke surfaced "can't exit anything" frustration. Fix:
 * Escape (GLFW key 256) is allowed through; other keys eaten.
 *
 * <p>Note: Escape closing the underlying inventory screen also dismisses
 * the dialog visually (the dialog adapter is bound to the screen). The
 * consumer's {@code dialogVisible} state isn't reset by Escape — on next
 * inventory open, the dialog reappears if still flagged visible. Consumer
 * can wire a screen-close listener to reset state if that's undesired.
 *
 * <p>If smoke surfaces other keys that should pass through (e.g., chat
 * key while dialog is up, hotbar number-keys for some reason), fold a
 * per-key allowlist post-smoke.
 */
@Mixin(KeyboardHandler.class)
public abstract class MenuKitModalKeyboardHandlerMixin {

    /** GLFW key code for Escape — allowed to pass through modal eating. */
    private static final int GLFW_KEY_ESCAPE = 256;

    @Inject(
            method = "keyPress",
            at = @At("HEAD"),
            cancellable = true
    )
    private void menukit$eatModalKey(long window, int action, KeyEvent event,
                                     CallbackInfo ci) {
        if (event.key() == GLFW_KEY_ESCAPE) return; // Always allow Escape.
        // M9: gate on tracksAsModal (window-state suppression scoped to
        // modal-tracking panels per §4.7). Non-modal opaque panels do NOT
        // suppress keyboard — keyboard isn't pointer-driven and can't
        // localize to bounds; eating keys whenever any opaque panel is
        // visible would suppress every keystroke whenever any non-modal
        // MK decoration is visible (overly aggressive).
        if (ScreenPanelRegistry.hasAnyVisibleModalTracking()) {
            ci.cancel();
        }
    }
}
