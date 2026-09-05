package com.trevorschoeny.menukit.inject;

import com.trevorschoeny.menukit.core.MKFocus;
import com.trevorschoeny.menukit.core.Panel;
import com.trevorschoeny.menukit.core.PanelElement;
import com.trevorschoeny.menukit.window.ClientWindowVisibility;
import com.trevorschoeny.menukit.mixin.AbstractContainerScreenAccessor;
import com.trevorschoeny.menukit.mixin.ScreenAccessor;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
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
 *   <li><b>Default checkpoint.</b> On the first screen-open event after init,
 *       {@link #applyEverywhereDefault} runs. Any adapter still in
 *       {@link #PENDING} declared no targeting, so it defaults to <b>every</b>
 *       container screen — the uniform "default-on, opt-out" model shared with
 *       {@code MKCContainerPanel}. Narrow it
 *       deliberately with {@code .on(...)} / {@code .onPlayerInventory()} /
 *       {@code .onMatching(allExcept(...))}.</li>
 *   <li><b>Dispatch.</b> For each opened {@link AbstractContainerScreen},
 *       walk {@link #REGISTERED} and for adapters whose targeting matches
 *       the screen class, cache the match list in {@link #SCREEN_DATA}
 *       and register a {@code ScreenMouseEvents.allowMouseClick} hook.
 *       Render dispatch runs via
 *       {@code MKPanelRenderMixin}
 *       (injects at {@code INVOKE renderCarriedItem} so panels land in
 *       the right render stratum — see M8 §8.2 for why Fabric's
 *       {@code afterRender} is the wrong hook for render). Fabric handles
 *       per-screen click-hook lifetime cleanup when the screen closes.</li>
 * </ol>
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
    // MenuContext opacity dispatch on container screens.

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
    // menuMatches is a CopyOnWriteArrayList so that {@link #untrack} (called
    // from a consumer's element-click callback when it toggles a feature off
    // mid-dispatch) can remove an adapter WHILE a render/input loop is
    // iterating this same list. A plain ArrayList would throw
    // ConcurrentModificationException inside the screen's input loop and wedge
    // the entire screen's input. COW makes removal iteration-safe: the
    // in-flight iterator sees the pre-removal snapshot, and the removal takes
    // effect on the next dispatch. The list is small (a few adapters) and
    // mutated only on register/unregister, so the copy cost is negligible.
    private record ScreenRenderData(List<ScreenPanelAdapter> menuMatches) {}

    private static final Map<AbstractContainerScreen<?>, ScreenRenderData> SCREEN_DATA =
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
     * <p>Removes from: PENDING set, REGISTERED list, and every cached
     * per-screen match list in {@code SCREEN_DATA}. Idempotent. After
     * untrack the adapter cannot be re-registered without constructing a
     * new one.
     */
    static void untrack(ScreenPanelAdapter adapter) {
        PENDING.remove(adapter);
        REGISTERED.remove(adapter);
        for (ScreenRenderData data : SCREEN_DATA.values()) {
            data.menuMatches().remove(adapter);
        }
    }

    // Post-§0042 split: SlotGroupPanelAdapter pending/registered tracking +
    // its corresponding API surface lives in menukit-containers' parallel
    // SlotGroupPanelRegistry.

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
     * Called once from {@code MKClient.onInitializeClient}. After this,
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
        // Promote any untargeted region adapters to the everywhere-default.
        // This runs on EVERY screen-open, not just the first. Init-time adapters
        // complete their fluent targeting chain before the first screen opens,
        // but an adapter constructed MID-SESSION (e.g. a consumer toggles a
        // feature on after the initial screen-open) also joins PENDING and must
        // be promoted, or it silently never matches any screen. Idempotent and
        // cheap — applyEverywhereDefault early-returns when PENDING is empty
        // (the steady state), so the per-open cost is one set snapshot.
        applyEverywhereDefault();
        if (!checkpointRun) {
            checkpointRun = true;
            // Phase 18s — non-container vanilla adapters keep the explicit-
            // targeting requirement (an everywhere-default makes no sense on
            // Options/title/etc.); their orphan-validation warn stays one-shot
            // to avoid per-open log spam.
            VanillaScreenPanelRegistry.validateTargetingDeclared();
        }

        // Phase 18s — branch on screen type:
        //   - Container screens (inventory, chest, furnace, etc.) → existing
        //     MenuContext + SlotGroupContext dispatch below.
        //   - Non-container screens (Options, Controls, KeyBinds, world-
        //     select, server-list, title, etc.) → VanillaScreenPanelRegistry
        //     parallel dispatch path. Same AFTER_INIT event, two paths.
        if (!(screen instanceof AbstractContainerScreen<?> acs)) {
            VanillaScreenPanelRegistry.onScreenInit(screen);
            return;
        }

        // ── MenuContext matching ────────────────────────────────────────
        Class<? extends AbstractContainerScreen<?>> screenClass =
                asConcreteScreenClass(acs.getClass());
        // CopyOnWriteArrayList — see ScreenRenderData: lets untrack() remove an
        // adapter mid-dispatch (consumer toggles a feature off from a click
        // callback) without a ConcurrentModificationException wedging input.
        List<ScreenPanelAdapter> menuMatches = new java.util.concurrent.CopyOnWriteArrayList<>();
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
        // calling GuiGraphicsExtractor.setTooltipForNextFrame during render get
        // their tooltip drawn in the same frame. The mixin path
        // (MKPanelRenderMixin, removed in Phase 17) injected at
        // INVOKE renderCarriedItem — correct stratum for visual ordering
        // but the renderables-iteration path is the standard MC integration
        // point and matches how vanilla widgets render.
        //
        // The Renderable is auto-cleared by Screen.clearWidgets() on next
        // init() — no manual removal needed.
        if (!menuMatches.isEmpty()) {
            ((ScreenAccessor) screen).mk$addRenderableOnly(
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
        // happens via MKPanelRenderMixin; see §8.2 of M8 for why the
        // render path can't use ScreenEvents.afterRender (tooltip layering).
        ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
            ScreenBounds frame = frameBounds(acs);
            // Dispatch the click to every adapter's element layer (per-element
            // handling routes it to the right element if any; we don't need the
            // consumed bit here — the eat decision is coverage-based).
            for (ScreenPanelAdapter adapter : menuMatches) {
                adapter.mouseClicked(frame, event.x(), event.y(), event.button(), acs);
            }
            // Click-through prohibition: eat from vanilla when the cursor is
            // over any panel that CLAIMS the point — an opaque background
            // (minus per-element holes) OR a solid opaque element. The SAME
            // claim test the MouseHandler-level eat and every hover/tooltip
            // suppressor use (findCoveringPanelAt → one predicate, no drift).
            // In practice the MouseHandler HEAD eat fires first for claimed
            // points and cancels this hook; this stays as a consistent backstop.
            boolean covered = findCoveringPanelAt(acs, event.x(), event.y()) != null;
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
            return !shouldEatCovered(covered);
        });

        // Keyboard dispatch via Fabric's allowKeyPress hook — the keyboard
        // parallel to the allowMouseClick hook above. Routes key presses to
        // the panel element layer so elements like Dropdown can do keyboard
        // navigation (arrows/Enter/Escape). Returns false (eat from vanilla)
        // when an element consumes, so vanilla doesn't also act on the key
        // (e.g., hotbar number keys, creative search focus). Not hit-tested —
        // keyboard isn't pointer-localized; each visible adapter is offered
        // the key until one consumes.
        ScreenKeyboardEvents.allowKeyPress(screen).register((s, keyEvent) -> {
            for (ScreenPanelAdapter adapter : menuMatches) {
                if (adapter.keyPressed(keyEvent.key(), keyEvent.scancode(),
                        keyEvent.modifiers())) {
                    return false;  // eat from vanilla
                }
            }
            // B3 modal-Escape fix (container-screen path): when a tracksAsModal
            // panel is up — the documented dialog-over-container pattern, where
            // ConfirmDialog/AlertDialog is hosted by a ScreenPanelAdapter at
            // MenuRegion.CENTER — treat Escape as "dismiss the topmost modal"
            // rather than letting vanilla close the whole container screen out
            // from under the dialog. Fire the modal panel's onEscape action
            // (the dialog builders wire onCancel/onAcknowledge there) and eat
            // the key. With no escape action set we still eat Escape so it
            // can't close the host screen while a modal is open. Mirrors
            // MKScreen.keyPressed.
            if (keyEvent.key() == GLFW.GLFW_KEY_ESCAPE) {
                Panel modal = topmostVisibleModalAmong(menuMatches);
                if (modal != null) {
                    Runnable escape = modal.getEscapeAction();
                    if (escape != null) escape.run();
                    return false;  // eat from vanilla — don't close the screen
                }
            }
            return true;
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
     * Called from {@code MKPanelRenderMixin}
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
                                             net.minecraft.client.gui.GuiGraphicsExtractor graphics,
                                             int mouseX, int mouseY) {
        ScreenRenderData data = SCREEN_DATA.get(screen);
        if (data == null) return;

        // Movement ① — overlay render order, unified with MKScreen's 3-pass:
        //   (1) NON-overlay panels render first (flow in their region)
        //   (2) if any panel with dimsBehind(true) visible: render dim
        //       overlay covering full screen window — covers vanilla +
        //       step-(1) panels
        //   (3) OVERLAY-positioned panels render last, on top of the dim layer
        //
        // The "render on top" pass gates on isOverlayPositioned() — the single
        // overlay authority — so EVERY overlay (PanelPosition.center(), a
        // dimsBehind panel, OR a tracksAsModal panel) draws on top, exactly as
        // MKScreen's overlay pass does. The dim FILL (Pass 2) stays gated on
        // dimsBehind() alone (M9 — the dim visual is independent of the on-top
        // ordering: an overlay can float on top without dimming). resolveMenuOrigin
        // independently re-centers these panels on the screen window, so an
        // overlay registered at ANY region floats centered + on top identically.
        //
        // Single-pass per-adapter dim (14d-1 round-3 v1) was order-fragile —
        // only worked when the dim panel iterated last. The pass split enforces
        // visual order architecturally regardless of registration order.
        ScreenBounds frame = frameBounds(screen);

        // Pass 1 — non-overlay adapters (flow-positioned).
        for (ScreenPanelAdapter adapter : data.menuMatches) {
            if (adapter.getPanel().isOverlayPositioned()) continue;
            adapter.render(graphics, frame, mouseX, mouseY, screen);
        }

        // Pass 2 — dim overlay if any dimsBehind panel visible. ~75% black,
        // covers full screen window. Tuned to match vanilla's confirm-
        // screen darkening (§4.10 smoke verdict). Gated on dimsBehind() alone
        // (M9) — independent of the on-top ordering below.
        if (hasVisibleDimsBehindOnScreen(screen)) {
            graphics.fill(0, 0, screen.width, screen.height, 0xC0000000);
        }

        // Pass 3 — overlay-positioned adapters render on top of the dim layer.
        for (ScreenPanelAdapter adapter : data.menuMatches) {
            if (!adapter.getPanel().isOverlayPositioned()) continue;
            adapter.render(graphics, frame, mouseX, mouseY, screen);
        }

        // Phase 14d-1 / M9 tooltip suppression — handled by
        // MKTooltipSuppressMixin (HEAD-cancellable on
        // GuiGraphicsExtractor.setTooltipForNextFrameInternal). Round-2
        // implementation finding: the render-path clear approach was
        // insufficient because creative-mode tab tooltips queue AFTER
        // super.render returns. Suppressing at the queueing site is
        // robust. M9 generalized the gate from
        // hasAnyVisibleModal → anyPanelCoversCursor;
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
    public static boolean shouldEatCovered(boolean covered) {
        return covered;
    }

    /**
     * M9 opaque click dispatch — combined dispatch + eat decision called
     * from {@code MKModalMouseHandlerMixin} at the HEAD of
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
    public static boolean dispatchCoveredClick(Screen screen,
                                               double mouseX, double mouseY,
                                               int button) {
        ScreenPanelAdapter target = findCoveringPanelAt(screen, mouseX, mouseY);

        if (target != null) {
            // Cursor inside an opaque panel — dispatch to its element
            // layer so buttons/elements get the click. Then eat;
            // vanilla chain doesn't see this click.
            ScreenBounds bounds = boundsForAdapter(screen, target);
            if (bounds != null) {
                target.mouseClicked(bounds, mouseX, mouseY, button,
                        screen instanceof AbstractContainerScreen<?> acs ? acs : null);
            }
            // Apply unified focus-janitor rule. Vanilla never sees this
            // click (we're about to eat), so the natural setFocused-on-
            // claim flow can't fire. Whether or not an MK element
            // claimed, if a focused MK widget exists and the click was
            // outside its bounds, blur. Clicks INSIDE the focused
            // widget's bounds (e.g., clicking an already-focused
            // TextField) are protected by the bounds check inside the
            // helper.
            MKFocus.blurOnOutsideBounds(screen, mouseX, mouseY);
            return true;
        }

        // No opaque panel under cursor. If a tracksAsModal panel is
        // visible, eat anyway (modal blocks outside-bounds interaction).
        if (hasAnyVisibleModalTracking()) {
            // Intentionally do NOT clearFocus here. The user clicked
            // outside the modal's bounds entirely; widgets focused
            // INSIDE the modal should retain focus (the modal-eat is
            // about blocking the underlying interaction, not about
            // tearing down the modal's own input state). Empty-panel-
            // space-clear semantics apply to in-panel clicks only.
            return true;
        }

        // No opaque + no modal-tracking — vanilla proceeds normally.
        return false;
    }

    /**
     * M9 opaque release dispatch — symmetric counterpart to {@link
     * #dispatchCoveredClick}. Called from
     * {@code MKModalMouseHandlerMixin.onButton} when {@code action == 0}
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
    public static boolean dispatchCoveredRelease(Screen screen,
                                                 double mouseX, double mouseY,
                                                 int button) {
        if (screen == null) return false;

        ScreenPanelAdapter covered = findCoveringPanelAt(screen, mouseX, mouseY);
        boolean modalTracking = hasAnyVisibleModalTracking();

        if (covered == null && !modalTracking) {
            // No opaque + no modal-tracking — vanilla path (Fabric
            // allowMouseRelease) handles non-opaque drag-end normally.
            return false;
        }

        // Eat at mixin level + manually dispatch mouseReleased to every
        // visible adapter's elements. Fabric's allowMouseRelease hook won't
        // fire since onButton is canceled, so we dispatch here directly —
        // matching the non-eaten Fabric path (which dispatches to ALL
        // adapters, not just opaque ones), so a covering element on a
        // non-opaque panel still gets its drag-end. Release is not hit-tested
        // (fires for every visible element regardless of cursor position).
        if (screen instanceof AbstractContainerScreen<?> acs) {
            ScreenRenderData data = SCREEN_DATA.get(acs);
            if (data != null) {
                ScreenBounds frame = frameBounds(acs);
                for (ScreenPanelAdapter adapter : data.menuMatches) {
                    Panel panel = adapter.getPanel();
                    if (!ClientWindowVisibility.panelShown(panel)) continue;
                    adapter.mouseReleased(frame, mouseX, mouseY, button, acs);
                }
            }
        }

        return true;
    }

    /**
     * M9 opaque scroll dispatch — parallels {@link #dispatchCoveredClick}.
     * Called from {@code MKModalMouseHandlerMixin.onScroll} at the
     * HEAD of {@code MouseHandler.onScroll}.
     *
     * <p>Cursor inside an opaque panel: dispatch scroll to its elements;
     * return true. Cursor outside + tracksAsModal visible: eat without
     * dispatch. Cursor outside + no modal-tracking: pass through.
     */
    public static boolean dispatchCoveredScroll(Screen screen,
                                                double mouseX, double mouseY,
                                                double scrollX, double scrollY) {
        ScreenPanelAdapter target = findCoveringPanelAt(screen, mouseX, mouseY);

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
     * Unified coverage query — finds the topmost (last-registered) visible
     * panel that <em>claims</em> the cursor point ({@link #panelClaimsPoint}:
     * an opaque background minus holes, OR a solid opaque element). Iterates
     * region adapters (via {@code SCREEN_DATA}) so they participate in the
     * click-through prohibition.
     *
     * <p>Iteration order: registration order. Highest-z = last-registered
     * wins; iterate forward and overwrite.
     *
     * <p>This is the dispatch-returning half of the inertness contract (it
     * returns the panel so the caller can route the input to its elements);
     * the boolean half consumers/suppressors call is
     * {@link com.trevorschoeny.menukit.core.MKFocus#isInertUnderPanel}.
     *
     * @return the topmost claiming adapter at coords, or {@code null} if none
     *         visible OR the cursor is over no panel's opaque background/element
     */
    public static @Nullable ScreenPanelAdapter findCoveringPanelAt(Screen screen,
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
                    if (!ClientWindowVisibility.panelShown(panel)) continue;
                    var origin = adapter.getOrigin(frame, acs);
                    if (origin.isEmpty()) continue;
                    if (panelClaimsPoint(panel, origin.get(), adapter.getPadding(),
                            mouseX, mouseY)) {
                        result = adapter; // overwrite — last-z wins
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
     * M9 per-element opacity — true if a visible, non-opaque element of this
     * panel covers the cursor, punching a click-through "hole" so the opaque
     * panel does NOT claim input at this point (it falls through to lower
     * panels / the slots behind it). Element screen bounds are composed exactly
     * as the dispatchers compose them: content origin (panel origin + padding)
     * plus the element's childX/childY (see {@link PanelElement#hitTest}).
     *
     * <p>Per-element opacity completes M9's panel-level opacity: panel opacity
     * is bounding-box click-eating (input, not visual alpha); a non-opaque
     * element opts its own bounds out of that eating. Default-opaque elements
     * are unaffected, so existing panels behave exactly as before.
     */
    static boolean panelHoleAt(Panel panel, ScreenOrigin origin, int padding,
                                        double mouseX, double mouseY) {
        int contentX = origin.x() + padding;
        int contentY = origin.y() + padding;
        for (PanelElement el : panel.getElements()) {
            if (!ClientWindowVisibility.elementShown(panel, el) || el.isElementOpaque()) continue;
            int ex = contentX + el.getChildX();
            int ey = contentY + el.getChildY();
            if (mouseX >= ex && mouseX < ex + el.getWidth()
                    && mouseY >= ey && mouseY < ey + el.getHeight()) {
                return true; // cursor is over a click-through hole
            }
        }
        return false;
    }

    /**
     * The unified "does this panel claim this screen point?" test — the core of
     * the inertness contract, shared across container AND vanilla-screen
     * adapters so every path agrees on ONE claim definition (no per-screen-type
     * fork). A panel claims P, in strict priority order:
     *
     * <ol>
     *   <li><b>Active overlay (any visible element):</b> an open transient
     *       overlay — a Dropdown popover, say — is an <em>exclusive</em> claim:
     *       top z, honored regardless of panel/element opacity
     *       ({@link #panelHasActiveOverlayAt}). Checked first so a popover always
     *       eats input behind it.</li>
     *   <li><b>Click-through hole:</b> a non-opaque element ({@link #panelHoleAt})
     *       is an unconditional pass-through — authoritative over BOTH the opaque
     *       background and any solid element, so an element overlapping a hole
     *       can't re-claim the point.</li>
     *   <li><b>(a) opaque background:</b> the panel is opaque and its padded
     *       bounds contain P (holes already excluded by step 2).</li>
     *   <li><b>(b) solid element:</b> a visible, opaque, <em>interactive</em>
     *       element covers P ({@link #panelHasSolidElementAt}) — so a solid
     *       button blocks the vanilla content behind it even when the panel
     *       background itself is non-opaque. Gated on interactivity so a
     *       render-only decoration never eats a click it does nothing with (the
     *       dead-click guard).</li>
     * </ol>
     *
     * <p>Steps 2 and 4 are duals of the per-element opacity flag: a non-opaque
     * element punches a hole in the background; a solid element forms a claim on
     * a transparent panel. Together they make "a solid element is here" ⟺ "the
     * vanilla behind it is inert" — what every suppression site must agree on.
     *
     * <p>Package-private (not {@code private}) so the vanilla-screen path
     * ({@link VanillaScreenPanelRegistry#hasOpaqueRegionAt},
     * {@link VanillaScreenPanelAdapter#mouseClicked}) consults the exact same
     * test — collapsing the per-screen-type claim fork the unification removes.
     */
    static boolean panelClaimsPoint(Panel panel, ScreenOrigin origin, int padding,
                                    double mouseX, double mouseY) {
        // (1) Active overlay = exclusive claim, top z, opacity-independent.
        if (panelHasActiveOverlayAt(panel, origin, padding, mouseX, mouseY)) {
            return true;
        }
        // (2) A hole is an unconditional pass-through — authoritative over the
        //     opaque background AND any solid element below it.
        if (panelHoleAt(panel, origin, padding, mouseX, mouseY)) {
            return false;
        }
        // (3) (a) opaque background — panel.isOpaque() AND the engine OPACITY key.
        if (ClientWindowVisibility.panelOpaque(panel)
                && containsPoint(origin, padding, panel.getWidth(), panel.getHeight(),
                        mouseX, mouseY)) {
            return true;
        }
        // (4) (b) solid (opaque + interactive) element.
        return panelHasSolidElementAt(panel, origin, padding, mouseX, mouseY);
    }

    /**
     * True if a visible element of the panel has an active overlay region
     * ({@link PanelElement#getActiveOverlayBounds} — e.g. an open Dropdown
     * popover) covering (mouseX, mouseY). An active overlay is an exclusive,
     * transient claim: honored for EVERY visible element regardless of opacity
     * (matching the dispatchers' overlay pass, which doesn't gate on opacity),
     * because the overlay region must be inert to anything behind it.
     */
    private static boolean panelHasActiveOverlayAt(Panel panel, ScreenOrigin origin, int padding,
                                                   double mouseX, double mouseY) {
        for (PanelElement el : panel.getElements()) {
            if (!ClientWindowVisibility.elementShown(panel, el)) continue;
            int[] overlay = el.getActiveOverlayBounds();
            if (overlay != null
                    && mouseX >= overlay[0] && mouseX < overlay[0] + overlay[2]
                    && mouseY >= overlay[1] && mouseY < overlay[1] + overlay[3]) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if a visible, SOLID (opaque + interactive) element of the panel covers
     * (mouseX, mouseY) via its interaction surface ({@link PanelElement#hitTest}).
     * The exact counterpart to {@link #panelHoleAt}: holes are non-opaque elements
     * that do NOT claim; this finds the solid elements that DO. Gated on
     * {@link PanelElement#isInteractive} as well as opacity, so a render-only
     * decoration (opaque by default but consuming nothing) can't eat a click on a
     * non-opaque panel — the dead-click guard. This is what lets a solid button
     * block the vanilla behind it on a non-opaque panel — the gap that let
     * creative-tab clicks/highlight leak through the pockets controls.
     */
    private static boolean panelHasSolidElementAt(Panel panel, ScreenOrigin origin, int padding,
                                                  double mouseX, double mouseY) {
        int contentX = origin.x() + padding;
        int contentY = origin.y() + padding;
        for (PanelElement el : panel.getElements()) {
            if (!ClientWindowVisibility.elementShown(panel, el) || !el.isElementOpaque() || !el.isInteractive()) continue;
            if (el.hitTest(mouseX, mouseY, contentX, contentY)) return true;
        }
        return false;
    }

    /**
     * Helper: returns the {@link ScreenBounds} for an adapter on the given
     * screen — frame bounds for the adapter's container screen. Returns null
     * if no bounds available (the screen isn't an
     * {@link AbstractContainerScreen} — shouldn't happen for a covering
     * region adapter).
     */
    private static @Nullable ScreenBounds boundsForAdapter(Screen screen,
                                                            ScreenPanelAdapter adapter) {
        if (screen instanceof AbstractContainerScreen<?> acs) {
            return frameBounds(acs);
        }
        return null;
    }

    /**
     * B3 modal-Escape helper: returns the topmost (last-registered, highest
     * z-order) visible {@code tracksAsModal} panel among the given adapters,
     * or {@code null} if none is up. Iterates in reverse so the most-recently
     * registered modal (paint-order-last) wins, matching the click-dispatch z
     * precedence. The container-screen {@code allowKeyPress} hook uses this to
     * route Escape to the topmost modal's {@code onEscape} action.
     */
    private static @Nullable Panel topmostVisibleModalAmong(List<ScreenPanelAdapter> adapters) {
        for (int i = adapters.size() - 1; i >= 0; i--) {
            Panel panel = adapters.get(i).getPanel();
            if (ClientWindowVisibility.panelShown(panel) && panel.tracksAsModal()) {
                return panel;
            }
        }
        return null;
    }

    /**
     * M9 query: is any visible panel with {@code tracksAsModal(true)} on
     * the current screen? Gates global suppressions (cursor lock, keyboard
     * eating, outside-bounds click eating).
     *
     * <p>Used by {@code MKModalKeyboardHandlerMixin}, the per-tick
     * cursor-lock callback in {@code MKClient}, and {@link
     * #dispatchCoveredClick} / {@link #dispatchCoveredScroll}.
     */
    public static boolean hasVisibleModalTrackingOnScreen(AbstractContainerScreen<?> screen) {
        ScreenRenderData data = SCREEN_DATA.get(screen);
        if (data == null) return false;
        for (ScreenPanelAdapter adapter : data.menuMatches) {
            Panel panel = adapter.getPanel();
            if (ClientWindowVisibility.panelShown(panel) && panel.tracksAsModal()) return true;
        }
        return false;
    }

    /**
     * M9 query: is any visible panel with {@code tracksAsModal(true)} on
     * the currently-active screen? Same as {@link
     * #hasVisibleModalTrackingOnScreen} but reads
     * {@code Minecraft.getInstance().gui.screen()} for callers without a
     * screen reference (tooltip suppression mixin, cursor-lock callback).
     */
    public static boolean hasAnyVisibleModalTracking() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null) return false;
        var screen = mc.gui.screen();
        if (screen == null) return false;
        if (screen instanceof AbstractContainerScreen<?> acs) {
            if (hasVisibleModalTrackingOnScreen(acs)) return true;
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
            if (ClientWindowVisibility.panelShown(panel) && panel.dimsBehind()) return true;
        }
        return false;
    }

    /**
     * M9 query: is the cursor currently inside any visible opaque panel
     * on the active screen? Convenience boolean wrapper around {@link
     * #findCoveringPanelAt} for callers that don't need the adapter and
     * have the mouse coords already (e.g., the slot-hover mixin which
     * receives mouseX/mouseY as method parameters).
     *
     * <p>Used by slot-hover suppression mixin (pointer-driven suppression
     * per M9 §4.7).
     */
    public static boolean anyPanelCoversPoint(double mouseX, double mouseY) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null) return false;
        return findCoveringPanelAt(mc.gui.screen(), mouseX, mouseY) != null;
    }

    /**
     * M9 query: is the cursor currently inside any visible opaque panel
     * on the active screen? Reads cursor position from {@code MouseHandler}
     * directly — for callers without mouse coords as parameters (e.g.,
     * the tooltip-suppression mixin which fires from inside
     * {@code GuiGraphicsExtractor.setTooltipForNextFrameInternal} without mouse
     * coords passed in).
     *
     * <p>Same coordinate-conversion formula as
     * {@code MKModalMouseHandlerMixin} — uses
     * {@code Window.getScreenWidth/Height} (logical pixels) for HiDPI
     * correctness, NOT {@code getWidth/Height} (framebuffer pixels).
     */
    /**
     * Post-Phase 18r-5: complement to {@link #anyPanelCoversPoint}
     * for ELEMENT-LEVEL active overlays — Dropdown popovers and any other
     * element whose {@code getActiveOverlayBounds()} extends beyond its
     * owning panel's bounds. The panel-bounds query misses these; this
     * query catches them.
     *
     * <p>Used by the widget-hover-suppression mixin so vanilla widgets
     * (buttons, list rows) covered by an open dropdown popover stop
     * highlighting on hover. The opacity-eat input path already routes
     * the CLICK away from them; this closes the visual loop.
     */
    public static boolean hasActiveOverlayAt(double mouseX, double mouseY) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null) return false;
        Screen screen = mc.gui.screen();
        if (screen == null) return false;

        // Container-screen region adapters.
        if (screen instanceof AbstractContainerScreen<?> acs) {
            ScreenRenderData data = SCREEN_DATA.get(acs);
            if (data != null) {
                for (ScreenPanelAdapter adapter : data.menuMatches) {
                    Panel panel = adapter.getPanel();
                    if (!ClientWindowVisibility.panelShown(panel)) continue;
                    for (var element : panel.getElements()) {
                        if (!ClientWindowVisibility.elementShown(panel, element)) continue;
                        int[] overlay = element.getActiveOverlayBounds();
                        if (overlay != null
                                && mouseX >= overlay[0] && mouseX < overlay[0] + overlay[2]
                                && mouseY >= overlay[1] && mouseY < overlay[1] + overlay[3]) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    public static boolean anyPanelCoversCursor() {
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
        return findCoveringPanelAt(mc.gui.screen(), scaledX, scaledY) != null;
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
                acc.mk$getLeftPos(),
                acc.mk$getTopPos(),
                acc.mk$getImageWidth(),
                acc.mk$getImageHeight());
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends AbstractContainerScreen<?>> asConcreteScreenClass(
            Class<?> raw) {
        return (Class<? extends AbstractContainerScreen<?>>) raw;
    }

    // ── Default targeting for undeclared adapters ───────────────────────

    /**
     * Applies the everywhere-default to region-based adapters that reached the
     * first screen-open without declaring targeting. An undeclared region-based
     * {@link ScreenPanelAdapter} defaults to <b>every</b> container screen — the
     * uniform "default-on, opt-out" model shared with
     * {@code MKCContainerPanel}. A consumer narrows it deliberately with
     * {@code .on(...)} / {@code .onPlayerInventory()} /
     * {@code .onMatching(ScreenMatcher.allExcept(...))}.
     *
     * <p>Runs on every screen-open after init (not just the first). Anything
     * still in {@link #PENDING} declared no targeting — whether it was built at
     * init or constructed mid-session by a runtime toggle. Each is promoted via
     * {@link ScreenPanelAdapter#onAny()} (sets the every-screen target and moves
     * it {@code PENDING → REGISTERED} so dispatch picks it up). Idempotent: the
     * method early-returns when {@link #PENDING} is empty.
     *
     * <p>Container screens only — non-container vanilla screens (Options, title,
     * …) keep the explicit-targeting requirement in
     * {@link VanillaScreenPanelRegistry#validateTargetingDeclared}; an
     * everywhere-default makes no sense there.
     */
    public static void applyEverywhereDefault() {
        Set<ScreenPanelAdapter> pendingMenu = pendingSnapshot();
        if (pendingMenu.isEmpty()) return;
        for (ScreenPanelAdapter adapter : pendingMenu) {
            // Promote to the every-container default (PENDING → REGISTERED).
            adapter.onAny();
            LOGGER.debug("[ScreenPanelRegistry] panel '{}' declared no targeting "
                    + "— defaulting to every container screen.",
                    adapter.getPanel().getId());
        }
    }

    // Post-§0042 split: SlotGroupPanelAdapter orphan validation runs
    // independently in menukit-containers' SlotGroupPanelRegistry's own
    // checkpoint. Both checkpoints fire on first screen-open and throw
    // independently if their respective pending sets are non-empty.
}
