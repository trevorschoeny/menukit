package com.trevlar.menukit.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.trevlar.menukit.mixin.MouseHandlerCursorPosAccessor;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;

import org.jspecify.annotations.Nullable;
import org.jetbrains.annotations.ApiStatus;

/**
 * Keeps the OS cursor where it was across screen transitions. Every screen's
 * {@code removed()} stashes the cursor position, and the end of the screen change that
 * opens the next one (any screen: vanilla, MenuKit, a slot library's or another mod's)
 * restores it. No base class and no per-consumer opt-in.
 *
 * <p>Vanilla moves the cursor to the window center on some screen transitions (in
 * dev, every InventoryScreen open lands at about the center). Capturing only on
 * screens that opt in can't fix a transition from a vanilla screen, because the
 * screen being left never opted in. So every screen captures and every screen
 * restores. Cursor position belongs to the transition, not to any Screen class,
 * which is why this is a utility on Fabric's AFTER_INIT (fired for every screen,
 * vanilla included) rather than base-class methods.
 *
 * <ul>
 *   <li><b>Capture:</b> every screen's {@code removed()} stashes the OS cursor
 *       position from {@code MouseHandler.xpos/ypos}. The listener is added in
 *       AFTER_INIT so it exists before removed() fires.</li>
 *   <li><b>Restore:</b> {@code Gui.setScreen}, after the new screen's {@code init()}
 *       and after vanilla reads the pointer back from the OS
 *       ({@code resyncMousePosition}, {@link com.trevlar.menukit.mixin.MKCursorContinuityMixin}),
 *       applies the stash with
 *       {@code InputConstants.releaseMouse} (a cursor warp; the cursor is
 *       already free on a screen) <i>and</i> writes Minecraft's
 *       {@code MouseHandler.xpos/ypos} through the accessor mixin, since the
 *       warp alone doesn't update them. The stash is cleared after
 *       use so a later, unrelated open doesn't get a stale position.</li>
 *   <li><b>Back to the HUD:</b> a tick listener clears the stash whenever
 *       {@code Gui.screen() == null}, so opening the first screen from the HUD
 *       centers the cursor as vanilla does, instead of restoring a position from
 *       long ago. Screen-to-screen changes never tick with no screen (setScreen
 *       removes, sets and inits synchronously), so the stash survives those.</li>
 * </ul>
 *
 * <p>This acts on every screen only to undo vanilla's re-centering. The only
 * visible effect is that the cursor stays where it was.
 */
@ApiStatus.Internal
public final class CursorContinuity {

    private CursorContinuity() {}

    // Cursor position waiting to be restored, or null.
    private static double @Nullable [] stashed = null;

    private static void capture() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        stashed = new double[]{mc.mouseHandler.xpos(), mc.mouseHandler.ypos()};
    }

    /** Restores the stashed cursor position, if any, and clears the stash. Runs at the end of every screen change. */
    public static void restoreIfAny() {
        if (stashed == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            stashed = null; // don't keep a stash if the window is gone
            return;
        }
        // Back to the HUD: the cursor is grabbed, and the tick listener drops the stash.
        if (mc.gui.screen() == null) return;
        InputConstants.releaseMouse(mc.getWindow(), stashed[0], stashed[1]);

        // The warp doesn't reach MouseHandler before this screen's first frame,
        // so MouseHandler.xpos/ypos would keep whatever
        // vanilla last wrote (usually the window center on the async open path).
        // Without this, hover detection and anything reading
        // mouseHandler.xpos()/ypos() sees the center until the mouse moves.
        ((MouseHandlerCursorPosAccessor) (Object) mc.mouseHandler)
                .mk$setXpos(stashed[0]);
        ((MouseHandlerCursorPosAccessor) (Object) mc.mouseHandler)
                .mk$setYpos(stashed[1]);
        stashed = null;
    }

    /**
     * Registers the AFTER_INIT listener, which adds a capture listener to each
     * screen's {@code removed()}, and the tick listener that clears the stash while
     * no screen is open. The restore itself is {@link com.trevlar.menukit.mixin.MKCursorContinuityMixin}'s:
     * at AFTER_INIT vanilla's read of the OS pointer is still to come, and would undo it
     * wherever the warp hasn't reached the OS (a client gametest blocks it, so the
     * pointer read is wherever the real mouse happens to be). Call once from client
     * init; a second call would add duplicate listeners.
     */
    public static void registerRestoreHook() {
        ScreenEvents.AFTER_INIT.register((client, screen, sw, sh) -> {
            ScreenEvents.remove(screen).register(s -> capture());
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // No screen open: drop the stash so the next open centers the cursor
            // as vanilla does. Screen-to-screen changes happen inside setScreen()
            // and never tick here with no screen.
            if (client.gui.screen() == null && stashed != null) {
                stashed = null;
            }
        });
    }
}
