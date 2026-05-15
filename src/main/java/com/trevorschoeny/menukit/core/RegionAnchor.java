package com.trevorschoeny.menukit.core;

/**
 * A region plus an explicit stacking priority. Used when a consumer wants
 * predictable ordering for sibling panels that share a region.
 *
 * <p>Consumers don't typically construct this directly — chain
 * {@code MenuRegion.RIGHT_ALIGN_TOP.priority(50)} (or the equivalent
 * {@link HudRegion#priority(int)}) and pass the result anywhere a region
 * is accepted. Adapter / builder overloads pick up the priority and pass
 * it through to the registry.
 *
 * <h3>Ordering semantics</h3>
 *
 * Within a region, siblings render in ascending order of priority — lower
 * numbers render first (closer to the region's anchor edge). The default
 * priority is {@link #DEFAULT_PRIORITY} (100), chosen as a middle value so
 * consumers can shift up (lower number) or down (higher number) without
 * recalculating everyone else's numbers.
 *
 * <p>When two siblings share a priority, the tiebreaker is alphabetical
 * by the registering mod's modId (captured automatically at registration
 * time via {@code FabricLoader}). The result is fully deterministic
 * across launches — mod-load order is no longer the de-facto sort key.
 *
 * <h3>Library-not-platform alignment (§0019)</h3>
 *
 * The default-priority path (consumers pass {@code MenuRegion.X} directly)
 * gets deterministic ordering for free via the modId tiebreaker — no API
 * change is required of consumers who don't care about explicit ordering.
 * The {@code priority(int)} chainable exists only for the case where a
 * consumer DOES want explicit ordering; library-not-platform says the
 * consumer asks only when they care.
 *
 * @param <R>      the region enum type ({@link MenuRegion} or {@link HudRegion})
 * @param region   the region itself
 * @param priority the stacking priority within that region (lower = first)
 */
public record RegionAnchor<R>(R region, int priority) {

    /**
     * Default priority for region-anchored panels. Middle-of-the-range
     * value so consumers can shift up (lower number) or down (higher
     * number) without recalculating others.
     */
    public static final int DEFAULT_PRIORITY = 100;
}
