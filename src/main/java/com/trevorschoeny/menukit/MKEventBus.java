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
     * Dispatches an event to all matching listeners in priority order.
     *
     * <p>Filters are checked in order of cost (cheapest first):
     * <ol>
     *   <li>Event type (EnumSet.contains -- O(1) bitwise)</li>
     *   <li>Player inventory flag (boolean compare, slot events only)</li>
     *   <li>Region name (string equality, slot events only)</li>
     *   <li>Panel name (string equality, works for both slot and UI events)</li>
     *   <li>Element ID (string equality, UI events only)</li>
     *   <li>Context set (EnumSet.contains)</li>
     *   <li>Where predicate (arbitrary user code -- last)</li>
     * </ol>
     *
     * @param event the event to dispatch
     * @return true if any handler returned {@link MKEventResult#CONSUMED}
     */
    public static boolean fire(MKEvent event) {
        // Snapshot iteration — CopyOnWriteArrayList guarantees a consistent
        // view even if registration happens concurrently (shouldn't during
        // gameplay, but safety first).
        MKEvent.Type eventType = event.getType();

        // Check once whether this is a slot event (avoids repeated instanceof)
        MKSlotEvent slotEvent = event instanceof MKSlotEvent se ? se : null;
        MKUIEvent uiEvent = event instanceof MKUIEvent ue ? ue : null;

        for (int i = 0, size = listeners.size(); i < size; i++) {
            MKEventListener listener = listeners.get(i);

            // 1. Type filter — cheapest check (EnumSet.contains is a bitwise op)
            if (!listener.types.contains(eventType)) continue;

            // 2. Player inventory filter — only applies to slot events
            if (listener.playerInventoryFilter) {
                if (slotEvent == null || !slotEvent.isPlayerInventorySlot()) continue;
            }

            // 3. Region name filter — only applies to slot events
            if (listener.regionFilter != null) {
                if (slotEvent == null) continue;
                MKRegion region = slotEvent.getRegion();
                if (region == null || !listener.regionFilter.equals(region.name())) continue;
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
                if (panel == null || !listener.panelFilter.equals(panel)) continue;
            }

            // 5. Element ID filter — only applies to UI events
            if (listener.elementFilter != null) {
                if (uiEvent == null) continue;
                String elemId = uiEvent.getElementId();
                if (elemId == null || !listener.elementFilter.equals(elemId)) continue;
            }

            // 6. Context filter — EnumSet.contains
            if (listener.contextFilter != null) {
                if (!listener.contextFilter.contains(event.getContext())) continue;
            }

            // 7. Where predicate — arbitrary user code, checked last
            if (listener.wherePredicate != null) {
                if (!listener.wherePredicate.test(event)) continue;
            }

            // All filters passed — invoke the handler
            MKEventResult result = listener.handler.apply(event);

            // CONSUMED stops the chain and tells the caller to cancel vanilla behavior
            if (result == MKEventResult.CONSUMED) {
                return true;
            }

            // PASS continues to the next listener
        }

        // All listeners passed (or none matched) — vanilla behavior proceeds
        return false;
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    /**
     * Removes all registered listeners. Intended for testing and hot-reload.
     */
    public static void clear() {
        listeners.clear();
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

        MKEventListener(Set<MKEvent.Type> types,
                         @Nullable String regionFilter,
                         @Nullable String panelFilter,
                         @Nullable String elementFilter,
                         boolean playerInventoryFilter,
                         @Nullable Set<MKContext> contextFilter,
                         @Nullable Predicate<MKEvent> wherePredicate,
                         int priority,
                         Function<MKEvent, MKEventResult> handler) {
            this.types = types;
            this.regionFilter = regionFilter;
            this.panelFilter = panelFilter;
            this.elementFilter = elementFilter;
            this.playerInventoryFilter = playerInventoryFilter;
            this.contextFilter = contextFilter;
            this.wherePredicate = wherePredicate;
            this.priority = priority;
            this.handler = handler;
        }
    }
}
