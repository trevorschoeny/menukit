package com.trevorschoeny.menukit.inject;

import com.trevorschoeny.menukit.mixin.ScreenAccessor;

import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;

import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Phase 18s — sibling to {@link ScreenPanelRegistry} for non-container
 * vanilla screens. Dispatched from {@link ScreenPanelRegistry}'s
 * {@code AFTER_INIT} listener when the opened screen is NOT an
 * {@code AbstractContainerScreen}.
 *
 * <h2>Why a sibling registry, not a unified one</h2>
 *
 * Trev's pick on the registry-shape question was "reuse the existing
 * listener, branch on screen type." That settles the EVENT subscription
 * (one listener in {@link ScreenPanelRegistry}). The actual dispatch
 * code differs enough (no chrome, no M9 opacity registry, simpler input
 * contract) that bundling it into {@link ScreenPanelRegistry} would
 * bloat that already-770-line file. This helper class owns the parallel
 * dispatch path; the existing registry just calls into it.
 *
 * <h2>Lifecycle</h2>
 *
 * <ol>
 *   <li>Adapter constructor calls {@link #trackPending} — adapter joins
 *       PENDING, awaiting {@code .on/.onAny} targeting declaration.</li>
 *   <li>{@code .on()} / {@code .onAny()} calls {@link #markTargetingDeclared}
 *       — adapter moves from PENDING to REGISTERED.</li>
 *   <li>When a matching screen opens, {@link #onScreenInit} matches
 *       registered adapters by class-ancestry, registers a
 *       {@link Renderable} via {@code addRenderableOnly} (renders inside
 *       Screen.render before the end-of-frame tooltip flush — correct
 *       z-order for MK widget tooltips), and registers Fabric input hooks
 *       for click/scroll/release dispatch.</li>
 *   <li>{@code unregister()} on the adapter calls {@link #untrack} —
 *       removes from both PENDING and REGISTERED sets. Per-screen
 *       Renderables + Fabric hooks auto-clean when the screen closes.</li>
 * </ol>
 *
 * <h2>Input contract</h2>
 *
 * On {@code allowMouseClick}, returns {@code false} (eats the click from
 * vanilla) when any matching adapter's panel is visible and the cursor
 * is within the panel's outer bounds — matches the
 * {@code Panel.opaque(true)} default of the container-screen path. Click
 * dispatch is two-pass (active-overlay claim, then hit-test) per the
 * library's existing dispatch discipline.
 */
@ApiStatus.Internal
public final class VanillaScreenPanelRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger("menukit");

    private VanillaScreenPanelRegistry() {}

    // Adapters in lifecycle stages. Identity-keyed for both sets so two
    // adapters around the "same" panel don't collide on equals.
    private static final Set<VanillaScreenPanelAdapter> PENDING =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<VanillaScreenPanelAdapter> REGISTERED =
            Collections.newSetFromMap(new IdentityHashMap<>());

    // ── Lifecycle ──────────────────────────────────────────────────────

    /** Called from the adapter constructor — adapter joins PENDING. */
    public static void trackPending(VanillaScreenPanelAdapter adapter) {
        PENDING.add(adapter);
    }

    /**
     * Called from {@link VanillaScreenPanelAdapter#on} / {@code onAny()} —
     * adapter moves from PENDING to REGISTERED, ready to dispatch on
     * matching screen opens.
     */
    public static void markTargetingDeclared(VanillaScreenPanelAdapter adapter) {
        PENDING.remove(adapter);
        REGISTERED.add(adapter);
    }

    /** Called from {@link VanillaScreenPanelAdapter#unregister} — removes from both sets. */
    public static void untrack(VanillaScreenPanelAdapter adapter) {
        PENDING.remove(adapter);
        REGISTERED.remove(adapter);
    }

    /** Snapshot of orphan (untargeted) adapters. */
    public static Set<VanillaScreenPanelAdapter> pendingSnapshot() {
        return Set.copyOf(PENDING);
    }

    /**
     * Orphan checkpoint — throws if any vanilla adapters were constructed
     * during init but never declared targeting. Called from
     * {@link ScreenPanelRegistry}'s shared checkpoint on first screen-open.
     */
    public static void validateTargetingDeclared() {
        Set<VanillaScreenPanelAdapter> pending = pendingSnapshot();
        if (pending.isEmpty()) return;

        StringBuilder msg = new StringBuilder("MenuKit: ");
        msg.append(pending.size())
           .append(" VanillaScreenPanelAdapter(s) constructed but never " +
                   "declared targeting (.on / .onAny). Panel IDs: ");
        boolean first = true;
        for (VanillaScreenPanelAdapter adapter : pending) {
            if (!first) msg.append(", ");
            msg.append(adapter.getPanel().getId());
            first = false;
        }
        msg.append(". Fix by adding the missing .on(...) call(s).");
        String message = msg.toString();
        LOGGER.error("[VanillaScreenPanelRegistry] {}", message);
        throw new IllegalStateException(message);
    }

    // ── Per-screen dispatch ────────────────────────────────────────────

    /**
     * Called from {@link ScreenPanelRegistry#onScreenInit} when the opened
     * screen is NOT an {@code AbstractContainerScreen}. Matches registered
     * adapters against the screen, wires render + input dispatch if any
     * match, no-ops otherwise.
     */
    public static void onScreenInit(Screen screen) {
        // Match adapters by class-ancestry.
        List<VanillaScreenPanelAdapter> matches = new ArrayList<>();
        for (VanillaScreenPanelAdapter adapter : REGISTERED) {
            if (adapter.matches(screen)) {
                matches.add(adapter);
            }
        }
        if (matches.isEmpty()) return;

        // Snapshot the match list — adapters could unregister mid-screen-
        // life via .unregister(), but per-screen-open dispatch should still
        // use the set that was active at init time. Caller can re-open the
        // screen if they want a different active set.
        List<VanillaScreenPanelAdapter> activeMatches = List.copyOf(matches);

        // ── Render hook ──────────────────────────────────────────────
        // Register a Renderable that iterates active adapters and renders
        // each. Renderables fire INSIDE Screen.render's renderable loop,
        // BEFORE the end-of-frame tooltip flush — so MK widgets' tooltips
        // (queued via setTooltipForNextFrame during this render) draw on
        // top correctly. Same trick Phase 18q added for the container path.
        Renderable mkRenderable = (graphics, mouseX, mouseY, partialTick) -> {
            int sw = screen.width;
            int sh = screen.height;
            for (VanillaScreenPanelAdapter adapter : activeMatches) {
                adapter.render(graphics, sw, sh, mouseX, mouseY, screen);
            }
        };
        ((ScreenAccessor) screen).menuKit$addRenderableOnly(mkRenderable);

        // ── Click dispatch ───────────────────────────────────────────
        // Two-pass dispatch inside each adapter (active-overlay then hit-
        // test). Adapter.mouseClicked returns true if it handled OR
        // cursor-was-inside-panel-bounds — both cases eat from vanilla
        // per the input-contract (panels opaque to vanilla widgets within
        // their bounds, mirroring Panel.opaque(true) default).
        ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
            int sw = s.width;
            int sh = s.height;
            for (VanillaScreenPanelAdapter adapter : activeMatches) {
                if (adapter.mouseClicked(sw, sh, event.x(), event.y(),
                        event.button(), s)) {
                    return false;  // eat from vanilla
                }
            }
            return true;
        });

        // ── Scroll dispatch ──────────────────────────────────────────
        ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, hAmount, vAmount) -> {
            int sw = s.width;
            int sh = s.height;
            for (VanillaScreenPanelAdapter adapter : activeMatches) {
                if (adapter.mouseScrolled(sw, sh, mouseX, mouseY,
                        hAmount, vAmount, s)) {
                    return false;
                }
            }
            return true;
        });

        // ── Release dispatch ─────────────────────────────────────────
        // Like the container path, release is NOT hit-tested — fires for
        // every visible element so drag-end detection works even when the
        // cursor has moved off during drag.
        ScreenMouseEvents.allowMouseRelease(screen).register((s, event) -> {
            for (VanillaScreenPanelAdapter adapter : activeMatches) {
                adapter.mouseReleased(event.x(), event.y(), event.button());
            }
            return true;
        });

        LOGGER.debug("[VanillaScreenPanelRegistry] Wired {} adapter(s) on {}",
                activeMatches.size(), screen.getClass().getSimpleName());
    }
}
