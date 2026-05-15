package com.trevorschoeny.menukit.input;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Phase 16h — utility for preserving OS cursor position across screen
 * transitions. Universal capture: every screen's {@code removed()}
 * stashes the cursor; the next screen's {@code init()} (any screen,
 * vanilla or MK or MKC or third-party) restores from the stash via the
 * AFTER_INIT hook. Single utility class, no base-class coupling, no
 * per-consumer opt-in required.
 *
 * <h3>Why universal capture (not per-consumer opt-in)</h3>
 *
 * Earlier 16h drafts made capture opt-in: consumers called
 * {@link #enableFor(Screen)} on their screens to register a per-screen
 * remove listener. Diagnostic logging then surfaced the actual problem:
 * vanilla / platform / OS layer was already moving the OS cursor to
 * window center on certain screen transitions (consistent ~427,240
 * coords on every InventoryScreen open in an 854×480 dev window).
 * Capture-on-opt-in couldn't help across vanilla→MK or vanilla→MKC
 * transitions because vanilla screens (the LEAVING side) weren't
 * opt-in candidates.
 *
 * <p>The right shape: capture is universal (every screen.removed()
 * stashes), restore is universal (every screen.init() restores).
 * Opt-in was wrong-shaped — cursor continuity is a defensive measure
 * against vanilla/platform quirks, not a feature consumers selectively
 * enable. The result is "your cursor stays where it was, always" — the
 * principle of least surprise.
 *
 * <h3>Why a utility class instead of base-class methods</h3>
 *
 * Earlier 16h drafts also coupled cursor continuity to {@code MenuKitScreen}
 * via a {@code removed()} override + static stash on that class. That
 * forced parallel duplication on {@code MenuKitHandledScreen} (different
 * vanilla base) and any future Screen type. The root cause: cursor
 * continuity is a transition-behavior, not a property of any Screen
 * hierarchy. Putting the mechanism in a utility decouples it from base
 * classes — and now also from per-consumer registration, since capture
 * rides on Fabric's universal AFTER_INIT (which fires for every screen
 * including vanilla).
 *
 * <h3>Lifecycle semantics</h3>
 *
 * <ul>
 *   <li><b>Capture (universal):</b> every screen's {@code removed()}
 *       stashes the current OS cursor pos via
 *       {@code GLFW.glfwGetCursorPos}. Wired in AFTER_INIT for every
 *       screen so the listener exists before its eventual removed()
 *       fires.</li>
 *   <li><b>Restore (universal):</b> every screen's {@code init()}
 *       reads the stash via {@code GLFW.glfwSetCursorPos}; one-shot,
 *       cleared after restore so subsequent unrelated opens don't get
 *       a stale pose.</li>
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
 *
 * <p>The {@link #enableFor(Screen)} entry point (and the
 * {@code preserveCursorContinuity} chainable wrappers on the MK and
 * MKC screen base classes) are kept as no-op-with-documentation entry
 * points for backward compatibility with the earlier opt-in API. They
 * record intent but don't affect behavior — capture happens for every
 * screen regardless.
 */
public final class CursorContinuity {

    private CursorContinuity() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("MenuKit/CursorContinuity");

    /**
     * Static stash for cursor pos pending restore. Null = no stash. Updated
     * by per-screen capture lambda, consumed by {@link #restoreIfAny}.
     */
    private static double @Nullable [] stashed = null;

    /**
     * Pending opt-ins — screens that called {@link #enableFor} but haven't
     * yet had their per-screen {@code ScreenEvents.remove} listener wired
     * (because Fabric's per-screen events aren't available until the
     * screen's first {@code init()} fires). Weak keys so screens that get
     * GC'd before init don't pin memory.
     *
     * <p><b>Why the deferred wiring:</b> {@code ScreenEvents.remove(screen)}
     * throws {@code IllegalStateException} ("screen has not been correctly
     * initialised") when called before the screen's {@code init()} runs —
     * the per-screen event registry is lazy-initialized via Fabric's
     * {@code ensureEventsAreInitialized} which only fires during the init
     * lifecycle. {@link #enableFor} is typically called from a screen
     * subclass constructor (via {@code super(...)} chain → super-class
     * chainable → here), which is too early. The universal AFTER_INIT
     * listener wired by {@link #registerRestoreHook} drains this set: any
     * screen present in it gets its per-screen remove listener wired and
     * the entry removed.
     */
    private static final Set<Screen> pendingOptIn =
            Collections.synchronizedSet(
                    Collections.newSetFromMap(new WeakHashMap<>()));

