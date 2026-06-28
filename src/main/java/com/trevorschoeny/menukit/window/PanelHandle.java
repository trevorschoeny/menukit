package com.trevorschoeny.menukit.window;

/**
 * A typed handle on a panel itself — its own visibility, opacity, and inertness.
 * All CLIENT-tier, MK-typed, resolved by the same engine and stored in the same
 * address-keyed side-table as every other behavior (no separate panel-config
 * system). A panel-level default cascades to the panel's child elements via the
 * AXIS-2 specificity walk (their owner chain passes through this panel) unless a
 * child overrides — so "inert-under" is an ordinary panel property, not a flag.
 */
public final class PanelHandle extends WindowHandle {

    PanelHandle(Address address) {
        super(address);
    }

    /** Show/hide the whole panel on the client (CLIENT-tier). */
    public PanelHandle visibility(TriBool visible) {
        set(BehaviorKeys.VISIBILITY, visible);
        return this;
    }

    /** Whether the panel is interaction-opaque (eats clicks over its bounds). */
    public PanelHandle opacity(TriBool opaque) {
        set(BehaviorKeys.OPACITY, opaque);
        return this;
    }

    /** Whether the panel makes what it covers inert. */
    public PanelHandle inertness(TriBool inertMaking) {
        set(BehaviorKeys.INERTNESS, inertMaking);
        return this;
    }
}
