package com.trevorschoeny.menukit.input;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Phase 16h — utility for preserving OS cursor position across screen
 * transitions. Decoupled from any particular {@link Screen} base class:
 * the consumer opts a Screen instance in via {@link #enableFor(Screen)},
 * the library captures cursor pos on that screen's removal via Fabric's
 * per-screen {@code ScreenEvents.remove}, and the universal AFTER_INIT
 * hook (wired once from {@code MenuKitClient}) restores on the next
 * screen's init — any next screen, any base class.
 *
 * <h3>Why a utility class instead of base-class methods</h3>
 *
 * Earlier 16h drafts coupled cursor continuity to {@code MenuKitScreen}
 * via a {@code removed()} override + static stash on that class. That
 * forced parallel duplication on {@code MenuKitHandledScreen} (which
 * extends a different vanilla base), and any future Screen type
 * consumers might subclass (vanilla {@code Screen}, third-party screens,
 * mod-defined screens not extending either MK base) would need yet
 * another duplication.
 *
 * <p>The root cause: cursor continuity is a transition-behavior, not a
 * property of any specific Screen hierarchy. Putting the mechanism in a
 * utility with a single-entry opt-in lets any {@code Screen} instance
 * participate — including vanilla subclasses that consumers can't modify
 * to add a base-class override. Per-screen capture rides on Fabric's
 * {@code ScreenEvents.remove(screen)} registry, which exists for exactly
 * this kind of per-screen lifecycle hook.
 *
 * <h3>Usage</h3>
 *
 * <pre>{@code
 * public MyScreen() {
 *     super(...);
 *     CursorContinuity.enableFor(this);
 * }
 * }</pre>
 *
 * Both {@code MenuKitScreen} and {@code MenuKitHandledScreen} expose
 * thin chainable wrappers ({@code .preserveCursorContinuity(true)}) that
 * delegate here — convenience for those subclass paths. The utility
 * itself is the canonical entry point and works for any Screen.
 *
 * <h3>Lifecycle semantics</h3>
 *
 * <ul>
 *   <li><b>Capture:</b> on {@code Screen.removed()}. Per-screen
 *       {@code ScreenEvents.remove} fires the capture lambda.</li>
 *   <li><b>Restore:</b> on the next screen's {@code init()} via the
 *       universal {@code ScreenEvents.AFTER_INIT} hook. One-shot — the
 *       stash is cleared after restore so subsequent unrelated screen
 *       opens don't get a stale cursor position.</li>
 *   <li><b>One-way per-instance:</b> {@link #enableFor(Screen)} registers
 *       a listener; there's no symmetric disable. Screens are short-lived
 *       and decisions are typically constructor-time. If runtime on/off
 *       becomes a need, a WeakHashMap-based registry can be added later.</li>
 * </ul>
 *
 * <h3>Library-not-platform alignment (§0019)</h3>
 *
 * Opt-in per-screen, no ambient behavior. Default off. Consumers
 * explicitly call {@link #enableFor(Screen)} (or the base-class
 * chainable wrapper) per-screen-instance to participate.
 */
public final class CursorContinuity {

    private CursorContinuity() {}

    /**
     * Static stash for cursor pos pending restore. Null = no stash. Updated
     * by per-screen capture lambda, consumed by {@link #restoreIfAny}.
     */
    private static double @Nullable [] stashed = null;

    /**
     * Opts the given screen in to cursor-position preservation. When this
     * screen's {@code removed()} fires (vanilla lifecycle), the current OS
     * cursor position is captured via {@code GLFW.glfwGetCursorPos} and
     * stashed; the next screen to open consumes the stash via the
     * universal restore hook.
     *
     * <p>Idempotent at the consumer level: multiple calls register multiple
     * listeners which all capture the same value (the last one to fire
     * wins, but they all produce identical state). No need to guard
     * against re-calling.
     *
     * @param screen the screen to enable cursor preservation for; the
     *               registration is tied to this specific instance.
     */
    public static void enableFor(Screen screen) {
        ScreenEvents.remove(screen).register(s -> capture());
    }

    /**
     * Samples the current OS cursor position and stashes it. Internal —
     * called by per-screen {@code ScreenEvents.remove} listeners
     * registered via {@link #enableFor}.
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
        stashed = null;
    }

    /**
     * Wires the universal AFTER_INIT restore hook. Call once from the
     * library's client-init entry point. Idempotent at the cost of
     * duplicate listeners — but typically called exactly once.
     */
    public static void registerRestoreHook() {
        ScreenEvents.AFTER_INIT.register(
                (client, screen, sw, sh) -> restoreIfAny());
    }
}
