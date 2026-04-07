package com.trevorschoeny.menukit.panel;

import com.trevorschoeny.menukit.MenuKit;

import com.trevorschoeny.menukit.container.MKContainerType;
import com.trevorschoeny.menukit.region.MKRegion;
import com.trevorschoeny.menukit.region.MKRegionGroup;
import com.trevorschoeny.menukit.region.MKRegionRegistry;

import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jspecify.annotations.Nullable;

/**
 * Context passed to {@link MKConditionalRule.ElementFactory#create} when a
 * conditional rule's predicate matches an element in the panel tree.
 *
 * <p>Provides information about the matched element and its surrounding
 * context so factories can make informed decisions about what to create.
 *
 * <p>Part of the <b>MenuKit</b> conditional element system.
 */
public record MKConditionalContext(
        /** ID of the element that triggered the rule. */
        String matchedElementId,
        /** The element itself. */
        MKGroupChild matchedElement,
        /** Which panel this is in. */
        String panelName,
        /** If the matched element is a SlotGroup, its associated region name. */
        @Nullable String regionName,
        /** If the matched element is a SlotGroup, its container type. */
        @Nullable MKContainerType containerType
) {

    // ── Lazy Lookup Helpers ─────────────────────────────────────────────────
    // These provide runtime context that would be expensive to compute eagerly.

    /**
     * Looks up the MKRegion for the matched element's region name, if any.
     * Returns null if no region name is set or the region isn't registered.
     *
     * @param menu the current container menu
     * @return the region, or null
     */
    public @Nullable MKRegion getRegion(AbstractContainerMenu menu) {
        if (regionName == null) return null;
        return MKRegionRegistry.getRegion(menu, regionName);
    }

    /**
     * Finds the owning MKRegionGroup for the matched element, if any.
     * Searches all region groups registered for the given menu.
     *
     * @param menu the current container menu
     * @return the owning group, or null
     */
    public @Nullable MKRegionGroup findOwningGroup(AbstractContainerMenu menu) {
        if (regionName == null) return null;
        var groups = MKRegionRegistry.getGroupsForRegion(menu, regionName);
        return groups.isEmpty() ? null : groups.get(0);
    }
}
