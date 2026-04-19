package com.trevorschoeny.menukit.inject;

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
 * on {@code ScreenEvents.AFTER_INIT} and dispatches render/input to the
 * adapters whose targeting matches each opened {@link AbstractContainerScreen}.
 * Consumers stop writing {@code ScreenEvents.AFTER_INIT} boilerplate; the
 * library owns the hook.
 *
 * <p>See {@code menukit/Design Docs/Phase 12.5/M8_FOUR_CONTEXT_MODEL.md} §8
 * for design and §7.3 for targeting enforcement.
 *
 * <p><b>Step 2 scope (this commit).</b> The pending-adapter tracking and
 * {@code .on()} / {@code .onAny()} completion hooks land here; actual
 * screen-open dispatch is deferred to step 3 of the M8 implementation
 * sequence. Adapters that declare targeting in step 2 don't yet render via
 * this registry — they continue to render via their consumer's existing
 * {@code ScreenEvents} listener (V4.2) or mixin (RegionProbes). Step 3
 * cuts those over.
 *
 * <h3>Orphan enforcement</h3>
 *
 * Construction of a region-based {@link ScreenPanelAdapter} adds it to the
 * {@link #PENDING} set as an orphan. Calling {@code .on()} or
 * {@code .onAny()} removes it. Adapters still in the pending set at the
 * first {@code AFTER_INIT} firing are orphans — incomplete adapters that
 * will never render. Step 3's checkpoint will walk {@link #PENDING} and
 * throw {@link IllegalStateException} naming the orphan panel IDs so the
 * client boot fails visibly rather than silently skipping the broken
 * decoration.
 *
 * <p>Lambda-based adapters (those constructed with a {@link ScreenOriginFn}
 * rather than a {@link com.trevorschoeny.menukit.core.MenuRegion}) don't
 * participate — they're the escape hatch and are scoped by the consumer's
 * own mixin. The {@code .on()} / {@code .onAny()} methods throw if called
 * on them.
 */
public final class ScreenPanelRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger("menukit");

    private ScreenPanelRegistry() {}

    // ── Pending-adapter tracking for orphan enforcement ─────────────────
    //
    // Region-based adapters add themselves to PENDING during construction.
    // Calling .on() / .onAny() removes them. At the step-3 boot checkpoint,
    // any adapters still in PENDING are orphans — the client boot fails
    // with their panel IDs so they're visible and fixable, not silent.
    //
    // Strong references (not WeakHashMap): consumers typically hold adapters
    // as static final fields, so they're process-lifetime anyway. An adapter
    // GC'd before declaring targeting is itself a leak — the orphan
    // enforcement wouldn't catch it, but no consumer pattern produces this
    // shape today.

    private static final Set<ScreenPanelAdapter> PENDING =
            Collections.synchronizedSet(new HashSet<>());

    // Registered-adapter list — populated by .on() / .onAny() completion.
    // Step 3 iterates this list per-screen-open to dispatch matching
    // adapters. In step 2, consumers still drive their own render via
    // legacy paths; step 3 flips the dispatch to the registry.

    private static final List<ScreenPanelAdapter> REGISTERED =
            Collections.synchronizedList(new ArrayList<>());

    // ── API called by ScreenPanelAdapter ────────────────────────────────

    /**
     * Called from {@link ScreenPanelAdapter}'s region-based constructor.
     * Marks the adapter as pending (awaiting {@code .on()} / {@code .onAny()}).
     */
    static void trackPending(ScreenPanelAdapter adapter) {
        PENDING.add(adapter);
    }

    /**
     * Called from {@link ScreenPanelAdapter#on} and
     * {@link ScreenPanelAdapter#onAny}. Moves the adapter from pending to
     * registered. Idempotent — duplicate calls are guarded at the
     * {@code ScreenPanelAdapter} level (IllegalStateException on repeat).
     */
    static void markTargetingDeclared(ScreenPanelAdapter adapter) {
        PENDING.remove(adapter);
        REGISTERED.add(adapter);
    }

    // ── Observable state for step-3 wiring ──────────────────────────────

    /**
     * Returns an unmodifiable snapshot of adapters awaiting targeting
     * declaration. Step 3's boot checkpoint reads this at first
     * {@code AFTER_INIT} firing.
     */
    public static Set<ScreenPanelAdapter> pendingSnapshot() {
        synchronized (PENDING) {
            return Set.copyOf(PENDING);
        }
    }

    /**
     * Returns an unmodifiable snapshot of adapters that have declared
     * targeting. Step 3 iterates this on screen open to find matching
     * adapters for dispatch.
     */
    public static List<ScreenPanelAdapter> registeredSnapshot() {
        synchronized (REGISTERED) {
            return List.copyOf(REGISTERED);
        }
    }

    // ── Step-3 placeholder — orphan enforcement checkpoint ──────────────
    //
    // Step 3 will call this from the AFTER_INIT listener the first time any
    // screen opens. The stub below logs pending adapters without throwing,
    // so step 2 doesn't break the client boot before step 3 lands. Step 3
    // swaps the log for a throw.

    /**
     * Validates that every region-based adapter constructed during init has
     * declared its targeting. Called once per client session — step 3 wires
     * this into the first {@code AFTER_INIT} firing.
     *
     * <p><b>Step 2 behavior:</b> logs pending adapters as warnings but does
     * not throw. Step 3 will change this to throw
     * {@link IllegalStateException} per M8 §7.3.
     */
    public static void validateTargetingDeclared() {
        Set<ScreenPanelAdapter> pending = pendingSnapshot();
        if (pending.isEmpty()) return;

        StringBuilder msg = new StringBuilder(
                "MenuKit: ").append(pending.size()).append(
                " region-based ScreenPanelAdapter(s) constructed but never declared targeting " +
                "(.on / .onAny). These adapters will not render. Panel IDs: ");
        boolean first = true;
        for (ScreenPanelAdapter adapter : pending) {
            if (!first) msg.append(", ");
            msg.append(adapter.getPanel().getId());
            first = false;
        }
        // Step 2: warn only. Step 3 will throw.
        LOGGER.warn("[ScreenPanelRegistry] {}", msg);
    }
}
