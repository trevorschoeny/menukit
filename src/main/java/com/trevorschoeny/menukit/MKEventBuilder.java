package com.trevorschoeny.menukit;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

/**
 * Builder-chain API for registering event handlers with zero boilerplate.
 *
 * <p>Created via {@link MenuKit#on(MKEvent.Type...)} and terminated by
 * calling {@link #handler(Function)}. Between those two calls, optional filter
 * methods narrow which events reach the handler.
 *
 * <p><b>Usage example:</b>
 * <pre>{@code
 * // Cancel all left-clicks on slots in the "storage" region
 * MenuKit.on(MKEvent.Type.LEFT_CLICK)
 *     .region("storage")
 *     .handler(event -> {
 *         System.out.println("Blocked click on " + ((MKSlotEvent) event).getSlotStack());
 *         return MKEventResult.CONSUMED;
 *     });
 *
 * // Listen for button clicks using the typed convenience method
 * MenuKit.on(MKEvent.Type.BUTTON_CLICK)
 *     .panel("settings")
 *     .uiHandler(event -> {
 *         System.out.println("Button clicked: " + event.getButton());
 *         return MKEventResult.PASS;
 *     });
 * }</pre>
 *
 * <p>Part of the <b>MenuKit</b> event system.
 */
public class MKEventBuilder {

    // ── Filter State ──────────────────────────────────────────────────────────
    //
    // Accumulated by chained method calls, then frozen into an MKEventListener
    // when handler() is called. Each filter is optional — null/false means
    // "don't filter on this criterion".