    /**
     * Historical opt-in entry point — kept for backward compatibility
     * with the earlier per-consumer opt-in API and the chainable
     * wrappers on the MK / MKC screen base classes
     * ({@code preserveCursorContinuity}).
     *
     * <p><b>No-op as of the 16h root-fix iteration:</b> capture is now
     * universal — every screen's {@code removed()} stashes the cursor
     * regardless of whether {@code enableFor} was called. The reason for
     * the change is documented on the class javadoc above; the short
     * version is that vanilla/platform code centers the cursor on
     * certain screen transitions, and the opt-in shape couldn't fix
     * vanilla→MK or vanilla→MKC transitions because vanilla screens
     * weren't opt-in candidates.
     *
     * <p>Still safe to call from screen constructors. Just records the
     * intent in {@link #pendingOptIn} for diagnostic visibility; the
     * AFTER_INIT hook wires the actual per-screen capture listener
     * universally, regardless of whether the screen is in this set.
     *
     * @param screen the screen the consumer "asked" for cursor
     *               preservation on; recorded but no behavior change
     *               (universal capture already handles it).
     */
    public static void enableFor(Screen screen) {
        pendingOptIn.add(screen);
        LOGGER.info("[cursor] enableFor: {} (intent recorded; capture is universal)",
                screen.getClass().getSimpleName());
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
        LOGGER.info("[cursor] capture: stashed=({}, {}) from screen={}",
                xpos[0], ypos[0],
                mc.screen != null ? mc.screen.getClass().getSimpleName() : "null");
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
        // Diagnostic — log current cursor pos vs the position we're about to
        // restore. Helps distinguish "we moved the cursor" vs "something
        // else moved it before us."
        double[] curX = new double[1];
        double[] curY = new double[1];
        GLFW.glfwGetCursorPos(mc.getWindow().handle(), curX, curY);
        LOGGER.info("[cursor] restore: current=({}, {}) -> stashed=({}, {}) on screen={}",
                curX[0], curY[0], stashed[0], stashed[1],
                mc.screen != null ? mc.screen.getClass().getSimpleName() : "null");
        GLFW.glfwSetCursorPos(mc.getWindow().handle(), stashed[0], stashed[1]);
        stashed = null;
    }

    /**
     * Wires the universal AFTER_INIT listener that does TWO things:
     * <ol>
     *   <li><b>Drains pending opt-ins.</b> If the just-init'd screen is in
     *       {@link #pendingOptIn} (added by {@link #enableFor} during
     *       construction), wire its per-screen
     *       {@code ScreenEvents.remove} listener now — by AFTER_INIT,
     *       Fabric's per-screen event registry is available. Remove from
     *       the pending set so subsequent re-inits (window resize)
     *       don't double-wire.</li>
     *   <li><b>Restores stashed cursor.</b> If a stash is pending (from
     *       the previous screen's removed() firing), teleport the OS
     *       cursor and clear the stash.</li>
     * </ol>
     *
     * <p>Call once from the library's client-init entry point. Idempotent
     * at the cost of duplicate listeners — but typically called exactly
     * once.
     */
    public static void registerRestoreHook() {
        ScreenEvents.AFTER_INIT.register((client, screen, sw, sh) -> {
            // Phase 16h root-fix: register a per-screen capture listener
            // UNIVERSALLY — on every screen, not just opted-in ones.
            //
            // The opt-in semantic (only-opted-in screens capture) was
            // wrong-shaped. Diagnostic logs surfaced that vanilla / OS /
            // some platform layer mysteriously centers the OS cursor on
            // certain screen transitions (consistently at window center
            // — readable via glfwGetCursorPos in the next screen's
            // AFTER_INIT). For the V5 case specifically, this only
            // happens on the async (C2S/S2C-mediated) Test MKC path, not
            // the synchronous Test MK path — so capture-on-opt-in alone
            // never gave a chance to record the pre-jump cursor pos for
            // Inventory→V5 transitions (Inventory isn't an MK opt-in
            // candidate).
            //
            // Making capture universal: every screen's removed() now
            // stashes the cursor before vanilla / platform code has a
            // chance to displace it. Restore in this same AFTER_INIT
            // then teleports the OS cursor back to that stashed pos —
            // canceling any platform-level cursor centering.
            //
            // pendingOptIn is no longer load-bearing (every screen wires
            // automatically), but kept for backward compat — its
            // semantic was an explicit "this consumer wanted cursor
            // preservation" marker. Drain it as a no-op acknowledgment.
            pendingOptIn.remove(screen);
            ScreenEvents.remove(screen).register(s -> capture());
            LOGGER.info("[cursor] AFTER_INIT: wired universal capture for {}",
                    screen.getClass().getSimpleName());

            // Restore stashed cursor for the new screen.
            restoreIfAny();
        });
    }
}
