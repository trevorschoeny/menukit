package com.trevorschoeny.menukit;

import java.util.List;

/**
 * Definition-time record for a region group. Registered at mod init,
 * resolved into {@link MKRegionGroup} instances at menu construction time.
 *
 * <p>A region group treats multiple regions as one logical unit.
 * For example, "player_storage" groups mk:hotbar + mk:main_inventory
 * so callers can query total item counts, empty slots, etc. across both.
 *
 * <p>Members are ordered by priority (lower = fill first). This matters
 * for operations like sorting across a group: items fill low-priority
 * regions before high-priority ones.
 *
 * <p>Part of the <b>MenuKit</b> framework.
 */
public record MKRegionGroupDef(String name, List<MemberDef> members) {

    /**
     * A single member of a region group.
     *
     * @param regionName the region name (e.g., "mk:hotbar")
     * @param priority   fill priority — lower values fill first
     */
    public record MemberDef(String regionName, int priority) {}
}
