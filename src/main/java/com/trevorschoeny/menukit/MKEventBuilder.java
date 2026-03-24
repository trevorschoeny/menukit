package com.trevorschoeny.menukit;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

/**
 * Builder-chain API for registering event handlers with zero boilerplate.
 *
 * <p>Created via {@link MenuKit#on(MKSlotEvent.Type...)} and terminated by
 * calling {@link #handler(Function)}. Between those two calls, optional filter
 * methods narrow which events reach the handler.
 *
 * <p><b>Usage example:</b>
 * <pre>{@code
 * // Cancel all left-clicks on slots in the "storage" region
 * MenuKit.on(MKSlotEvent.Type.LEFT_CLICK)
 *     .region("storage")
 *     .handler(event -> {
 *         System.out.println("Blocked click on " + event.getSlotStack());
 *         return MKEventResult.CONSUMED;
 *     });
 *
 * // Log all shift-clicks in player inventory, low priority
 * MenuKit.on(MKSlotEvent.Type.SHIFT_CLICK)
 *     .playerInventory()
 *     .priority(-10)
 *     .handler(event -> {
 *         System.out.println("Player shift-clicked slot " + event.getContainerSlot());
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

    private final Set<MKSlotEvent.Type> types;
    private @Nullable String regionFilter;
    private @Nullable String panelFilter;
    private boolean playerInventoryFilter;
    private @Nullable Set<MKContext> contextFilter;
    private @Nullable Predicate<MKSlotEvent> wherePredicate;
    private int priority;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Package-private constructor — consumers create builders via
     * {@link MenuKit#on(MKSlotEvent.Type...)}.
     *
     * @param types the event types this handler should receive
     */
    MKEventBuilder(MKSlotEvent.Type... types) {
        // EnumSet for O(1) contains checks during dispatch
        this.types = EnumSet.noneOf(MKSlotEvent.Type.class);
        for (MKSlotEvent.Type type : types) {
            this.types.add(type);
        }
    }

    // ── Filter Methods (each returns this for chaining) ───────────────────────

    /**
     * Only fire for events on slots belonging to the named region.
     *
     * @param name the region name to match
     * @return this builder for chaining
     */
    public MKEventBuilder region(String name) {
        this.regionFilter = name;
        return this;
    }

    /**
     * Only fire for events on slots belonging to the named panel.
     *
     * @param name the panel name to match
     * @return this builder for chaining
     */
    public MKEventBuilder panel(String name) {
        this.panelFilter = name;
        return this;
    }

    /**
     * Only fire for events on player inventory slots.
     * Shorthand for {@code .where(MKSlotEvent::isPlayerInventorySlot)},
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
    public MKEventBuilder where(Predicate<MKSlotEvent> predicate) {
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

    // ── Terminal Method ───────────────────────────────────────────────────────

    /**
     * Registers the handler and returns the listener handle.
     *
     * <p>This is the terminal method — calling it freezes all filter state
     * into an {@link MKEventBus.MKEventListener}, registers it in the bus,
     * and returns the listener so it can be unregistered later if needed.
     *
     * @param handler function that receives the event and returns CONSUMED or PASS
     * @return the registered listener (retain this to unregister later)
     */
    public MKEventBus.MKEventListener handler(Function<MKSlotEvent, MKEventResult> handler) {
        // Freeze the builder state into an immutable listener
        MKEventBus.MKEventListener listener = new MKEventBus.MKEventListener(
                types,
                regionFilter,
                panelFilter,
                playerInventoryFilter,
                contextFilter,
                wherePredicate,
                priority,
                handler
        );

        // Register in the bus (thread-safe, priority-sorted)
        MKEventBus.register(listener);

        return listener;
    }
}
