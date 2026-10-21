package com.trevlar.menukit.mixin;

import com.trevlar.menukit.core.PressedTracker;
import com.trevlar.menukit.inject.PanelEditing;
import com.trevlar.menukit.inject.ScreenPanelRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;

import org.jetbrains.annotations.ApiStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Eats mouse input under MenuKit panels at {@link MouseHandler}, before vanilla
 * routes it to the screen.
 *
 * <p>Hooking each screen's mouseClicked would be fragile: any
 * {@code AbstractContainerScreen} subclass that overrides {@code mouseClicked}
 * without calling super skips the hook. {@link net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen}
 * is one: its tab handling never calls super, so a HEAD injection on the parent
 * {@code mouseClicked} never sees tab clicks. At the HEAD of
 * {@link MouseHandler#onButton} the click hasn't reached any screen yet, so one
 * hook covers every screen, vanilla or modded. Scrolling works the same way through
 * {@link MouseHandler#onScroll}.
 *
 * <p>The modal flag is per panel; this mixin only reads it. Two mods that both
 * ship modal panels coexist, because the mixin checks one shared panel registry and
 * "any visible modal" means any panel with the flag, from any mod.
 *
 * <p>A click inside a visible modal's bounds goes to the modal's adapter (so its
 * Confirm button fires and so on) and is then cancelled, so the screen's
 * mouseClicked never sees it: no slot pickup, no tab switch.
 *
 * <p>{@link MouseHandler#xpos} and {@code ypos} are raw window coordinates, while
 * screens use GUI-scaled ones:
 * {@code scaled = raw * window.getGuiScaledWidth() / window.getScreenWidth()},
 * and the same for height.
 */
@ApiStatus.Internal
@Mixin(MouseHandler.class)
public abstract class MKModalMouseHandlerMixin {

    @Shadow private double xpos;
    @Shadow private double ypos;

    /**
     * Runs for every mouse button event before it reaches the screen. Presses and
     * releases are treated alike:
     *
     * <ul>
     *   <li><b>Press:</b> eaten (and sent to an opaque panel's elements when
     *       inside it), so vanilla never starts its own drag.</li>
     *   <li><b>Release:</b> eaten under the same conditions (opaque panel at the
     *       cursor, or a modal visible) and sent to the opaque panels' elements so
     *       drags in progress end. Otherwise it passes through to vanilla and
     *       Fabric's {@code allowMouseRelease}.</li>
     * </ul>
     *
     * <p>{@code action} values: 0 = release, 1 = press.
     *
     * <p>Every event is also handed to {@link PressedTracker} first, cancelled or
     * not, since that is the only record of whether the left button is held.
     *
     * <p>Releases have to match presses. Letting every release through (to end
     * ScrollContainer drags) broke modal tab blocking, because creative selects a
     * tab in {@code mouseReleased}, not {@code mouseClicked}. So a release is eaten
     * whenever its press would be, and {@code mouseReleased} is sent to the opaque
     * panels' elements by hand, since cancelling onButton keeps Fabric's
     * {@code allowMouseRelease} from firing.
     */
    @Inject(
            method = "onButton",
            at = @At("HEAD"),
            cancellable = true
    )
    private void mk$eatModalButton(long window, MouseButtonInfo button, int action,
                                        CallbackInfo ci) {
        PressedTracker.onMouseButton(button.button(), action, button.modifiers());
        // Edit mode (7.4.0) takes every button on its screen, before the screen, its widgets,
        // its slots or any other mod's hook could: here, ahead of every screen's own handling.
        if (editModeTakes(button, action)) {
            ci.cancel();
            return;
        }
        if (action == 0) {
            // Release: ends drags in opaque panels, and is eaten when the press
            // would have been. Passes through when nothing opaque covers the
            // cursor and no modal is visible.
            if (releaseAtCurrentMouse(button.button())) {
                ci.cancel();
            }
            return;
        }
        if (eatInputAtCurrentMouse(button.button())) {
            ci.cancel();
        }
    }

    /** Hands the event to {@link PanelEditing}, at the cursor in GUI pixels; true to eat it. */
    private boolean editModeTakes(MouseButtonInfo button, int action) {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.gui.screen() == null || mc.getWindow() == null) return false;
        var window = mc.getWindow();
        double scaledX = xpos * window.getGuiScaledWidth() / window.getScreenWidth();
        double scaledY = ypos * window.getGuiScaledHeight() / window.getScreenHeight();
        // Shift from the event's modifiers, or the key itself held (what a client gametest's held Shift sets).
        boolean shift = button.hasShiftDown() || com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_RSHIFT);
        return PanelEditing.onButton(button.button(), action, shift, scaledX, scaledY);
    }

    /** Sends the release to {@link com.trevlar.menukit.inject.ScreenPanelRegistry#dispatchCoveredRelease}; true to eat it. */
    private boolean releaseAtCurrentMouse(int button) {
        var mc = Minecraft.getInstance();
        if (mc == null) return false;
        var window = mc.getWindow();
        if (window == null) return false;
        double scaledX = xpos * window.getGuiScaledWidth() / window.getScreenWidth();
        double scaledY = ypos * window.getGuiScaledHeight() / window.getScreenHeight();
        var screen = mc.gui.screen();
        if (screen == null) return false;
        return ScreenPanelRegistry.dispatchCoveredRelease(screen, scaledX, scaledY, button);
    }

    @Inject(
            method = "onScroll",
            at = @At("HEAD"),
            cancellable = true
    )
    private void mk$eatModalScroll(long window, double xOffset, double yOffset,
                                        CallbackInfo ci) {
        // Cursor inside an opaque panel: the scroll goes to that panel's elements
        // (so a ScrollContainer in a non-modal opaque panel scrolls). Outside, with
        // a modal visible: eaten. Otherwise it passes through, and Fabric's
        // allowMouseScroll (registered in ScreenPanelRegistry.onScreenInit)
        // handles non-opaque panels.
        var mc = Minecraft.getInstance();
        if (mc == null || mc.gui.screen() == null) return;
        // Edit mode (7.4.0): nothing on its screen takes the wheel either.
        if (PanelEditing.active()) {
            ci.cancel();
            return;
        }
        // The wheel moved on a screen: tell MKTooltip.onWheel listeners first, so a
        // mod scrolling a tooltip hears every turn, consumed below or not. Scaled the
        // way vanilla scales it for screens (discrete scrolling, wheel sensitivity).
        boolean discrete = mc.options.discreteMouseScroll().get();
        double sensitivity = mc.options.mouseWheelSensitivity().get();
        com.trevlar.menukit.api.element.MKTooltip.wheelMoved(
                (discrete ? Math.signum(xOffset) : xOffset) * sensitivity,
                (discrete ? Math.signum(yOffset) : yOffset) * sensitivity);
        var mcWindow = mc.getWindow();
        if (mcWindow == null) return;
        // getScreenWidth/getScreenHeight (logical window pixels) for HiDPI.
        double scaledX = xpos * mcWindow.getGuiScaledWidth() / mcWindow.getScreenWidth();
        double scaledY = ypos * mcWindow.getGuiScaledHeight() / mcWindow.getScreenHeight();
        if (ScreenPanelRegistry.dispatchCoveredScroll(mc.gui.screen(), scaledX, scaledY, xOffset, yOffset)) {
            ci.cancel();
        }
    }

    /**
     * Whether to eat the press. False when no opaque panel covers the cursor and no
     * tracksAsModal panel is visible, so vanilla handles it as usual.
     */
    private boolean eatInputAtCurrentMouse(int button) {
        var mc = Minecraft.getInstance();
        if (mc == null) return false;
        var window = mc.getWindow();
        if (window == null) return false;
        // getScreenWidth/getScreenHeight (logical window pixels), not
        // getWidth/getHeight (framebuffer pixels, which scale wrongly on HiDPI
        // displays). Vanilla's MouseHandler.onButton uses the same formula.
        double scaledX = xpos * window.getGuiScaledWidth() / window.getScreenWidth();
        double scaledY = ypos * window.getGuiScaledHeight() / window.getScreenHeight();
        var screen = mc.gui.screen();
        if (screen == null) return false;
        return ScreenPanelRegistry.dispatchCoveredClick(screen, scaledX, scaledY, button);
    }
}
