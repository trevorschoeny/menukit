package com.trevorschoeny.menukit.core;

import java.util.List;

/**
 * Handle for a region of {@link MenuKitSlot} instances grafted onto a vanilla
 * handler via {@link SlotInjector}. Carries the slot index range and the
 * associated Panel — enough for consumers to implement quickMoveStack routing
 * in their handler-specific mixins.
 *
 * <p>Returned by {@link SlotInjector#graft}. Consumers store the handle and
 * query it during shift-click dispatch.
 */
public final class GraftedRegion {

    private final int startIndex;
    private final int endIndex;
    private final Panel panel;
    private final List<SlotGroup> groups;

    GraftedRegion(int startIndex, int endIndex, Panel panel, List<SlotGroup> groups) {
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.panel = panel;
        this.groups = List.copyOf(groups);
    }

    /** Start of the grafted slot range (inclusive) in the handler's slot list. */
    public int startIndex() { return startIndex; }

    /** End of the grafted slot range (exclusive). */
    public int endIndex() { return endIndex; }

    /** The Panel these grafted slots belong to. */
    public Panel panel() { return panel; }

    /** The SlotGroups in this region. */
    public List<SlotGroup> groups() { return groups; }

    /** Returns true if the given slot index falls in this region. */
    public boolean containsIndex(int slotIndex) {
        return slotIndex >= startIndex && slotIndex < endIndex;
    }

    /** Returns true if the region's panel is currently visible (slots are active). */
    public boolean isActive() {
        return panel.isVisible();
    }
}
