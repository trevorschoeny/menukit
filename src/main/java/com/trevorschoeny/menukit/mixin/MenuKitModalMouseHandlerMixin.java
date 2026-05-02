package com.trevorschoeny.menukit.mixin;

import com.trevorschoeny.menukit.inject.ScreenPanelRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Phase 14d-1 modal input pre-emption — mixin into {@link MouseHandler}
 * at the input dispatch root, BEFORE per-screen routing.
 *
 * <h3>Why MouseHandler-level (not Screen-level)</h3>
 *
 * Round-3 verdict on the structural-modal finding: per-Screen mouseClicked
 * mixins are fragile because every {@code AbstractContainerScreen} subclass
 * that overrides {@code mouseClicked} without super-calling pre-empts the
 * mixin chain (the silent-inert dispatch failure mode CONTEXTS.md
 * documents). {@link net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen}
 * is one example — its tab handling pre-empts super, so a HEAD-cancellable
 * mixin on the parent {@code mouseClicked} never fires for tab clicks.
 *
 * <p>Mixing into {@link MouseHandler#onButton} HEAD intercepts BEFORE any
 * per-screen dispatch — the click hasn't been routed to a screen yet.
 * Subclass overrides become irrelevant. Single hook covers every screen
 * (vanilla and modded) uniformly.
 *
 * <p>Same shape for {@code onScroll} — scroll-wheel events route through
 * {@link MouseHandler#onScroll} before reaching screen-specific scroll
 * handlers. Modal up + scroll outside modal = eaten.
 *
 * <h3>Library-not-platform check (round-3 audit)</h3>
 *
 * Per-Panel modal flag is the consumer surface; the input-handler mixin is
 * library-wide dispatch policy. Two mods both shipping modal panels coexist
 * via structural independence — each mod's mixin sees the same Panel
 * registry; "any visible modal" means "any panel with the flag set" across
 * all mods. Each mod's modal works on its own. The mixin is observational
 * (consults flags, decides eat-or-pass), not ownership of vanilla input
 * routing.
 *
 * <h3>Inside-modal click handling</h3>
 *
 * When the click coordinate lands inside a visible modal's bounds:
 * <ol>
 *   <li>Dispatch the click to the modal's adapter (so its element layer
 *       gets the click — Confirm button fires, etc.)</li>
 *   <li>Cancel — vanilla's screen.mouseClicked never sees this click.
 *       No slot pickup, no tab switch, no underlying interaction.</li>
 * </ol>
 *
 * <h3>Coordinate conversion</h3>
 *
 * {@link MouseHandler#xpos} and {@code ypos} are raw window coordinates
 * (GLFW pixel-space). Screens use GUI-scaled coordinates. Conversion:
 * {@code scaled = raw * window.getGuiScaledWidth() / window.getWidth()}
 * (and the height-axis equivalent).
 */
@Mixin(MouseHandler.class)
public abstract class MenuKitModalMouseHandlerMixin {

    @Shadow private double xpos;
    @Shadow private double ypos;

    /**
     * Fires at HEAD of {@code onButton} — every mouse-button event, before
     * routing to the active screen. Eats clicks outside modal bounds;
     * dispatches clicks inside modal bounds to the modal's adapter then
     * eats them (so the underlying screen never sees them).
     *
     * <p>{@code action} GLFW values: 0 = release, 1 = press, 2 = repeat.
     * The current logic eats both press and release symmetrically — modal
     * blocks all button events. If smoke surfaces a need for differentiated
     * handling (e.g., let release through), fold-on-evidence.
     */
    @Inject(
            method = "onButton",
            at = @At("HEAD"),
            cancellable = true
    )
    private void menukit$eatModalButton(long window, MouseButtonInfo button, int action,
                                        CallbackInfo ci) {
        if (eatInputAtCurrentMouse(button.button())) {
            ci.cancel();
        }
    }

    @Inject(
            method = "onScroll",
            at = @At("HEAD"),
            cancellable = true
    )
    private void menukit$eatModalScroll(long window, double xOffset, double yOffset,
                                        CallbackInfo ci) {
        // Phase 14d-2 fold-inline: modal-aware scroll dispatch. Round-3
        // 14d-1 was wholesale-eat-when-modal because dialogs had no
        // scrollable content. ScrollContainer-inside-modal needs scroll
        // INSIDE modal bounds to reach the modal's elements. Same dispatch
        // shape as click-eat (dispatchModalClick): inside modal → dispatch
        // to modal's adapter then eat; outside modal → eat without dispatch;
        // no modal → pass through (Fabric allowMouseScroll handles non-modal
        // scroll dispatch via ScreenPanelRegistry.onScreenInit).
        var mc = Minecraft.getInstance();
        if (mc == null || mc.screen == null) return;
        if (!ScreenPanelRegistry.hasAnyVisibleModal()) return;
        // Compute scaled coords from current mouse position (same formula
        // as the click mixin — getScreenWidth/Height for HiDPI correctness).
        var mcWindow = mc.getWindow();
        if (mcWindow == null) return;
        double scaledX = xpos * mcWindow.getGuiScaledWidth() / mcWindow.getScreenWidth();
        double scaledY = ypos * mcWindow.getGuiScaledHeight() / mcWindow.getScreenHeight();
        if (ScreenPanelRegistry.dispatchModalScroll(mc.screen, scaledX, scaledY, xOffset, yOffset)) {
            ci.cancel();
        }
    }

    /**
     * Computes scaled mouse coordinates from raw window position, queries
     * the modal dispatch, and returns whether the input should be eaten.
     * Returns {@code false} when no modal is visible — vanilla dispatch
     * proceeds normally.
     */
    private boolean eatInputAtCurrentMouse(int button) {
        var mc = Minecraft.getInstance();
        if (mc == null) return false;
        var window = mc.getWindow();
        if (window == null) return false;
        // Convert raw window coords to GUI-scaled coords. Use
        // getScreenWidth/getScreenHeight (logical window pixels), NOT
        // getWidth/getHeight (framebuffer pixels — gives wrong scaling on
        // HiDPI/Retina displays). Vanilla MouseHandler.onButton uses the
        // screen-width formula too.
        double scaledX = xpos * window.getGuiScaledWidth() / window.getScreenWidth();
        double scaledY = ypos * window.getGuiScaledHeight() / window.getScreenHeight();
        var screen = mc.screen;
        if (screen == null) return false;
        return ScreenPanelRegistry.dispatchModalClick(screen, scaledX, scaledY, button);
    }
}
