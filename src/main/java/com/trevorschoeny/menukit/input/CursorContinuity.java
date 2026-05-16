package com.trevorschoeny.menukit.input;

import com.trevorschoeny.menukit.mixin.MouseHandlerCursorPosAccessor;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;

import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.jetbrains.annotations.ApiStatus;

/**
 * Phase 16h — utility for preserving OS cursor position across screen
 * transitions. Universal capture: every screen's {@code removed()}
 * stashes the cursor; the next screen's {@code init()} (any screen,
 * vanilla or MK or MKC or third-party) restores from the stash via the
 * AFTER_INIT hook. Single utility class, no base-class coupling, no
 * per-consumer opt-in.
 *
 * <h3>Why universal capture</h3>
 *
 * Vanilla / platform / OS layer moves the OS cursor to window center on
 * certain screen transitions (consistent ~window-center coords on every
 * InventoryScreen open in dev). Capture-on-opt-in can't help across
 * vanilla→MK or vanilla→MKC transitions because vanilla screens (the
 * LEAVING side) weren't opt-in candidates. Universal capture sidesteps
 * that — every screen.removed() stashes, every screen.init() restores.
 *
 * <h3>Why a utility class instead of base-class methods</h3>
 *
 * Cursor continuity is a transition-behavior, not a property of any
 * Screen hierarchy. Putting the mechanism in a utility decouples it
 * from base classes and from per-consumer registration; the mechanism
 * rides on Fabric's universal AFTER_INIT (which fires for every screen
 * including vanilla).
 *
 * <h3>Lifecycle semantics</h3>
 *
 * <ul>
 *   <li><b>Capture:</b> every screen's {@code removed()} stashes the
 *       current OS cursor pos via {@code GLFW.glfwGetCursorPos}. Wired
 *       in AFTER_INIT for every screen so the listener exists before
 *       its eventual removed() fires.</li>
 *   <li><b>Restore:</b> every screen's {@code init()} reads the stash
 *       via {@code GLFW.glfwSetCursorPos} <i>and</i> updates Minecraft's
 *       internal {@code MouseHandler.xpos/ypos} via the accessor mixin.
 *       glfwSetCursorPos alone wouldn't update the internal handler
 *       state; the mixin sync covers that gap. One-shot, cleared after
 *       restore so subsequent unrelated opens don't get a stale pose.</li>
 *   <li><b>HUD invalidation (Phase 17 follow-up):</b> a per-tick listener
 *       clears the stash whenever {@code Minecraft.screen == null}.
 *       Reason: HUD → first-screen-open should center the cursor
 *       (vanilla default), not restore from a long-stale stash left over
 *       from the last screen the user closed. Chained
 *       screen → screen transitions don't tick through HUD (setScreen is
 *       synchronous: remove → set new screen → init), so the stash
 *       survives those. Only an actual close-to-HUD frame invalidates.</li>
 * </ul>
 *
 * <h3>Library-not-platform alignment (§0019)</h3>
 *
 * Universal capture/restore is library-not-platform-compatible
 * <i>because</i> it counters a platform misbehavior (vanilla / OS
 * centering cursor on screen transitions). If the platform did the
 * right thing by default, the library wouldn't need to act; since
 * vanilla doesn't, the library compensates uniformly. There's no
 * ambient behavior beyond "cursor stays where it was" — which is the
 * intuitively-correct user-facing default.
 */
@ApiStatus.Internal
public final class CursorContinuity {

    private CursorContinuity() {}

    /**
     * Static stash for cursor pos pending restore. Null = no stash. Updated
     * by per-screen capture lambda, consumed by {@link #restoreIfAny}.
     */
    private static double @Nullable [] stashed = null;

    /**
     * Samples the current OS cursor position and stashes it. Internal —
     * called by per-screen {@code ScreenEvents.remove} listeners wired
     * universally in {@link #registerRestoreHook}.
     */
    private static void capture() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        double[] xpos = new double[1];
        double[] ypos = new double[1];
        GLFW.glfwGetCursorPos(mc.getWindow().handle(), xpos, ypos);
        stashed = new double[]{xpos[0], ypos[0]};
    }

    /**
     * Restores the stashed cursor position if one is pending, then clears
     * the stash. Wired from {@code MenuKitClient.onInitializeClient} via
     * {@link #registerRestoreHook} as a universal {@code AFTER_INIT}
     * listener so it fires for ANY screen open (vanilla, MK, MKC, third-
     * party — same behavior). One-shot semantics: cleared after restore
     * so unrelated subsequent opens don't get a stale cursor position.
     */
    public static void restoreIfAny() {
        if (stashed == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            stashed = null; // avoid stale stash if window vanished
            return;
        }
        GLFW.glfwSetCursorPos(mc.getWindow().handle(), stashed[0], stashed[1]);

        // Phase 16h — sync Minecraft's internal MouseHandler fields too.
        // glfwSetCursorPos doesn't fire GLFW's cursor-position callback for
        // programmatic moves, so MouseHandler.xpos/ypos remain stuck at
        // whatever vanilla last wrote (typically window-center on the
        // async-open path). Writing through the accessor mixin keeps the
        // OS cursor and internal handler state aligned — without this,
        // hover detection and any code reading mouseHandler.xpos()/ypos()
        // gets a stale centered position until the next physical mouse move.
        ((MouseHandlerCursorPosAccessor) (Object) mc.mouseHandler)
                .menuKit$setXpos(stashed[0]);
        ((MouseHandlerCursorPosAccessor) (Object) mc.mouseHandler)
                .menuKit$setYpos(stashed[1]);
        stashed = null;
    }

    /**
     * Wires the universal AFTER_INIT listener that does two things:
     * <ol>
     *   <li><b>Registers a per-screen capture listener.</b> Every screen's
     *       eventual {@code removed()} call will stash the cursor.</li>
     *   <li><b>Restores stashed cursor.</b> If a stash is pending (from
     *       the previous screen's removed() firing), teleport the OS
     *       cursor and clear the stash.</li>
     * </ol>
     *
     * <p>Also wires a per-tick HUD-invalidation listener (see class javadoc
     * "HUD invalidation"): whenever {@code Minecraft.screen == null}, clear
     * any pending stash so the next HUD → screen-open lands with vanilla's
     * centering behavior. Chained screen → screen transitions don't tick
     * through HUD and therefore preserve the stash for restore.
     *
     * <p>Call once from the library's client-init entry point. Idempotent
     * at the cost of duplicate listeners — but typically called exactly
     * once.
     */
    public static void registerRestoreHook() {
        ScreenEvents.AFTER_INIT.register((client, screen, sw, sh) -> {
            ScreenEvents.remove(screen).register(s -> capture());
            restoreIfAny();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // HUD frame — invalidate any stash so next screen-open centers
            // (vanilla default). Chained screen → screen transitions are
            // synchronous within setScreen() and never tick through here
            // with screen == null, so this only triggers on real
            // close-to-HUD events.
            if (client.screen == null && stashed != null) {
                stashed = null;
            }
        });
    }
}
