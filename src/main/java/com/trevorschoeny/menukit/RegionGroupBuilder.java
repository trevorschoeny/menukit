package com.trevorschoeny.menukit;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for {@link MKRegionGroupDef}. Creates a group definition
 * that treats multiple regions as one logical unit.
 *
 * <p>Usage:
 * <pre>{@code
 * MenuKit.regionGroup("player_storage")
 *     .region("mk:hotbar", 1)        // priority 1 = fill first
 *     .region("mk:main_inventory", 2)
 *     .register();
 * }</pre>
 *
 * <p>Priority can be omitted — it auto-increments from 1:
 * <pre>{@code
 * MenuKit.regionGroup("player_all")
 *     .region("mk:hotbar")           // auto priority 1
 *     .region("mk:main_inventory")   // auto priority 2
 *     .region("mk:armor")            // auto priority 3
 *     .region("mk:offhand")          // auto priority 4
 *     .register();
 * }</pre>
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public class RegionGroupBuilder {

    private final String name;
    private final List<MKRegionGroupDef.MemberDef> members = new ArrayList<>();
    private final List<MKTransferRoute> routes = new ArrayList<>();
    private int nextPriority = 1;  // auto-incrementing priority counter

    RegionGroupBuilder(String name) {
        this.name = name;
    }

    /**
     * Adds a region with explicit fill priority.
     * Lower priority values fill first during sort/distribute operations.
     *
     * @param regionName the region name (e.g., "mk:hotbar")
     * @param priority   fill priority (1 = highest)
     * @return this builder
     */
    public RegionGroupBuilder region(String regionName, int priority) {
        members.add(new MKRegionGroupDef.MemberDef(regionName, priority));
        // Keep auto-priority ahead of the highest explicit priority seen
        if (priority >= nextPriority) {
            nextPriority = priority + 1;
        }
        return this;
    }

    /**
     * Adds a region with auto-incrementing fill priority.
     * Each call gets the next priority value (starting from 1).
     *
     * @param regionName the region name (e.g., "mk:hotbar")
     * @return this builder
     */
    public RegionGroupBuilder region(String regionName) {
        return region(regionName, nextPriority);
    }

    /**
     * Declares a one-way transfer route: items in {@code from} can transfer
     * to {@code to} when a consumer module requests transfer targets.
     *
     * <p>Use {@link #transferPair} for bidirectional routes.
     *
     * @param from source region name (e.g., "mk:hotbar")
     * @param to   target region name (e.g., "mk:chest")
     * @return this builder
     */
    public RegionGroupBuilder transferRoute(String from, String to) {
        routes.add(new MKTransferRoute(from, to));
        return this;
    }

    /**
     * Declares a bidirectional transfer pair: items can transfer in either
     * direction between the two regions.
     *
     * @param a first region name
     * @param b second region name
     * @return this builder
     */
    public RegionGroupBuilder transferPair(String a, String b) {
        routes.add(new MKTransferRoute(a, b));
        routes.add(new MKTransferRoute(b, a));
        return this;
    }

    /**
     * Registers the group definition with MenuKit.
     * The definition is resolved into live {@link MKRegionGroup} instances
     * at menu construction time.
     */
    public void register() {
        MKRegionGroupDef def = new MKRegionGroupDef(name, List.copyOf(members),
                List.copyOf(routes));
        MenuKit.registerRegionGroup(def);
    }
}
