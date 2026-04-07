package com.trevorschoeny.menukit;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Central dispatch engine for MenuKit events.
 *
 * <p>Listeners are registered globally (typically during mod init) and fire
 * whenever a matching {@link MKEvent} is dispatched. Registration is
 * thread-safe (mods register from different threads during init).
 *
 * <p><b>Threading:</b> Click, hover, and keyboard events dispatch on the
 * client render thread. Transfer events (ITEM_TRANSFER_IN/OUT) and state
 * change events (SLOT_CHANGED/EMPTIED/FILLED) dispatch on the server tick
 * thread. Lifecycle events (MENU_OPEN/CLOSE) dispatch on whichever thread
 * constructs/removes the menu. UI events (BUTTON_CLICK, PANEL_SHOW, etc.)
 * dispatch on the client render thread. Handlers that need client-only
 * behavior should check {@code player.level().isClientSide()}.
 *
 * <p>Listeners are sorted by priority (descending -- higher priority fires first)
 * at registration time so that {@link #fire(MKEvent)} has zero overhead from
 * sorting. The fire path is allocation-free and optimized for the hot path
 * (called on every click).
 *
 * <p>Part of the <b>MenuKit</b> event system.
 */
public final class MKEventBus {

    // ── Listener Storage ──────────────────────────────────────────────────────
    //
    // CopyOnWriteArrayList gives us thread-safe registration (rare, during init)
    // with zero-cost iteration during dispatch (frequent, every click).
    // Listeners are kept sorted by priority descending — highest priority first.

    private static final CopyOnWriteArrayList<MKEventListener> listeners = new CopyOnWriteArrayList<>();

    // Private constructor — all methods are static
    private MKEventBus() {}

    // ── Registration ──────────────────────────────────────────────────────────

    /**
     * Registers a listener and inserts it in priority order (descending).
     * Thread-safe — can be called from any thread during mod init.
     *
     * @param listener the listener to register
     */
    static void register(MKEventListener listener) {
        // CopyOnWriteArrayList: add + sort is safe because writes are serialized
        // internally. We synchronize the add+sort pair to keep the list consistent
        // if multiple mods register concurrently.
        synchronized (listeners) {
            listeners.add(listener);
            // Sort descending by priority — higher priority fires first.
            // This is O(n log n) but only happens during registration (rare).
            listeners.sort((a, b) -> Integer.compare(b.priority, a.priority));
        }
    }

    /**
     * Removes a previously registered listener.
     *
     * @param listener the listener to remove
     * @return true if the listener was found and removed
     */
    public static boolean unregister(MKEventListener listener) {
        return listeners.remove(listener);
    }

    // ── Dispatch ──────────────────────────────────────────────────────────────
    //
    // This is the HOT PATH — called on every slot click. Performance rules:
    //   1. No allocations in the loop
    //   2. Cheapest checks first (type set contains)
    //   3. Short-circuit on CONSUMED
    //   4. Iterate the pre-sorted list directly (no copy, no stream)

    /**
     * Dispatches an event to all matching listeners using three-phase dispatch:
     * ALLOW → BEFORE → AFTER.
     *
     * <p><b>ALLOW phase:</b> If any ALLOW listener returns CONSUMED, the event
     * is cancelled — BEFORE and AFTER phases never fire, and true is returned.
     *
     * <p><b>BEFORE phase:</b> Notification only. All matching BEFORE listeners
     * fire. Return values are ignored (cannot cancel at this point).
     *
     * <p><b>AFTER phase:</b> Notification only. All matching AFTER listeners
     * fire. Return values are ignored.
     *
     * <p>Within each phase, listeners fire in priority order (highest first).
     * Listeners default to BEFORE phase for backward compatibility.
     *
     * <p>Filters are checked in order of cost (cheapest first):
     * <ol>
     *   <li>Event type (EnumSet.contains -- O(1) bitwise)</li>
     *   <li>Phase (enum equality)</li>
     *   <li>Player inventory flag (boolean compare, slot events only)</li>
     *   <li>Region name (string equality, slot events only)</li>
     *   <li>Panel name (string equality, works for both slot and UI events)</li>
     *   <li>Element ID (string equality, UI events only)</li>
     *   <li>Context set (EnumSet.contains)</li>
     *   <li>Where predicate (arbitrary user code -- last)</li>
     * </ol>
     *
     * @param event the event to dispatch
     * @return true if any ALLOW handler returned {@link MKEventResult#CONSUMED}
     */
    public static boolean fire(MKEvent event) {
        MKEvent.Type eventType = event.getType();

        // Check once whether this is a slot event (avoids repeated instanceof)
        MKSlotEvent slotEvent = event instanceof MKSlotEvent se ? se : null;
        MKUIEvent uiEvent = event instanceof MKUIEvent ue ? ue : null;

        // ── Phase 1: ALLOW ──────────────────────────────────────────────────
        // Any ALLOW listener returning CONSUMED cancels the entire event.
        for (int i = 0, size = listeners.size(); i < size; i++) {
            MKEventListener listener = listeners.get(i);
            if (listener.phase != MKEventPhase.ALLOW) continue;
            if (!matchesFilters(listener, eventType, event, slotEvent, uiEvent)) continue;

            MKEventResult result = listener.handler.apply(event);
            if (result == MKEventResult.CONSUMED) {
                return true; // Event cancelled — BEFORE and AFTER never fire
            }
        }

        // ── Phase 2: BEFORE ─────────────────────────────────────────────────
        // Notification only. All matching listeners fire. Return values checked
        // for CONSUMED to maintain backward compat (existing listeners that
        // return CONSUMED in BEFORE phase still stop the chain within BEFORE
        // and signal cancellation to the caller).
        boolean consumed = false;
        for (int i = 0, size = listeners.size(); i < size; i++) {
            MKEventListener listener = listeners.get(i);
            if (listener.phase != MKEventPhase.BEFORE) continue;
            if (!matchesFilters(listener, eventType, event, slotEvent, uiEvent)) continue;

            MKEventResult result = listener.handler.apply(event);
            if (result == MKEventResult.CONSUMED) {
                consumed = true;
                break; // Stop BEFORE chain, but AFTER still fires
            }
        }

        // ── Phase 3: AFTER ──────────────────────────────────────────────────
        // Notification only. All matching listeners fire regardless of BEFORE result.
        for (int i = 0, size = listeners.size(); i < size; i++) {
            MKEventListener listener = listeners.get(i);
            if (listener.phase != MKEventPhase.AFTER) continue;
            if (!matchesFilters(listener, eventType, event, slotEvent, uiEvent)) continue;

            listener.handler.apply(event); // Return value ignored
        }

        return consumed;
    }

    /**
     * Checks all filter criteria for a listener against an event.
     * Extracted to avoid duplicating filter logic across phases.
     */
    private static boolean matchesFilters(MKEventListener listener,
                                           MKEvent.Type eventType,
                                           MKEvent event,
                                           MKSlotEvent slotEvent,
                                           MKUIEvent uiEvent) {
        // 1. Type filter — cheapest check (EnumSet.contains is a bitwise op)
        if (!listener.types.contains(eventType)) return false;

        // 2. Player inventory filter — only applies to slot events
        if (listener.playerInventoryFilter) {
            if (slotEvent == null || !slotEvent.isPlayerInventorySlot()) return false;
        }

        // 3. Region name filter — only applies to slot events
        if (listener.regionFilter != null) {
            if (slotEvent == null) return false;
            MKRegion region = slotEvent.getRegion();
            if (region == null || !listener.regionFilter.equals(region.name())) return false;
        }

        // 4. Panel name filter — works for both slot and UI events
        if (listener.panelFilter != null) {
            String panel;
            if (slotEvent != null) {
                panel = slotEvent.getPanelName();
            } else if (uiEvent != null) {
                panel = uiEvent.getPanelName();
            } else {
                panel = null;
            }
            if (panel == null || !listener.panelFilter.equals(panel)) return false;
        }

        // 5. Element ID filter — only applies to UI events
        if (listener.elementFilter != null) {
            if (uiEvent == null) return false;
            String elemId = uiEvent.getElementId();
            if (elemId == null || !listener.elementFilter.equals(elemId)) return false;
        }

        // 6. Context filter — EnumSet.contains
        if (listener.contextFilter != null) {
            if (!listener.contextFilter.contains(event.getContext())) return false;
        }

        // 7. Where predicate — arbitrary user code, checked last
        if (listener.wherePredicate != null) {
            if (!listener.wherePredicate.test(event)) return false;
        }

        return true;
    }

    // ── Session Management ─────────────────────────────────────────────────────

    // Active session IDs — tracked so we can validate session references.
    private static final Set<String> activeSessions = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Current screen session ID, or null if no screen is open. */
    private static volatile @Nullable String currentScreenSession = null;

    /** Current world session ID, or null if not in a world. */
    private static volatile @Nullable String currentWorldSession = null;

    /**
     * Starts a new screen session. Called when a container screen opens.
     * Returns the session ID for use with {@link MKEventBuilder#session()}.
     */
    public static String startScreenSession() {
        String id = "screen-" + System.nanoTime();
        activeSessions.add(id);
        currentScreenSession = id;
        return id;
    }

    /**
     * Starts a new world session. Called when the player joins a world.
     * Returns the session ID.
     */
    public static String startWorldSession() {
        String id = "world-" + System.nanoTime();
        activeSessions.add(id);
        currentWorldSession = id;
        return id;
    }

    /** Returns the current screen session ID, or null. */
    public static @Nullable String getCurrentScreenSession() {
        return currentScreenSession;
    }

    /** Returns the current world session ID, or null. */
    public static @Nullable String getCurrentWorldSession() {
        return currentWorldSession;
    }

    /**
     * Ends a session and removes all listeners scoped to it.
     * Called when a screen closes or the player leaves a world.
     */
    public static void endSession(String sessionId) {
        activeSessions.remove(sessionId);
        if (sessionId.equals(currentScreenSession)) {
            currentScreenSession = null;
        }
        if (sessionId.equals(currentWorldSession)) {
            currentWorldSession = null;
        }
        // Remove all listeners belonging to this session
        synchronized (listeners) {
            listeners.removeIf(l -> sessionId.equals(l.sessionId));
        }
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    /**
     * Removes all registered listeners. Intended for testing and hot-reload.
     */
    public static void clear() {
        listeners.clear();
        activeSessions.clear();
        currentScreenSession = null;
        currentWorldSession = null;
    }

    /**
     * Returns the current number of registered listeners. Useful for debugging.
     */
    public static int listenerCount() {
        return listeners.size();
    }

    /**
     * Returns an unmodifiable view of all registered listeners. For debugging only.
     */
    static List<MKEventListener> getListeners() {
        return Collections.unmodifiableList(listeners);
    }

    // ── Listener Record ───────────────────────────────────────────────────────
    //
    // Package-private — consumers never see this directly. They interact with
    // MKEventBuilder, which constructs an MKEventListener and registers it.
    // The listener is returned from handler() so consumers CAN unregister later.

    /**
     * Holds filter criteria and the handler function for a single event listener.
     * Created by {@link MKEventBuilder}, registered in the bus, and optionally
     * retained by the consumer for later unregistration.
     */
    static final class MKEventListener {

        /** Which event types this listener cares about. Never empty. */
        final Set<MKEvent.Type> types;

        /** Region name filter. Null means "match any region". */
        final @Nullable String regionFilter;

        /** Panel name filter. Null means "match any panel". */
        final @Nullable String panelFilter;

        /** Element ID filter. Null means "match any element". */
        final @Nullable String elementFilter;

        /** If true, only fires for player inventory slots. */
        final boolean playerInventoryFilter;

        /** Context filter. Null means "match any context". */
        final @Nullable Set<MKContext> contextFilter;

        /** Custom predicate filter. Null means "no extra filter". */
        final @Nullable Predicate<MKEvent> wherePredicate;

        /** Priority for ordering. Higher = fires first. Default 0. */
        final int priority;

        /** The actual handler function. */
        final Function<MKEvent, MKEventResult> handler;

        /** Dispatch phase: ALLOW, BEFORE, or AFTER. Defaults to BEFORE. */
        final MKEventPhase phase;

        /** Session ID for session-scoped listeners. Null = global (lives forever). */
        final @Nullable String sessionId;

        MKEventListener(Set<MKEvent.Type> types,
                         @Nullable String regionFilter,
                         @Nullable String panelFilter,
                         @Nullable String elementFilter,
                         boolean playerInventoryFilter,
                         @Nullable Set<MKContext> contextFilter,
                         @Nullable Predicate<MKEvent> wherePredicate,
                         int priority,
                         Function<MKEvent, MKEventResult> handler,
                         MKEventPhase phase,
                         @Nullable String sessionId) {
            this.types = types;
            this.regionFilter = regionFilter;
            this.panelFilter = panelFilter;
            this.elementFilter = elementFilter;
            this.playerInventoryFilter = playerInventoryFilter;
            this.contextFilter = contextFilter;
            this.wherePredicate = wherePredicate;
            this.priority = priority;
            this.handler = handler;
            this.phase = phase;
            this.sessionId = sessionId;
        }

        /** Backward-compatible constructor — defaults to BEFORE phase, global scope. */
        MKEventListener(Set<MKEvent.Type> types,
                         @Nullable String regionFilter,
                         @Nullable String panelFilter,
                         @Nullable String elementFilter,
                         boolean playerInventoryFilter,
                         @Nullable Set<MKContext> contextFilter,
                         @Nullable Predicate<MKEvent> wherePredicate,
                         int priority,
                         Function<MKEvent, MKEventResult> handler) {
            this(types, regionFilter, panelFilter, elementFilter,
                 playerInventoryFilter, contextFilter, wherePredicate,
                 priority, handler, MKEventPhase.BEFORE, null);
        }
    }
}
