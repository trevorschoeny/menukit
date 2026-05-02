package com.trevorschoeny.menukit.inject;

import com.trevorschoeny.menukit.core.Panel;
import com.trevorschoeny.menukit.core.SlotGroupCategory;
import com.trevorschoeny.menukit.mixin.AbstractContainerScreenAccessor;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

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

    // Parallel tracking for SlotGroupContext adapters. Distinct sets rather
    // than a shared abstract base because SlotGroupPanelAdapter has a
    // different render signature (takes SlotGroupBounds + category) than
    // ScreenPanelAdapter — unifying would dilute the call sites.
    private static final Set<SlotGroupPanelAdapter> PENDING_SLOT_GROUP =
            Collections.synchronizedSet(new HashSet<>());

    private static final List<SlotGroupPanelAdapter> REGISTERED_SLOT_GROUP =
            Collections.synchronizedList(new ArrayList<>());

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
     * Called from {@link SlotGroupPanelAdapter}'s constructor — marks the
     * slot-group adapter as pending (awaiting {@code .on(...)}).
     */
    static void trackPendingSlotGroup(SlotGroupPanelAdapter adapter) {
        PENDING_SLOT_GROUP.add(adapter);
    }

    /**
     * Called from {@link SlotGroupPanelAdapter#on}. Moves the adapter from
     * pending to registered.
     */
    static void markSlotGroupTargetingDeclared(SlotGroupPanelAdapter adapter) {
        PENDING_SLOT_GROUP.remove(adapter);
        REGISTERED_SLOT_GROUP.add(adapter);
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

    /** Returns an unmodifiable snapshot of orphan SlotGroupContext adapters. */
    public static Set<SlotGroupPanelAdapter> pendingSlotGroupSnapshot() {
        synchronized (PENDING_SLOT_GROUP) {
            return Set.copyOf(PENDING_SLOT_GROUP);
        }
    }

    /** Returns an unmodifiable snapshot of registered SlotGroupContext adapters. */
    public static List<SlotGroupPanelAdapter> registeredSlotGroupSnapshot() {
        synchronized (REGISTERED_SLOT_GROUP) {
            return List.copyOf(REGISTERED_SLOT_GROUP);
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

        // Click dispatch via Fabric's hook — input doesn't have a render-
        // ordering constraint so no mixin is needed here. Render dispatch
        // happens via MenuKitPanelRenderMixin; see §8.2 of M8 for why the
        // render path can't use ScreenEvents.afterRender (tooltip layering).
        ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
            ScreenBounds frame = frameBounds(acs);
            boolean consumed = false;
            boolean anyModal = false;
            for (ScreenPanelAdapter adapter : menuMatches) {
                if (adapter.mouseClicked(frame, event.x(), event.y(), event.button(), acs)) {
                    consumed = true;
                }
                // Track modality on visible panels — invisible panels' flags
                // are dormant. Per Phase 14d-1 dialog primitive: when any
                // visible matched panel has cancelsUnhandledClicks(true),
                // unhandled clicks (didn't hit an interactive element) are
                // eaten from vanilla rather than passed through.
                Panel panel = adapter.getPanel();
                if (panel.isVisible() && panel.cancelsUnhandledClicks()) {
                    anyModal = true;
                }
            }
            // Re-resolve slot groups per click — creative-tab transitions
            // and any future dynamic menu mutate menu.slots between clicks.
            // SlotGroupContext panels don't participate in modality (dialogs
            // are MenuContext-only per DIALOGS.md §4.5); slot-group dispatch
            // doesn't affect the modal-eat decision below.
            dispatchSlotGroupClicks(acs, event.x(), event.y(), event.button());
            // Modal click-eat decision — extracted to a pure static method
            // so /mkverify probes can exercise the logic without spinning up
            // a real screen.
            return !shouldEatUnhandledClick(anyModal, consumed);
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

    /**
     * Re-resolves slot groups for the given screen's menu and dispatches a
     * click to every matching (adapter, category) pair. Called per click
     * from the {@code ScreenMouseEvents.allowMouseClick} hook.
     */
    private static void dispatchSlotGroupClicks(AbstractContainerScreen<?> screen,
                                                  double mouseX, double mouseY, int button) {
        Map<SlotGroupCategory, List<Slot>> resolved = SlotGroupCategories.of(screen.getMenu());
        if (resolved.isEmpty()) return;
        for (SlotGroupPanelAdapter adapter : registeredSlotGroupSnapshot()) {
            List<SlotGroupCategory> targets = adapter.getTargets();
            if (targets == null) continue;
            for (SlotGroupCategory category : targets) {
                List<Slot> slots = resolved.get(category);
                if (slots == null || slots.isEmpty()) continue;
                SlotGroupBounds bounds = computeSlotGroupBounds(slots, screen);
                adapter.mouseClicked(bounds, category, mouseX, mouseY, button, screen);
            }
        }
    }

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

        // Phase 14d-1 two-pass modal render order:
        //   (1) non-modal MenuContext adapters render first
        //   (2) if any modal panel visible: render dim overlay covering
        //       full screen window — covers vanilla + step-(1) panels
        //   (3) modal adapters render last on top of dim
        //
        // Single-pass per-adapter dim (round-3 v1) was order-fragile —
        // only worked when modal iterated last. Two-pass enforces the
        // visual order architecturally regardless of registration order.
        ScreenBounds frame = frameBounds(screen);

        // Pass 1 — non-modal adapters.
        for (ScreenPanelAdapter adapter : data.menuMatches) {
            if (adapter.getPanel().cancelsUnhandledClicks()) continue;
            adapter.render(graphics, frame, mouseX, mouseY, screen);
        }

        // Pass 2 — dim overlay if any modal visible. ~75% black, covers
        // full screen window. Tuned to match vanilla's confirm-screen
        // darkening (§4.10 smoke verdict).
        if (hasVisibleModalOnScreen(screen)) {
            graphics.fill(0, 0, screen.width, screen.height, 0xC0000000);
        }

        // Pass 3 — modal adapters render on top of dim.
        for (ScreenPanelAdapter adapter : data.menuMatches) {
            if (!adapter.getPanel().cancelsUnhandledClicks()) continue;
            adapter.render(graphics, frame, mouseX, mouseY, screen);
        }

        // Phase 14d-1 modal tooltip suppression — handled by
        // MenuKitTooltipSuppressMixin (HEAD-cancellable on
        // GuiGraphics.setTooltipForNextFrameInternal). Round-2
        // implementation finding: the render-path clear approach was
        // insufficient because creative-mode tab tooltips queue AFTER
        // super.render returns. Suppressing at the queueing site is
        // robust. See ScreenPanelRegistry.hasAnyVisibleModal.

        // SlotGroupContext: re-resolve per frame. Creative-tab switches
        // and other dynamic menu mutations change menu.slots; caching the
        // resolved map at screen-open would produce stale bounds. Per-frame
        // resolution is cheap (resolvers do slot-index subList slicing on
        // menu.slots).
        Map<SlotGroupCategory, List<Slot>> resolved = SlotGroupCategories.of(screen.getMenu());
        if (resolved.isEmpty()) return;
        for (SlotGroupPanelAdapter adapter : registeredSlotGroupSnapshot()) {
            List<SlotGroupCategory> targets = adapter.getTargets();
            if (targets == null) continue;
            for (SlotGroupCategory category : targets) {
                List<Slot> slots = resolved.get(category);
                if (slots == null || slots.isEmpty()) continue;
                SlotGroupBounds bounds = computeSlotGroupBounds(slots, screen);
                adapter.render(graphics, bounds, category, mouseX, mouseY, screen);
            }
        }
    }

    /**
     * Pure decision used by the {@code allowMouseClick} hook to determine
     * whether an unhandled click should be eaten from vanilla.
     *
     * <p>Returns {@code true} when there is at least one visible matched
     * panel with {@link Panel#cancelsUnhandledClicks() cancelsUnhandledClicks}
     * set AND no adapter consumed the click — modal-active, click-missed,
     * eat from vanilla.
     *
     * <p>Returns {@code false} otherwise — either nothing modal is up, or
     * something consumed the click (in which case the click was already
     * dispatched correctly to a panel element and shouldn't be eaten
     * additionally).
     *
     * <p>Extracted from the click-hook closure so {@code /mkverify} probes
     * can test the decision without instantiating a screen.
     *
     * @param anyModalVisible true when at least one matched adapter's panel
     *                        is visible and has {@code cancelsUnhandledClicks(true)}
     * @param consumed        true when at least one matched adapter
     *                        consumed the click
     * @return {@code true} if the dispatcher should eat the click,
     *         {@code false} if vanilla should process it normally
     */
    public static boolean shouldEatUnhandledClick(boolean anyModalVisible, boolean consumed) {
        return anyModalVisible && !consumed;
    }

    /**
     * Phase 14d-1 modal mechanism — pure decision called from the
     * {@code MenuKitModalClickEatMixin} mixins (multi-target on
     * {@code Screen.mouseClicked} + the three vanilla
     * {@code AbstractContainerScreen} subclasses that override it). Fires at
     * the HEAD of each {@code mouseClicked} entry point so it can short-
     * circuit subclass-specific click handling (e.g., creative-mode tabs)
     * BEFORE that handling runs — the failure mode the simpler
     * {@code allowMouseClick} hook can't catch when subclass overrides
     * pre-empt their {@code super.mouseClicked(...)} call.
     *
     * <p>The decision: a click should be eaten when (a) at least one
     * registered adapter on the current screen has a visible panel with
     * {@link Panel#cancelsUnhandledClicks() cancelsUnhandledClicks(true)}
     * set, AND (b) the click coordinate is outside the bounds of every
     * such modal panel. Clicks inside any modal's bounds pass through
     * to that modal's element dispatch.
     *
     * <p>The "any-modal-eats-non-bounds" semantic — clicks outside every
     * visible modal are eaten regardless of cross-mod ordering. Two mods'
     * modal panels coexist independently; each consumer is expected to
     * gate their own modal visibility so only one is up at a time
     * per-consumer. Cross-mod overlap of modal panels is consumer
     * ergonomics, not library mediation. See DIALOGS.md §10 round-2
     * Principle 1 audit.
     *
     * @param screen the current screen (any {@link Screen} subclass)
     * @param mouseX click x-coordinate (screen space)
     * @param mouseY click y-coordinate (screen space)
     * @return {@code true} if the dispatching mixin should cancel the
     *         click via {@code cir.setReturnValue(true)}
     */
    /**
     * Phase 14d-1 modal click dispatch — combined dispatch + eat decision.
     *
     * <p>When a modal panel is visible on the current screen:
     * <ul>
     *   <li><b>Click inside any modal's bounds</b> — dispatches to that
     *       modal's adapter (so its button elements get the click), then
     *       returns {@code true} to signal eat. The vanilla screen
     *       hierarchy never sees the click — no slot pickup, no tab
     *       switching, no other dispatchers see the event. The modal owns
     *       the click entirely.</li>
     *   <li><b>Click outside every modal's bounds</b> — returns {@code true}
     *       (eat) without dispatching. Modal blocks underlying interaction.</li>
     *   <li><b>No modal visible</b> — returns {@code false}, vanilla dispatch
     *       proceeds normally and existing non-modal adapters fire via
     *       the Fabric {@code allowMouseClick} hook.</li>
     * </ul>
     *
     * <p>Round-2 fix for the post-smoke bug "click on Confirm button picks
     * up item behind it" — previous version only eat-without-dispatch for
     * outside clicks; inside clicks fell through, slots got picked up
     * before the button was reached via Fabric's hook. Combining dispatch
     * with eat at the mixin entry point ensures atomic modal-click handling.
     */
    public static boolean dispatchModalClick(Screen screen,
                                              double mouseX, double mouseY,
                                              int button) {
        if (!(screen instanceof AbstractContainerScreen<?> acs)) return false;
        ScreenRenderData data = SCREEN_DATA.get(acs);
        if (data == null) return false;

        ScreenBounds frame = frameBounds(acs);
        ScreenPanelAdapter modalToDispatch = null;

        // First pass — find any visible modal and check if click is inside.
        boolean anyModal = false;
        for (ScreenPanelAdapter adapter : data.menuMatches) {
            Panel panel = adapter.getPanel();
            if (!panel.isVisible() || !panel.cancelsUnhandledClicks()) continue;
            anyModal = true;
            var origin = adapter.getOrigin(frame, acs);
            if (origin.isEmpty()) continue;
            int padding = adapter.getPadding();
            int pw = panel.getWidth() + 2 * padding;
            int ph = panel.getHeight() + 2 * padding;
            int x = origin.get().x();
            int y = origin.get().y();
            if (mouseX >= x && mouseX < x + pw
                    && mouseY >= y && mouseY < y + ph) {
                modalToDispatch = adapter;
                break;
            }
        }

        if (!anyModal) return false; // No modal — let vanilla dispatch.

        if (modalToDispatch != null) {
            // Click inside modal bounds — dispatch to its element layer
            // so buttons get the click. The dispatch happens at the mixin
            // entry point (HEAD of the most-derived screen mouseClicked),
            // which is BEFORE any vanilla click handling (slot pickup,
            // tab switch, etc.). After dispatch, eat — the vanilla chain
            // doesn't see this click.
            modalToDispatch.mouseClicked(frame, mouseX, mouseY, button, acs);
        }

        // Modal up + click outside any modal: also eat (modal blocks
        // underlying interaction). Modal up + click inside: ate after
        // dispatch above. Either way, return true to cancel the screen's
        // mouseClicked chain.
        return true;
    }

    /**
     * Phase 14d-2 modal-aware scroll dispatch helper. Returns the matching
     * adapter whose modal panel contains the cursor, or {@code null} if no
     * modal is visible OR the cursor is outside every visible modal's
     * bounds.
     *
     * <p>Used by {@code MenuKitModalMouseHandlerMixin.onScroll} (paralleling
     * the click dispatch in {@link #dispatchModalClick}): when modal up,
     * scroll inside modal bounds dispatches to the modal's adapter (so a
     * ScrollContainer inside a modal dialog can receive scroll input);
     * scroll outside is eaten.
     *
     * <p>Also used by ScrollContainer-inside-non-modal scenarios — the
     * Fabric {@code allowMouseScroll} hook in {@link #onScreenInit} routes
     * scrolls to non-modal adapters via {@link ScreenPanelAdapter#mouseScrolled}.
     *
     * @return the modal adapter whose panel contains the cursor, or {@code null}
     */
    public static ScreenPanelAdapter findModalAtPoint(Screen screen,
                                                      double mouseX, double mouseY) {
        if (!(screen instanceof AbstractContainerScreen<?> acs)) return null;
        ScreenRenderData data = SCREEN_DATA.get(acs);
        if (data == null) return null;
        ScreenBounds frame = frameBounds(acs);
        for (ScreenPanelAdapter adapter : data.menuMatches) {
            Panel panel = adapter.getPanel();
            if (!panel.isVisible() || !panel.cancelsUnhandledClicks()) continue;
            var origin = adapter.getOrigin(frame, acs);
            if (origin.isEmpty()) continue;
            int padding = adapter.getPadding();
            int pw = panel.getWidth() + 2 * padding;
            int ph = panel.getHeight() + 2 * padding;
            int x = origin.get().x();
            int y = origin.get().y();
            if (mouseX >= x && mouseX < x + pw
                    && mouseY >= y && mouseY < y + ph) {
                return adapter;
            }
        }
        return null;
    }

    /**
     * Phase 14d-2 modal-aware scroll dispatch — used by
     * {@code MenuKitModalMouseHandlerMixin.onScroll} when modal is up. If
     * cursor is inside a modal, dispatch the scroll to that modal's
     * elements (so a ScrollContainer inside the modal scrolls); return
     * true. If cursor is outside every modal, return true to signal
     * "modal is up, scroll outside should be eaten" — caller cancels.
     * If no modal visible, returns false — caller passes scroll through
     * to normal dispatch.
     */
    public static boolean dispatchModalScroll(Screen screen,
                                              double mouseX, double mouseY,
                                              double scrollX, double scrollY) {
        if (!(screen instanceof AbstractContainerScreen<?> acs)) return false;
        if (!hasVisibleModalOnScreen(acs)) return false;
        ScreenPanelAdapter target = findModalAtPoint(screen, mouseX, mouseY);
        if (target != null) {
            // Cursor inside a modal — dispatch scroll to its elements.
            ScreenBounds frame = frameBounds(acs);
            target.mouseScrolled(frame, mouseX, mouseY, scrollX, scrollY, acs);
        }
        // Whether dispatched or not, return true to signal "eat" — modal
        // is up; vanilla scroll dispatch shouldn't reach the underlying
        // screen.
        return true;
    }


    /**
     * Phase 14d-1 modal tooltip suppression — query for "is any modal
     * panel visible on the current screen?" Called from
     * {@code MenuKitTooltipSuppressMixin} which mixins
     * {@code GuiGraphics.setTooltipForNextFrameInternal} at HEAD-cancellable
     * to suppress tooltip queueing while a modal is up.
     *
     * <p>Round-2 implementation finding (post-smoke): the original
     * accessor-based queue-clear approach was insufficient because
     * {@code CreativeModeInventoryScreen.render} queues tab-hover tooltips
     * AFTER {@code super.render()} returns (and thus after our render
     * path's clear). The {@code deferredTooltip} field is a single
     * last-write-wins Runnable; subsequent setTooltipForNextFrame calls
     * after our clear simply re-queue. Suppressing at the queueing site
     * is the robust mechanism. Same architectural shape as click-eat:
     * library-wide HEAD-cancellable mixin gated on per-Panel modal flag.
     */
    public static boolean hasVisibleModalOnScreen(AbstractContainerScreen<?> screen) {
        ScreenRenderData data = SCREEN_DATA.get(screen);
        if (data == null) return false;
        for (ScreenPanelAdapter adapter : data.menuMatches) {
            Panel panel = adapter.getPanel();
            if (panel.isVisible() && panel.cancelsUnhandledClicks()) return true;
        }
        return false;
    }

    /**
     * Phase 14d-1 modal tooltip suppression — query for "is any modal
     * panel visible on the currently-active screen?" Tooltip suppression
     * mixin doesn't have a screen reference at its inject point; it
     * checks {@code Minecraft.getInstance().screen} via this helper.
     */
    public static boolean hasAnyVisibleModal() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null) return false;
        var screen = mc.screen;
        if (!(screen instanceof AbstractContainerScreen<?> acs)) return false;
        return hasVisibleModalOnScreen(acs);
    }

    /**
     * Computes the bounding rectangle enclosing the given slots in screen
     * space. Slots store {@code x}/{@code y} relative to the screen frame;
     * the returned bounds are absolute (includes {@code leftPos}/{@code topPos}).
     * Standard slot visual is 16×16.
     */
    private static SlotGroupBounds computeSlotGroupBounds(List<Slot> slots,
                                                           AbstractContainerScreen<?> screen) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (Slot slot : slots) {
            int sx = slot.x;
            int sy = slot.y;
            if (sx < minX) minX = sx;
            if (sy < minY) minY = sy;
            if (sx + 16 > maxX) maxX = sx + 16;
            if (sy + 16 > maxY) maxY = sy + 16;
        }
        AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
        int frameLeft = acc.trevorMod$getLeftPos();
        int frameTop = acc.trevorMod$getTopPos();
        return new SlotGroupBounds(
                frameLeft + minX,
                frameTop + minY,
                maxX - minX,
                maxY - minY);
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
        Set<ScreenPanelAdapter> pendingMenu = pendingSnapshot();
        Set<SlotGroupPanelAdapter> pendingSlotGroup = pendingSlotGroupSnapshot();
        if (pendingMenu.isEmpty() && pendingSlotGroup.isEmpty()) return;

        StringBuilder msg = new StringBuilder("MenuKit: ");
        if (!pendingMenu.isEmpty()) {
            msg.append(pendingMenu.size())
               .append(" region-based ScreenPanelAdapter(s) constructed but never " +
                       "declared targeting (.on / .onAny). Panel IDs: ");
            boolean first = true;
            for (ScreenPanelAdapter adapter : pendingMenu) {
                if (!first) msg.append(", ");
                msg.append(adapter.getPanel().getId());
                first = false;
            }
        }
        if (!pendingSlotGroup.isEmpty()) {
            if (!pendingMenu.isEmpty()) msg.append("; ");
            msg.append(pendingSlotGroup.size())
               .append(" SlotGroupPanelAdapter(s) constructed but never declared " +
                       "targeting (.on(SlotGroupCategory...)). Panel IDs: ");
            boolean first = true;
            for (SlotGroupPanelAdapter adapter : pendingSlotGroup) {
                if (!first) msg.append(", ");
                msg.append(adapter.getPanel().getId());
                first = false;
            }
        }
        msg.append(". Fix by adding the missing .on(...) call(s).");
        String message = msg.toString();
        LOGGER.error("[ScreenPanelRegistry] {}", message);
        throw new IllegalStateException(message);
    }
}
