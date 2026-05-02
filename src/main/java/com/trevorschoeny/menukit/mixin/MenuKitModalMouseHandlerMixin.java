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
     * routing to the active screen. Press and release events handled
     * symmetrically:
     *
     * <ul>
     *   <li><b>Press</b> — eat (and dispatch to opaque panel's elements if
     *       inside bounds) so vanilla never starts its own drag-state.</li>
     *   <li><b>Release</b> — eat under the same conditions (opaque-at-
     *       cursor OR modal-tracking visible), AND dispatch to opaque
     *       adapters' elements so in-progress drags end correctly. When
     *       neither condition holds, release passes through to vanilla
     *       → Fabric {@code allowMouseRelease} (existing path).</li>
     * </ul>
     *
     * <p>{@code action} GLFW values: 0 = release, 1 = press, 2 = repeat.
     *
     * <p><b>Why release handling needs symmetry (smoke fold-inline finding):</b>
     * Pre-M9 14d-1 ate everything when modal was up — releases included.
     * M9's first cut passed releases through unconditionally to fix
     * ScrollContainer drag-end. That broke modal tab-blocking because
     * vanilla {@code CreativeModeInventoryScreen.mouseReleased} is what
     * selects tabs (not {@code mouseClicked}). Symmetric handling: when
     * we'd eat the press, also eat the release AND manually dispatch
     * {@code mouseReleased} to opaque adapters' elements (since canceling
     * onButton means Fabric's {@code allowMouseRelease} can't fire).
     * Pure non-modal non-opaque releases still pass through normally.
     */
    @Inject(
            method = "onButton",
            at = @At("HEAD"),
            cancellable = true
    )
    private void menukit$eatModalButton(long window, MouseButtonInfo button, int action,
                                        CallbackInfo ci) {
        if (action == 0) {
            // Release: dispatch to opaque adapters for drag-end + eat
            // when we'd have eaten the press. Returns false (passthrough)
            // when no opaque covers cursor and no modal-tracking visible.
            if (releaseAtCurrentMouse(button.button())) {
                ci.cancel();
            }
            return;
        }
        // Press / repeat:
        if (eatInputAtCurrentMouse(button.button())) {
            ci.cancel();
        }
    }

    /**
     * Computes scaled mouse coordinates and dispatches release via
     * {@link com.trevorschoeny.menukit.inject.ScreenPanelRegistry#dispatchOpaqueRelease}.
     * Returns whether the release should be eaten at this layer.
     */
    private boolean releaseAtCurrentMouse(int button) {
        var mc = Minecraft.getInstance();
        if (mc == null) return false;
        var window = mc.getWindow();
        if (window == null) return false;
        double scaledX = xpos * window.getGuiScaledWidth() / window.getScreenWidth();
        double scaledY = ypos * window.getGuiScaledHeight() / window.getScreenHeight();
        var screen = mc.screen;
        if (screen == null) return false;
        return ScreenPanelRegistry.dispatchOpaqueRelease(screen, scaledX, scaledY, button);
    }

    @Inject(
            method = "onScroll",
            at = @At("HEAD"),
            cancellable = true
    )
    private void menukit$eatModalScroll(long window, double xOffset, double yOffset,
                                        CallbackInfo ci) {
        // M9: opaque-scroll dispatch. Cursor inside an opaque panel →
        // dispatch scroll to that panel's elements (so a ScrollContainer
        // inside a non-modal opaque panel scrolls); cursor outside +
        // modal-tracking visible → eat; cursor outside + no modal-tracking
        // → pass through (Fabric allowMouseScroll handles non-opaque
        // dispatch via ScreenPanelRegistry.onScreenInit).
        var mc = Minecraft.getInstance();
        if (mc == null || mc.screen == null) return;
        var mcWindow = mc.getWindow();
        if (mcWindow == null) return;
        // Compute scaled coords from current mouse position — getScreenWidth
        // /getScreenHeight (logical window pixels) for HiDPI correctness.
        double scaledX = xpos * mcWindow.getGuiScaledWidth() / mcWindow.getScreenWidth();
        double scaledY = ypos * mcWindow.getGuiScaledHeight() / mcWindow.getScreenHeight();
        if (ScreenPanelRegistry.dispatchOpaqueScroll(mc.screen, scaledX, scaledY, xOffset, yOffset)) {
            ci.cancel();
        }
    }

    /**
     * Computes scaled mouse coordinates from raw window position, queries
     * the M9 opacity dispatch, and returns whether the input should be
     * eaten. Returns {@code false} when no opaque panel covers the cursor
     * AND no tracksAsModal panel is visible — vanilla dispatch proceeds
     * normally.
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
        return ScreenPanelRegistry.dispatchOpaqueClick(screen, scaledX, scaledY, button);
    }
}
