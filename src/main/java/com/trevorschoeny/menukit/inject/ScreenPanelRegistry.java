package com.trevorschoeny.menukit.inject;

import com.trevorschoeny.menukit.core.Panel;
import com.trevorschoeny.menukit.mixin.AbstractContainerScreenAccessor;
import com.trevorschoeny.menukit.mixin.ScreenAccessor;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import org.jetbrains.annotations.ApiStatus;

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
 *       the screen class, cache the match list in {@link #SCREEN_DATA}
 *       and register a {@code ScreenMouseEvents.allowMouseClick} hook.
 *       Render dispatch runs via
 *       {@link com.trevorschoeny.menukit.mixin.MenuKitPanelRenderMixin}
 *       (injects at {@code INVOKE renderCarriedItem} so panels land in
 *       the right render stratum — see M8 §8.2 for why Fabric's
 *       {@code afterRender} is the wrong hook for render). Fabric handles
 *       per-screen click-hook lifetime cleanup when the screen closes.</li>
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
@ApiStatus.Internal
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

    // Post-§0042 split: SlotGroupContext adapter tracking + dispatch lives in
    // menukit-containers' SlotGroupPanelRegistry. This registry handles only
    // MenuContext + lambda-active opacity dispatch on Screen subclasses.

    private static volatile boolean checkpointRun = false;

    // Per-screen cache of MenuContext matches populated at screen-open.
    // Only the menu-context match list is static per-screen (targeting is
    // class-ancestry against screen.getClass(), which doesn't change once
    // the screen opens). SlotGroupContext matches re-resolve per frame
    // because menu.slots can mutate mid-session (creative tab switches;
    // future modded dynamic menus). See M8 §5.4 + §8.2 for the rationale.
    //
    // WeakHashMap keyed on Screen so entries GC when the screen is
    // unreferenced — no manual cleanup on screen close.
    private record ScreenRenderData(List<ScreenPanelAdapter> menuMatches) {}

    private static final Map<AbstractContainerScreen<?>, ScreenRenderData> SCREEN_DATA =
            Collections.synchronizedMap(new WeakHashMap<>());

    // ── Lambda-path opacity registry (M9 §4.4) ───────────────────────────
    //
    // Per-screen list of (adapter, boundsSupplier) tuples for lambda-based
    // adapters that have called .activeOn(...). Consulted by the four input-
    // handler mixins via findOpaquePanelAt / hasAnyVisibleOpaquePanelAt /
    // hasAnyVisibleModalTracking so lambda panels participate in M9's
    // click-through prohibition automatically.
    //
    // WeakHashMap keyed on Screen so entries GC when the screen is
    // unreferenced — no manual cleanup if a consumer forgets to call
    // .deactivate(). Per-screen list rather than global so lookups by
    // active screen are O(L) where L is per-screen lambda count.
    private record LambdaActiveEntry(
            ScreenPanelAdapter adapter,
            java.util.function.Supplier<ScreenBounds> boundsSupplier) {}

    private static final Map<net.minecraft.client.gui.screens.Screen, List<LambdaActiveEntry>> LAMBDA_ACTIVE =
            Collections.synchronizedMap(new WeakHashMap<>());

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

    /**
     * Phase 16j R5 — removes an adapter from every internal tracking
     * collection. Called from {@link ScreenPanelAdapter#unregister()};
     * pairs the constructor-time {@link #trackPending}/
     * {@link #markTargetingDeclared} flow with a symmetric teardown.
     *
     * <p>Removes from: PENDING set, REGISTERED list, every cached
     * per-screen match list in {@code SCREEN_DATA}, and any lambda-active
     * entry in {@code LAMBDA_ACTIVE}. Idempotent. After untrack the
     * adapter cannot be re-registered without constructing a new one.
     */
    static void untrack(ScreenPanelAdapter adapter) {
        PENDING.remove(adapter);
        REGISTERED.remove(adapter);
        for (ScreenRenderData data : SCREEN_DATA.values()) {
            data.menuMatches().remove(adapter);
        }
        for (List<LambdaActiveEntry> entries : LAMBDA_ACTIVE.values()) {
            entries.removeIf(e -> e.adapter() == adapter);
        }
    }

    // Post-§0042 split: SlotGroupPanelAdapter pending/registered tracking +
    // its corresponding API surface lives in menukit-containers' parallel
    // SlotGroupPanelRegistry.

    // ── Lambda-path opacity registration (M9) ───────────────────────────

    /**
     * Registers a lambda-based adapter as active on the given screen for
     * opacity dispatch. Called from {@link ScreenPanelAdapter#activeOn}.
     *
     * <p>Idempotent over (screen, adapter) — a duplicate call replaces the
     * previous bounds-supplier rather than appending a second entry.
     */
    static void registerLambdaActive(net.minecraft.client.gui.screens.Screen screen,
                                      ScreenPanelAdapter adapter,
                                      java.util.function.Supplier<ScreenBounds> boundsSupplier) {
        boolean newRegistration;
        synchronized (LAMBDA_ACTIVE) {
            List<LambdaActiveEntry> entries = LAMBDA_ACTIVE.computeIfAbsent(
                    screen, k -> new ArrayList<>());
            // Replace existing entry for this adapter if present (idempotent).
            newRegistration = entries.stream().noneMatch(e -> e.adapter() == adapter);
            entries.removeIf(e -> e.adapter() == adapter);
            entries.add(new LambdaActiveEntry(adapter, boundsSupplier));
        }
        // Phase 14d-3 — fire onAttach on first registration only
        // (idempotent re-registrations skip; matches addRenderableWidget
        // semantics where adding the same widget twice is a vanilla error).
        if (newRegistration) {
            for (var element : adapter.getPanel().getElements()) {
                element.onAttach(screen);
            }
        }
    }

    /**
     * Unregisters a lambda-based adapter from the given screen for opacity
     * dispatch. Called from {@link ScreenPanelAdapter#deactivate}. No-op if
     * the (screen, adapter) pair was never registered.
     */
    static void unregisterLambdaActive(net.minecraft.client.gui.screens.Screen screen,
                                        ScreenPanelAdapter adapter) {
        boolean wasRegistered;
        synchronized (LAMBDA_ACTIVE) {
            List<LambdaActiveEntry> entries = LAMBDA_ACTIVE.get(screen);
            if (entries == null) return;
            wasRegistered = entries.stream().anyMatch(e -> e.adapter() == adapter);
            entries.removeIf(e -> e.adapter() == adapter);
            if (entries.isEmpty()) {
                LAMBDA_ACTIVE.remove(screen);
            }
        }
        // Phase 14d-3 — fire onDetach on actual unregistration only.
        if (wasRegistered) {
            for (var element : adapter.getPanel().getElements()) {
                element.onDetach(screen);
            }
        }
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

    // Post-§0042 split: SlotGroupContext snapshots live on
    // menukit-containers' SlotGroupPanelRegistry.

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

        // MenuContext + SlotGroupContext dispatch only for AbstractContainerScreen —
        // other screen types (title, pause menu, etc.) are outside scope.
        if (!(screen instanceof AbstractContainerScreen<?> acs)) return;

        // ── MenuContext matching ────────────────────────────────────────
        Class<? extends AbstractContainerScreen<?>> screenClass =
                asConcreteScreenClass(acs.getClass());
        List<ScreenPanelAdapter> menuMatches = new ArrayList<>();
        for (ScreenPanelAdapter adapter : registeredSnapshot()) {
            if (adapter.matches(screenClass)) {
                menuMatches.add(adapter);
            }
        }

        // Cache the menu-context match list. SlotGroupContext matches
        // resolve per-frame inside renderMatchingPanels / the click hook
        // below because menu.slots can mutate mid-session.
        SCREEN_DATA.put(acs, new ScreenRenderData(menuMatches));

        // Phase 17 — render dispatch via Screen.addRenderableOnly instead
        // of a mixin INVOKE injection. Renderables iterate during
        // Screen.render BEFORE the end-of-frame tooltip flush, so widgets
        // calling GuiGraphics.setTooltipForNextFrame during render get
        // their tooltip drawn in the same frame. The mixin path
        // (MenuKitPanelRenderMixin, removed in Phase 17) injected at
        // INVOKE renderCarriedItem — correct stratum for visual ordering
        // but the renderables-iteration path is the standard MC integration
        // point and matches how vanilla widgets render.
        //
        // The Renderable is auto-cleared by Screen.clearWidgets() on next
        // init() — no manual removal needed.
        if (!menuMatches.isEmpty()) {
            ((ScreenAccessor) screen).menuKit$addRenderableOnly(
                    (graphics, mx, my, partialTick) ->
                            renderMatchingPanels(acs, graphics, mx, my));
        }

        // Phase 14d-3 — fire onAttach lifecycle hook on each matched
        // adapter's panel elements so widget-wrapping elements (TextField
        // etc.) can register vanilla widgets via screen.addRenderableWidget.
        // Mirrored by onDetach fired from ScreenEvents.remove below.
        for (ScreenPanelAdapter adapter : menuMatches) {
            for (var element : adapter.getPanel().getElements()) {
                element.onAttach(screen);
            }
        }
        // Lambda-active adapters fire onAttach when .activeOn is called
        // (registerLambdaActive); onDetach when .deactivate runs.

        // ScreenEvents.remove fires when the screen is being removed.
        // Fire onDetach so widget-wrapping elements can unregister via
        // screen.removeWidget. Mirrors the onAttach above.
        ScreenEvents.remove(screen).register(removed -> {
            for (ScreenPanelAdapter adapter : menuMatches) {
                for (var element : adapter.getPanel().getElements()) {
                    element.onDetach(removed);
                }
            }
        });

        // Click dispatch via Fabric's hook — input doesn't have a render-
        // ordering constraint so no mixin is needed here. Render dispatch
        // happens via MenuKitPanelRenderMixin; see §8.2 of M8 for why the
        // render path can't use ScreenEvents.afterRender (tooltip layering).
        ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
            ScreenBounds frame = frameBounds(acs);
            boolean opaqueAtCursor = false;
            for (ScreenPanelAdapter adapter : menuMatches) {
                // Dispatch the click to this adapter; per-element handling
                // returns true when an element consumed it but we don't
                // need that bit downstream — M9 eats based on opaque-
                // coverage alone.
                adapter.mouseClicked(frame, event.x(), event.y(), event.button(), acs);
                // M9 click-through prohibition: when cursor is inside any
                // visible opaque panel's bounds, the click is eaten from
                // vanilla regardless of whether an element consumed it.
                // The previous element-dispatch loop above already routed
                // the click to the right element if any; vanilla shouldn't
                // additionally see the click if a panel is sitting opaquely
                // over the coords. (Pre-M9, this was a modal-only check;
                // M9 generalizes it to any opaque panel.)
                Panel panel = adapter.getPanel();
                if (!panel.isVisible() || !panel.isOpaque()) continue;
                var origin = adapter.getOrigin(frame, acs);
                if (origin.isEmpty()) continue;
                int padding = adapter.getPadding();
                int pw = panel.getWidth() + 2 * padding;
                int ph = panel.getHeight() + 2 * padding;
                int x = origin.get().x();
                int y = origin.get().y();
                if (event.x() >= x && event.x() < x + pw
                        && event.y() >= y && event.y() < y + ph) {
                    opaqueAtCursor = true;
                }
            }
            // Post-§0042 split: SlotGroupContext click dispatch lives on
            // menukit-containers' SlotGroupPanelRegistry, which registers its
            // own ScreenMouseEvents.allowMouseClick listener via its own
            // ScreenEvents.AFTER_INIT hookup. Behavior change: when a
            // MenuContext modal eats a click, the slot-group dispatch no
            // longer fires (Fabric's allowMouseClick stops at first false
            // return). Modal blocks all interaction — this is the correct
            // UX. See 16a REPORT for the rationale.

            // M9 opaque-dispatch decision — extracted to a pure static
            // method so /mkverify probes can exercise the logic without
            // spinning up a real screen.
            return !shouldEatOpaqueDispatch(opaqueAtCursor);
        });

        // Phase 14d-2 — scroll dispatch via Fabric's allowMouseScroll hook.
        // Mirrors the click hook above: dispatches scroll events to matching
        // adapters' element layer (so ScrollContainer receives scroll
        // input). Modal-aware path is handled at the MouseHandler-level
        // mixin BEFORE this hook fires (the mixin cancels for outside-
        // modal scrolls and dispatches inside-modal scrolls directly).
        // This hook serves the non-modal case: regular scroll dispatch to
        // any adapter whose elements include a ScrollContainer.
        ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, hAmount, vAmount) -> {
            ScreenBounds frame = frameBounds(acs);
            for (ScreenPanelAdapter adapter : menuMatches) {
                if (adapter.mouseScrolled(frame, mouseX, mouseY, hAmount, vAmount, acs)) {
                    // Element consumed — eat from vanilla so screen.mouseScrolled
                    // doesn't double-dispatch (e.g., to creative-tab scroll).
                    return false;
                }
            }
            return true;
        });

        // Phase 14d-2 — release dispatch via Fabric's allowMouseRelease hook.
        // Used by ScrollContainer (and future draggable elements) to detect
        // drag end. Unlike click dispatch, release fires for every visible
        // element regardless of cursor position — drag-end is detected
        // even when the user has dragged the cursor off the element.
        ScreenMouseEvents.allowMouseRelease(screen).register((s, event) -> {
            ScreenBounds frame = frameBounds(acs);
            for (ScreenPanelAdapter adapter : menuMatches) {
                adapter.mouseReleased(frame, event.x(), event.y(), event.button(), acs);
            }
            return true;
        });
    }

    // Post-§0042 split: dispatchSlotGroupClicks moved to
    // menukit-containers' SlotGroupPanelRegistry along with the slot-group
    // adapter tracking and the AFTER_INIT listener that registers it.

    /**
     * Called from {@link com.trevorschoeny.menukit.mixin.MenuKitPanelRenderMixin}
     * at the injection point in {@code AbstractContainerScreen.render}
     * (before {@code renderCarriedItem}). Dispatches all matching MenuContext
     * and SlotGroupContext adapters for the current screen. No-op for
     * screens with no matches, or for screens opened before
     * {@link #onScreenInit} populated the cache (shouldn't happen in
     * practice — AFTER_INIT fires before the first render).
     *
     * <p>Public visibility required because the mixin is in a different
     * package ({@code mixin}) from this class ({@code inject}).
     */
    public static void renderMatchingPanels(AbstractContainerScreen<?> screen,
                                             net.minecraft.client.gui.GuiGraphics graphics,
                                             int mouseX, int mouseY) {
        ScreenRenderData data = SCREEN_DATA.get(screen);
        if (data == null) return;

        // Phase 14d-1 / M9 two-pass dim render order:
        //   (1) panels without dimsBehind render first
        //   (2) if any panel with dimsBehind(true) visible: render dim
        //       overlay covering full screen window — covers vanilla +
        //       step-(1) panels
        //   (3) dim panels render last on top of dim layer
        //
        // Single-pass per-adapter dim (14d-1 round-3 v1) was order-
        // fragile — only worked when dim panel iterated last. Two-pass
        // enforces visual order architecturally regardless of
        // registration order. M9 gates on panel.dimsBehind() instead of
        // panel.cancelsUnhandledClicks() — modal panels still trigger
        // dim (modal() sugar sets dimsBehind=true), but non-modal opaque
        // panels (popovers, dropdowns) don't.
        ScreenBounds frame = frameBounds(screen);

        // Pass 1 — non-dim adapters.
        for (ScreenPanelAdapter adapter : data.menuMatches) {
            if (adapter.getPanel().dimsBehind()) continue;
            adapter.render(graphics, frame, mouseX, mouseY, screen);
        }

        // Pass 2 — dim overlay if any dimsBehind panel visible. ~75% black,
        // covers full screen window. Tuned to match vanilla's confirm-
        // screen darkening (§4.10 smoke verdict).
        if (hasVisibleDimsBehindOnScreen(screen)) {
            graphics.fill(0, 0, screen.width, screen.height, 0xC0000000);
        }

        // Pass 3 — dim adapters render on top of dim layer.
        for (ScreenPanelAdapter adapter : data.menuMatches) {
            if (!adapter.getPanel().dimsBehind()) continue;
            adapter.render(graphics, frame, mouseX, mouseY, screen);
        }

        // Phase 14d-1 / M9 tooltip suppression — handled by
        // MenuKitTooltipSuppressMixin (HEAD-cancellable on
        // GuiGraphics.setTooltipForNextFrameInternal). Round-2
        // implementation finding: the render-path clear approach was
        // insufficient because creative-mode tab tooltips queue AFTER
        // super.render returns. Suppressing at the queueing site is
        // robust. M9 generalized the gate from
        // hasAnyVisibleModal → hasAnyVisibleOpaquePanelAtCursor;
        // pointer-driven bounds-localized suppression.

        // Post-§0042 split: SlotGroupContext per-frame render loop moved to
        // menukit-containers' SlotGroupPanelRegistry.renderMatchingPanels,
        // which is invoked by a separate mixin (SlotGroupPanelRenderMixin)
        // injecting at the same point on AbstractContainerScreen.render.
    }

    /**
     * M9 pure decision used by the {@code allowMouseClick} hook to
     * determine whether a click should be eaten from vanilla.
     *
     * <p>Returns {@code true} when the cursor is inside any visible opaque
     * panel's bounds — vanilla shouldn't see the click since the panel is
     * sitting opaquely over the coords. Returns {@code false} otherwise.
     *
     * <p>Extracted from the click-hook closure so {@code /mkverify} probes
     * can test the decision without instantiating a screen.
     */
    public static boolean shouldEatOpaqueDispatch(boolean opaqueAtCursor) {
        return opaqueAtCursor;
    }

    /**
     * M9 opaque click dispatch — combined dispatch + eat decision called
     * from {@code MenuKitModalMouseHandlerMixin} at the HEAD of
     * {@code MouseHandler.onButton}. Fires before any per-Screen routing
     * so subclass-specific click handling (creative-mode tabs, etc.)
     * doesn't pre-empt the opacity decision.
     *
     * <p>Decision tree:
     * <ul>
     *   <li><b>Click inside any visible opaque panel</b> — dispatches to
     *       that panel's adapter (so its element layer gets the click),
     *       returns {@code true} to signal eat. Vanilla never sees the
     *       click.</li>
     *   <li><b>Click outside all opaque panels + a tracksAsModal panel
     *       visible</b> — returns {@code true} (eat) without dispatching.
     *       Modal-tracking blocks underlying interaction outside its
     *       bounds (preserves 14d-1 modal semantics).</li>
     *   <li><b>Click outside all opaque panels + no tracksAsModal panel</b>
     *       — returns {@code false}; vanilla dispatch proceeds and the
     *       Fabric {@code allowMouseClick} hook handles non-modal
     *       region-based click dispatch normally.</li>
     * </ul>
     *
     * <p>Successor to 14d-1's {@code dispatchModalClick}, generalized for
     * non-modal opaque panels. Same atomic-dispatch-and-eat shape.
     */
    public static boolean dispatchOpaqueClick(Screen screen,
                                               double mouseX, double mouseY,
                                               int button) {
        ScreenPanelAdapter target = findOpaquePanelAt(screen, mouseX, mouseY);

        if (target != null) {
            // Cursor inside an opaque panel — dispatch to its element
            // layer so buttons/elements get the click. Then eat;
            // vanilla chain doesn't see this click.
            ScreenBounds bounds = boundsForAdapter(screen, target);
            if (bounds != null) {
                target.mouseClicked(bounds, mouseX, mouseY, button,
                        screen instanceof AbstractContainerScreen<?> acs ? acs : null);
            }
            return true;
        }

        // No opaque panel under cursor. If a tracksAsModal panel is
        // visible, eat anyway (modal blocks outside-bounds interaction).
        if (hasAnyVisibleModalTracking()) {
            return true;
        }

        // No opaque + no modal-tracking — vanilla proceeds normally.
        return false;
    }

    /**
     * M9 opaque release dispatch — symmetric counterpart to {@link
     * #dispatchOpaqueClick}. Called from
     * {@code MenuKitModalMouseHandlerMixin.onButton} when {@code action == 0}
     * (GLFW_RELEASE).
     *
     * <p><b>Why this exists (smoke fold-inline finding):</b>
     * Initial M9 implementation passed releases through unconditionally
     * (let Fabric {@code allowMouseRelease} handle drag-end for
     * ScrollContainer). That broke modal blocking for vanilla
     * release-driven UIs: {@code CreativeModeInventoryScreen.mouseReleased}
     * is what selects creative tabs (not {@code mouseClicked}), so
     * passed-through releases switched tabs while a modal was visible.
     *
     * <p>Symmetric handling: when the press would have been eaten (opaque
     * at cursor OR modal-tracking visible), eat the release too. Since
     * eating cancels the entire {@code MouseHandler.onButton} chain,
     * Fabric's {@code allowMouseRelease} hook can't fire — so this method
     * also manually dispatches {@code adapter.mouseReleased} to all
     * visible opaque adapters' elements (drag-end semantic preserved).
     *
     * <p>Decision tree:
     * <ul>
     *   <li><b>Cursor inside any visible opaque panel</b> — eat at
     *       mixin level; manually dispatch {@code mouseReleased} to all
     *       visible opaque adapters so any in-progress drag (on a
     *       ScrollContainer or future draggable element) ends.</li>
     *   <li><b>Cursor outside opaque + tracksAsModal panel visible</b> —
     *       eat at mixin level (modal blocks tab selection on release).
     *       Still dispatch {@code mouseReleased} to opaque adapters in
     *       case a drag started inside an opaque panel and cursor moved
     *       outside before release.</li>
     *   <li><b>No opaque + no modal-tracking</b> — return false (don't
     *       eat); release passes through to vanilla → Fabric
     *       {@code allowMouseRelease} → adapter.mouseReleased dispatch
     *       (existing 14d-2 plumbing).</li>
     * </ul>
     */
    public static boolean dispatchOpaqueRelease(Screen screen,
                                                 double mouseX, double mouseY,
                                                 int button) {
        if (screen == null) return false;

        ScreenPanelAdapter opaqueAtCursor = findOpaquePanelAt(screen, mouseX, mouseY);
        boolean modalTracking = hasAnyVisibleModalTracking();

        if (opaqueAtCursor == null && !modalTracking) {
            // No opaque + no modal-tracking — vanilla path (Fabric
            // allowMouseRelease) handles non-opaque drag-end normally.
            return false;
        }

        // Eat at mixin level + manually dispatch mouseReleased to all
        // visible opaque adapters' elements. Fabric's allowMouseRelease
        // hook won't fire since onButton is canceled, so we dispatch
        // here directly.
        if (screen instanceof AbstractContainerScreen<?> acs) {
            ScreenRenderData data = SCREEN_DATA.get(acs);
            if (data != null) {
                ScreenBounds frame = frameBounds(acs);
                for (ScreenPanelAdapter adapter : data.menuMatches) {
                    Panel panel = adapter.getPanel();
                    if (!panel.isVisible() || !panel.isOpaque()) continue;
                    adapter.mouseReleased(frame, mouseX, mouseY, button, acs);
                }
            }
        }
        // Lambda-active opaque adapters too.
        synchronized (LAMBDA_ACTIVE) {
            List<LambdaActiveEntry> entries = LAMBDA_ACTIVE.get(screen);
            if (entries != null) {
                for (LambdaActiveEntry entry : entries) {
                    ScreenPanelAdapter adapter = entry.adapter();
                    Panel panel = adapter.getPanel();
                    if (!panel.isVisible() || !panel.isOpaque()) continue;
                    ScreenBounds bounds = entry.boundsSupplier().get();
                    if (bounds == null) continue;
                    adapter.mouseReleased(bounds, mouseX, mouseY, button,
                            screen instanceof AbstractContainerScreen<?> a ? a : null);
                }
            }
        }

        return true;
    }

    /**
     * M9 opaque scroll dispatch — parallels {@link #dispatchOpaqueClick}.
     * Called from {@code MenuKitModalMouseHandlerMixin.onScroll} at the
     * HEAD of {@code MouseHandler.onScroll}.
     *
     * <p>Cursor inside an opaque panel: dispatch scroll to its elements;
     * return true. Cursor outside + tracksAsModal visible: eat without
     * dispatch. Cursor outside + no modal-tracking: pass through.
     */
    public static boolean dispatchOpaqueScroll(Screen screen,
                                                double mouseX, double mouseY,
                                                double scrollX, double scrollY) {
        ScreenPanelAdapter target = findOpaquePanelAt(screen, mouseX, mouseY);

        if (target != null) {
            ScreenBounds bounds = boundsForAdapter(screen, target);
            if (bounds != null) {
                target.mouseScrolled(bounds, mouseX, mouseY, scrollX, scrollY,
                        screen instanceof AbstractContainerScreen<?> acs ? acs : null);
            }
            return true;
        }

        if (hasAnyVisibleModalTracking()) {
            return true;
        }

        return false;
    }

    /**
     * M9 unified opacity query — finds the topmost (last-registered)
     * visible opaque panel whose bounds contain the cursor. Iterates BOTH
     * region-based adapters (via {@code SCREEN_DATA}) AND lambda-active
     * adapters (via {@code LAMBDA_ACTIVE}) so both paths participate in
     * the click-through prohibition.
     *
     * <p>Iteration order: region adapters first (in registration order),
     * then lambda adapters (in registration order). Highest-z = last-
     * registered wins; iterate forward and overwrite.
     *
     * @return the topmost opaque adapter at coords, or {@code null} if
     *         none visible OR cursor outside all visible opaque panels
     */
    public static @Nullable ScreenPanelAdapter findOpaquePanelAt(Screen screen,
                                                                  double mouseX, double mouseY) {
        if (screen == null) return null;

        ScreenPanelAdapter result = null;

        // Region-based adapters on AbstractContainerScreen.
        if (screen instanceof AbstractContainerScreen<?> acs) {
            ScreenRenderData data = SCREEN_DATA.get(acs);
            if (data != null) {
                ScreenBounds frame = frameBounds(acs);
                for (ScreenPanelAdapter adapter : data.menuMatches) {
                    Panel panel = adapter.getPanel();
                    if (!panel.isVisible() || !panel.isOpaque()) continue;
                    var origin = adapter.getOrigin(frame, acs);
                    if (origin.isEmpty()) continue;
                    if (containsPoint(origin.get(), adapter.getPadding(),
                            panel.getWidth(), panel.getHeight(),
                            mouseX, mouseY)) {
                        result = adapter; // overwrite — last-z wins
                    }
                }
            }
        }

        // Lambda-active adapters (any Screen subclass).
        synchronized (LAMBDA_ACTIVE) {
            List<LambdaActiveEntry> entries = LAMBDA_ACTIVE.get(screen);
            if (entries != null) {
                for (LambdaActiveEntry entry : entries) {
                    ScreenPanelAdapter adapter = entry.adapter();
                    Panel panel = adapter.getPanel();
                    if (!panel.isVisible() || !panel.isOpaque()) continue;
                    ScreenBounds bounds = entry.boundsSupplier().get();
                    if (bounds == null) continue;
                    var origin = adapter.getOriginForScreen(bounds, screen);
                    if (origin.isEmpty()) continue;
                    if (containsPoint(origin.get(), adapter.getPadding(),
                            panel.getWidth(), panel.getHeight(),
                            mouseX, mouseY)) {
                        result = adapter;
                    }
                }
            }
        }

        return result;
    }

    /** Helper: tests whether (mouseX, mouseY) is within the panel's bounding box. */
    private static boolean containsPoint(ScreenOrigin origin, int padding,
                                          int panelWidth, int panelHeight,
                                          double mouseX, double mouseY) {
        int pw = panelWidth + 2 * padding;
        int ph = panelHeight + 2 * padding;
        int x = origin.x();
        int y = origin.y();
        return mouseX >= x && mouseX < x + pw
                && mouseY >= y && mouseY < y + ph;
    }

    /**
     * Helper: returns the {@link ScreenBounds} appropriate for an
     * adapter on the given screen — frame bounds for region adapters
     * on AbstractContainerScreen; supplier-evaluated bounds for lambda
     * adapters. Returns null if no bounds available (adapter not
     * registered for this screen).
     */
    private static @Nullable ScreenBounds boundsForAdapter(Screen screen,
                                                            ScreenPanelAdapter adapter) {
        if (adapter.isRegionBased()) {
            if (screen instanceof AbstractContainerScreen<?> acs) {
                return frameBounds(acs);
            }
            return null; // region adapter on non-AbstractContainerScreen — shouldn't happen
        }
        // Lambda — find supplier in LAMBDA_ACTIVE.
        synchronized (LAMBDA_ACTIVE) {
            List<LambdaActiveEntry> entries = LAMBDA_ACTIVE.get(screen);
            if (entries == null) return null;
            for (LambdaActiveEntry entry : entries) {
                if (entry.adapter() == adapter) {
                    return entry.boundsSupplier().get();
                }
            }
        }
        return null;
    }

    /**
     * M9 query: is any visible panel with {@code tracksAsModal(true)} on
     * the current screen? Gates global suppressions (cursor lock, keyboard
     * eating, outside-bounds click eating).
     *
     * <p>Used by {@code MenuKitModalKeyboardHandlerMixin}, the per-tick
     * cursor-lock callback in {@code MenuKitClient}, and {@link
     * #dispatchOpaqueClick} / {@link #dispatchOpaqueScroll}.
     */
    public static boolean hasVisibleModalTrackingOnScreen(AbstractContainerScreen<?> screen) {
        ScreenRenderData data = SCREEN_DATA.get(screen);
        if (data == null) return false;
        for (ScreenPanelAdapter adapter : data.menuMatches) {
            Panel panel = adapter.getPanel();
            if (panel.isVisible() && panel.tracksAsModal()) return true;
        }
        // Lambda-active modal-tracking panels too.
        synchronized (LAMBDA_ACTIVE) {
            List<LambdaActiveEntry> entries = LAMBDA_ACTIVE.get(screen);
            if (entries != null) {
                for (LambdaActiveEntry entry : entries) {
                    Panel panel = entry.adapter().getPanel();
                    if (panel.isVisible() && panel.tracksAsModal()) return true;
                }
            }
        }
        return false;
    }

    /**
     * M9 query: is any visible panel with {@code tracksAsModal(true)} on
     * the currently-active screen? Same as {@link
     * #hasVisibleModalTrackingOnScreen} but reads
     * {@code Minecraft.getInstance().screen} for callers without a
     * screen reference (tooltip suppression mixin, cursor-lock callback).
     *
     * <p>Generalized to any {@link Screen} subclass — lambda adapters on
     * standalone vanilla screens (PauseScreen, etc.) participate too.
     */
    public static boolean hasAnyVisibleModalTracking() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null) return false;
        var screen = mc.screen;
        if (screen == null) return false;
        if (screen instanceof AbstractContainerScreen<?> acs) {
            if (hasVisibleModalTrackingOnScreen(acs)) return true;
        }
        // Lambda-active on any screen subclass.
        synchronized (LAMBDA_ACTIVE) {
            List<LambdaActiveEntry> entries = LAMBDA_ACTIVE.get(screen);
            if (entries != null) {
                for (LambdaActiveEntry entry : entries) {
                    Panel panel = entry.adapter().getPanel();
                    if (panel.isVisible() && panel.tracksAsModal()) return true;
                }
            }
        }
        return false;
    }

    /**
     * M9 query: is any visible panel with {@code dimsBehind(true)} on
     * the given screen? Gates the dim-overlay render pass in {@link
     * #renderMatchingPanels}.
     */
    public static boolean hasVisibleDimsBehindOnScreen(AbstractContainerScreen<?> screen) {
        ScreenRenderData data = SCREEN_DATA.get(screen);
        if (data == null) return false;
        for (ScreenPanelAdapter adapter : data.menuMatches) {
            Panel panel = adapter.getPanel();
            if (panel.isVisible() && panel.dimsBehind()) return true;
        }
        return false;
    }

    /**
     * M9 query: is the cursor currently inside any visible opaque panel
     * on the active screen? Convenience boolean wrapper around {@link
     * #findOpaquePanelAt} for callers that don't need the adapter and
     * have the mouse coords already (e.g., the slot-hover mixin which
     * receives mouseX/mouseY as method parameters).
     *
     * <p>Used by slot-hover suppression mixin (pointer-driven suppression
     * per M9 §4.7).
     */
    public static boolean hasAnyVisibleOpaquePanelAt(double mouseX, double mouseY) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null) return false;
        return findOpaquePanelAt(mc.screen, mouseX, mouseY) != null;
    }

    /**
     * M9 query: is the cursor currently inside any visible opaque panel
     * on the active screen? Reads cursor position from {@code MouseHandler}
     * directly — for callers without mouse coords as parameters (e.g.,
     * the tooltip-suppression mixin which fires from inside
     * {@code GuiGraphics.setTooltipForNextFrameInternal} without mouse
     * coords passed in).
     *
     * <p>Same coordinate-conversion formula as
     * {@code MenuKitModalMouseHandlerMixin} — uses
     * {@code Window.getScreenWidth/Height} (logical pixels) for HiDPI
     * correctness, NOT {@code getWidth/Height} (framebuffer pixels).
     */
    public static boolean hasAnyVisibleOpaquePanelAtCursor() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null) return false;
        var window = mc.getWindow();
        var mouseHandler = mc.mouseHandler;
        if (window == null || mouseHandler == null) return false;
        // Convert raw cursor coords to GUI-scaled coords.
        double rawX = mouseHandler.xpos();
        double rawY = mouseHandler.ypos();
        double scaledX = rawX * window.getGuiScaledWidth() / window.getScreenWidth();
        double scaledY = rawY * window.getGuiScaledHeight() / window.getScreenHeight();
        return findOpaquePanelAt(mc.screen, scaledX, scaledY) != null;
    }

    // Post-§0042 split: computeSlotGroupBounds moved to menukit-containers'
    // SlotGroupPanelRegistry — references vanilla Slot + SlotGroupBounds
    // (containers).

    /**
     * Reads the screen's frame bounds via
     * {@link AbstractContainerScreenAccessor}. Computed per-frame because
     * {@code leftPos}/{@code topPos} shift on resize and recipe-book toggle.
     */
    private static ScreenBounds frameBounds(AbstractContainerScreen<?> screen) {
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        return new ScreenBounds(
                acc.menuKit$getLeftPos(),
                acc.menuKit$getTopPos(),
                acc.menuKit$getImageWidth(),
                acc.menuKit$getImageHeight());
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
        Set<ScreenPanelAdapter> pendingMenu = pendingSnapshot();
        if (pendingMenu.isEmpty()) return;

        StringBuilder msg = new StringBuilder("MenuKit: ");
        msg.append(pendingMenu.size())
           .append(" region-based ScreenPanelAdapter(s) constructed but never " +
                   "declared targeting (.on / .onAny). Panel IDs: ");
        boolean first = true;
        for (ScreenPanelAdapter adapter : pendingMenu) {
            if (!first) msg.append(", ");
            msg.append(adapter.getPanel().getId());
            first = false;
        }
        msg.append(". Fix by adding the missing .on(...) call(s).");
        String message = msg.toString();
        LOGGER.error("[ScreenPanelRegistry] {}", message);
        throw new IllegalStateException(message);
    }

    // Post-§0042 split: SlotGroupPanelAdapter orphan validation runs
    // independently in menukit-containers' SlotGroupPanelRegistry's own
    // checkpoint. Both checkpoints fire on first screen-open and throw
    // independently if their respective pending sets are non-empty.
}