    private final Set<MKEvent.Type> types;
    private @Nullable String regionFilter;
    private @Nullable String panelFilter;
    private @Nullable String elementFilter;
    private boolean playerInventoryFilter;
    private @Nullable Set<MKContext> contextFilter;
    private @Nullable Predicate<MKEvent> wherePredicate;
    private int priority;
    private MKEventPhase phase = MKEventPhase.BEFORE; // default for backward compat
    private @Nullable String sessionId; // null = global (lives forever)

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Package-private constructor — consumers create builders via
     * {@link MenuKit#on(MKEvent.Type...)}.
     *
     * @param types the event types this handler should receive
     */
    MKEventBuilder(MKEvent.Type... types) {
        // EnumSet for O(1) contains checks during dispatch
        this.types = EnumSet.noneOf(MKEvent.Type.class);
        for (MKEvent.Type type : types) {
            this.types.add(type);
        }
    }

    // ── Filter Methods (each returns this for chaining) ───────────────────────

    /**
     * Only fire for events on slots belonging to the named region.
     * Only applies to {@link MKSlotEvent}s -- UI events skip this filter.
     *
     * @param name the region name to match
     * @return this builder for chaining
     */
    public MKEventBuilder region(String name) {
        this.regionFilter = name;
        return this;
    }

    /**
     * Only fire for events on slots belonging to the named panel,
     * or UI events associated with the named panel.
     *
     * @param name the panel name to match
     * @return this builder for chaining
     */
    public MKEventBuilder panel(String name) {
        this.panelFilter = name;
        return this;
    }

    /**
     * Only fire for UI events matching the given element ID.
     * Only applies to {@link MKUIEvent}s -- slot events skip this filter.
     *
     * @param elementId the element ID to match
     * @return this builder for chaining
     */
    public MKEventBuilder element(String elementId) {
        this.elementFilter = elementId;
        return this;
    }

    /**
     * Only fire for events on player inventory slots.
     * Shorthand for {@code .where(e -> e instanceof MKSlotEvent se && se.isPlayerInventorySlot())},
     * but implemented as a dedicated boolean flag for dispatch performance.
     *
     * @return this builder for chaining
     */
    public MKEventBuilder playerInventory() {
        this.playerInventoryFilter = true;
        return this;
    }

    /**
     * Only fire when the event's screen context is one of the given contexts.
     * Multiple calls overwrite the previous context filter.
     *
     * @param contexts one or more contexts to match
     * @return this builder for chaining
     */
    public MKEventBuilder context(MKContext... contexts) {
        // EnumSet for O(1) contains checks during dispatch
        this.contextFilter = EnumSet.noneOf(MKContext.class);
        for (MKContext ctx : contexts) {
            this.contextFilter.add(ctx);
        }
        return this;
    }

    /**
     * Adds a custom predicate filter. The handler only fires if the
     * predicate returns true. This is checked LAST during dispatch
     * (after all built-in filters) since it may involve arbitrary logic.
     *
     * @param predicate custom filter condition
     * @return this builder for chaining
     */
    public MKEventBuilder where(Predicate<MKEvent> predicate) {
        this.wherePredicate = predicate;
        return this;
    }

    /**
     * Sets the priority for this handler. Higher values fire first.
     * Default is 0. Negative values fire after default-priority handlers.
     *
     * @param priority ordering value (higher = earlier)
     * @return this builder for chaining
     */
    public MKEventBuilder priority(int priority) {
        this.priority = priority;
        return this;
    }

    // ── Phase Selection ─────────────────────────────────────────────────────

    /**
     * Sets this listener to the ALLOW phase — it can cancel the event.
     * If the handler returns CONSUMED, the event stops entirely and
     * BEFORE/AFTER phases never fire.
     *
     * @return this builder for chaining
     */
    public MKEventBuilder allow() {
        this.phase = MKEventPhase.ALLOW;
        return this;
    }

    /**
     * Sets this listener to the BEFORE phase (default).
     * Fires after all ALLOW listeners have permitted the event.
     * Returning CONSUMED stops the BEFORE chain but AFTER still fires.
     *
     * @return this builder for chaining
     */
    public MKEventBuilder before() {
        this.phase = MKEventPhase.BEFORE;
        return this;
    }

    /**
     * Sets this listener to the AFTER phase.
     * Fires after the event has been fully processed.
     * Return value is ignored — cannot cancel or stop the chain.
     *
     * @return this builder for chaining
     */
    public MKEventBuilder after() {
        this.phase = MKEventPhase.AFTER;
        return this;
    }

    // ── Session Scoping ──────────────────────────────────────────────────────

    /**
     * Scopes this listener to the current screen session.
     * The listener is automatically removed when the screen closes.
     *
     * <p>If no screen session is active, the listener is registered globally
     * as a fallback (with a warning logged).
     *
     * @return this builder for chaining
     */
    public MKEventBuilder screenSession() {
        String session = MKEventBus.getCurrentScreenSession();
        if (session != null) {
            this.sessionId = session;
        } else {
            MenuKit.LOGGER.warn("[MenuKit] screenSession() called with no active screen session — listener will be global");
        }
        return this;
    }

    /**
     * Scopes this listener to the current world session.
     * The listener is automatically removed when the player leaves the world.
     *
     * <p>If no world session is active, the listener is registered globally
     * as a fallback (with a warning logged).
     *
     * @return this builder for chaining
     */
    public MKEventBuilder worldSession() {
        String session = MKEventBus.getCurrentWorldSession();
        if (session != null) {
            this.sessionId = session;
        } else {
            MenuKit.LOGGER.warn("[MenuKit] worldSession() called with no active world session — listener will be global");
        }
        return this;
    }

    // ── Terminal Methods ──────────────────────────────────────────────────────

    /**
     * Registers the handler and returns the listener handle.
     *
     * <p>This is the terminal method -- calling it freezes all filter state
     * into an {@link MKEventBus.MKEventListener}, registers it in the bus,
     * and returns the listener so it can be unregistered later if needed.
     *
     * <p>The handler receives the base {@link MKEvent} type. For type-safe
     * access to slot or UI event fields, use {@link #slotHandler} or
     * {@link #uiHandler} instead.
     *
     * @param handler function that receives the event and returns CONSUMED or PASS
     * @return the registered listener (retain this to unregister later)
     */
    public MKEventBus.MKEventListener handler(Function<MKEvent, MKEventResult> handler) {
        // Freeze the builder state into an immutable listener
        MKEventBus.MKEventListener listener = new MKEventBus.MKEventListener(
                types,
                regionFilter,
                panelFilter,
                elementFilter,
                playerInventoryFilter,
                contextFilter,
                wherePredicate,
                priority,
                handler,
                phase,
                sessionId
        );

        // Register in the bus (thread-safe, priority-sorted)
        MKEventBus.register(listener);

        return listener;
    }

    /**
     * Convenience terminal method for handlers that only care about slot events.
     *
     * <p>Wraps the given handler: if the event is not an {@link MKSlotEvent},
     * returns {@link MKEventResult#PASS} automatically. Otherwise casts and
     * delegates to the typed handler.
     *
     * @param handler function that receives a typed MKSlotEvent
     * @return the registered listener
     */
    public MKEventBus.MKEventListener slotHandler(Function<MKSlotEvent, MKEventResult> handler) {
        return handler(event -> {
            if (event instanceof MKSlotEvent slotEvent) {
                return handler.apply(slotEvent);
            }
            return MKEventResult.PASS;
        });
    }

    /**
     * Convenience terminal method for handlers that only care about UI events.
     *
     * <p>Wraps the given handler: if the event is not an {@link MKUIEvent},
     * returns {@link MKEventResult#PASS} automatically. Otherwise casts and
     * delegates to the typed handler.
     *
     * @param handler function that receives a typed MKUIEvent
     * @return the registered listener
     */
    public MKEventBus.MKEventListener uiHandler(Function<MKUIEvent, MKEventResult> handler) {
        return handler(event -> {
            if (event instanceof MKUIEvent uiEvent) {
                return handler.apply(uiEvent);
            }
            return MKEventResult.PASS;
        });
    }
}
