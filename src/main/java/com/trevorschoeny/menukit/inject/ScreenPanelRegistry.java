package com.trevorschoeny.menukit.inject;

import com.trevorschoeny.menukit.mixin.AbstractContainerScreenAccessor;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Library-owned registry of MenuContext {@link ScreenPanelAdapter}s that
 * declare targeting via {@code .on(...)} or {@code .onAny()}. Listens once
 * on {@link ScreenEvents#AFTER_INIT} and dispatches render/input to the
 * adapters whose targeting matches each opened {@link AbstractContainerScreen}.
 * Consumers stop writing per-screen {@code ScreenEvents.AFTER_INIT}
 * boilerplate — the library owns the hook.
 *
 * <p>See {@code menukit/Design Docs/Phase 12.5/M8_FOUR_CONTEXT_MODEL.md} §8
 * for the design and §7.3 for orphan-adapter enforcement.
 *
 * <h3>Adapter lifecycle</h3>
 *
 * <ol>
 *   <li><b>Construction.</b> {@code new ScreenPanelAdapter(panel, region)}
 *       calls {@link #trackPending} — the adapter joins {@link #PENDING}
 *       until it declares targeting.</li>
 *   <li><b>Targeting declaration.</b> {@code .on(Class...)} or
 *       {@code .onAny()} calls {@link #markTargetingDeclared} — the adapter
 *       moves from {@link #PENDING} to {@link #REGISTERED}.</li>
 *   <li><b>Orphan checkpoint.</b> On the first screen-open event after init,
 *       {@link #validateTargetingDeclared} runs. If {@link #PENDING} is
 *       non-empty, those adapters are orphans — {@link IllegalStateException}
 *       fails the client boot visibly, naming each orphan's panel ID.</li>
 *   <li><b>Dispatch.</b> For each opened {@link AbstractContainerScreen},
 *       walk {@link #REGISTERED} and for adapters whose targeting matches
 *       the screen class, register per-screen {@code afterRender} +
 *       {@code allowMouseClick} hooks. Fabric handles per-screen lifetime
 *       cleanup when the screen closes.</li>
 * </ol>
 *
 * <h3>Lambda-based adapters are exempt</h3>
 *
 * Adapters constructed with a {@link ScreenOriginFn} (rather than a
 * {@link com.trevorschoeny.menukit.core.MenuRegion}) don't participate in
 * this registry — they're the escape hatch scoped by the consumer's own
 * mixin. {@link ScreenPanelAdapter#on} / {@link ScreenPanelAdapter#onAny}
 * throw if called on them.
 */
public final class ScreenPanelRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger("menukit");

    private ScreenPanelRegistry() {}

    // ── Adapter tracking ────────────────────────────────────────────────
    //
    // Region-based adapters move from PENDING → REGISTERED when they
    // declare targeting. Strong references (not WeakHashMap): consumers
    // typically hold adapters as static final fields, so they're
    // process-lifetime anyway.

    private static final Set<ScreenPanelAdapter> PENDING =
            Collections.synchronizedSet(new HashSet<>());

    private static final List<ScreenPanelAdapter> REGISTERED =
            Collections.synchronizedList(new ArrayList<>());

    private static volatile boolean checkpointRun = false;

    // ── API called by ScreenPanelAdapter ────────────────────────────────

    /**
     * Called from {@link ScreenPanelAdapter}'s region-based constructor —
     * marks the adapter as pending (awaiting targeting declaration).
     */
    static void trackPending(ScreenPanelAdapter adapter) {
        PENDING.add(adapter);
    }

    /**
     * Called from {@link ScreenPanelAdapter#on} / {@link ScreenPanelAdapter#onAny}.
     * Moves the adapter from pending to registered.
     */
    static void markTargetingDeclared(ScreenPanelAdapter adapter) {
        PENDING.remove(adapter);
        REGISTERED.add(adapter);
    }

    // ── Observable state ────────────────────────────────────────────────

    /** Returns an unmodifiable snapshot of orphan (untargeted) adapters. */
    public static Set<ScreenPanelAdapter> pendingSnapshot() {
        synchronized (PENDING) {
            return Set.copyOf(PENDING);
        }
    }

    /** Returns an unmodifiable snapshot of registered adapters. */
    public static List<ScreenPanelAdapter> registeredSnapshot() {
        synchronized (REGISTERED) {
            return List.copyOf(REGISTERED);
        }
    }

    // ── Initialization ──────────────────────────────────────────────────

    /**
     * Registers the library-owned {@link ScreenEvents#AFTER_INIT} listener.
     * Called once from {@code MenuKitClient.onInitializeClient}. After this,
     * any region-based adapter that declared targeting will render on
     * matching screens without the consumer writing per-screen boilerplate.
     */
    public static void init() {
        ScreenEvents.AFTER_INIT.register(ScreenPanelRegistry::onScreenInit);
    }

    // ── Screen-open dispatch ────────────────────────────────────────────

    /**
     * Called on every screen-open after {@link #init}. Runs the orphan
     * checkpoint once, then wires per-screen render + input hooks for the
     * adapters whose targeting matches this screen.
     */
    private static void onScreenInit(Minecraft client, Screen screen,
                                      int scaledWidth, int scaledHeight) {
        // Orphan checkpoint — runs once per client session. All region-based
        // adapters that were constructed during init have completed their
        // targeting declaration by now (static initializers + onInitializeClient
        // ran before the first screen opened). Any still in PENDING are
        // orphans.
        if (!checkpointRun) {
            checkpointRun = true;
            validateTargetingDeclared();
        }

        // MenuContext only dispatches for AbstractContainerScreen — other
        // screen types (title screen, pause menu, etc.) are outside scope.
        if (!(screen instanceof AbstractContainerScreen<?> acs)) return;

        // Filter registered adapters to those whose targeting matches.
        // Captured by value into a per-screen list so we don't re-filter
        // the global REGISTERED list on every frame.
        Class<? extends AbstractContainerScreen<?>> screenClass =
                asConcreteScreenClass(acs.getClass());
        List<ScreenPanelAdapter> matching = new ArrayList<>();
        for (ScreenPanelAdapter adapter : registeredSnapshot()) {
            if (adapter.matches(screenClass)) {
                matching.add(adapter);
            }
        }
        if (matching.isEmpty()) return;

        // Per-screen render + input hooks. Fabric auto-unregisters these
        // when the screen closes, so no manual cleanup is needed.
        ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, delta) -> {
            ScreenBounds bounds = frameBounds(acs);
            for (ScreenPanelAdapter adapter : matching) {
                adapter.render(graphics, bounds, mouseX, mouseY, acs);
            }
        });
        ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
            ScreenBounds bounds = frameBounds(acs);
            for (ScreenPanelAdapter adapter : matching) {
                adapter.mouseClicked(bounds, event.x(), event.y(), event.button(), acs);
            }
            // Return true — vanilla continues processing clicks that missed
            // any adapter's interactive element. Per M8 §8.3, future
            // cancellation-aware behavior will be added via
            // ScreenPanelAdapter.cancelsUnhandledClicks(boolean).
            return true;
        });
    }

    /**
     * Reads the screen's frame bounds via
     * {@link AbstractContainerScreenAccessor}. Computed per-frame because
     * {@code leftPos}/{@code topPos} shift on resize and recipe-book toggle.
     */
    private static ScreenBounds frameBounds(AbstractContainerScreen<?> screen) {
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        return new ScreenBounds(
                acc.trevorMod$getLeftPos(),
                acc.trevorMod$getTopPos(),
                acc.trevorMod$getImageWidth(),
                acc.trevorMod$getImageHeight());
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends AbstractContainerScreen<?>> asConcreteScreenClass(
            Class<?> raw) {
        return (Class<? extends AbstractContainerScreen<?>>) raw;
    }

    // ── Orphan enforcement ──────────────────────────────────────────────

    /**
     * Validates that every region-based adapter constructed during init
     * declared its targeting. Called once, on the first screen-open event.
     * Per M8 §7.3: throws {@link IllegalStateException} naming the orphan
     * panel IDs, failing the client boot visibly rather than silently
     * skipping the broken decoration.
     *
     * <p>Orphans are a build/wiring defect, not a runtime recoverable
     * condition — a consumer constructed a region-based adapter and never
     * called {@code .on()} / {@code .onAny()}. The fix is to add the
     * missing targeting declaration.
     */
    public static void validateTargetingDeclared() {
        Set<ScreenPanelAdapter> pending = pendingSnapshot();
        if (pending.isEmpty()) return;

        StringBuilder msg = new StringBuilder(
                "MenuKit: ").append(pending.size()).append(
                " region-based ScreenPanelAdapter(s) constructed but never declared " +
                "targeting (.on / .onAny). Fix by calling .on(ScreenClass...) or " +
                ".onAny() on each. Orphan panel IDs: ");
        boolean first = true;
        for (ScreenPanelAdapter adapter : pending) {
            if (!first) msg.append(", ");
            msg.append(adapter.getPanel().getId());
            first = false;
        }
        String message = msg.toString();
        LOGGER.error("[ScreenPanelRegistry] {}", message);
        throw new IllegalStateException(message);
    }
}
