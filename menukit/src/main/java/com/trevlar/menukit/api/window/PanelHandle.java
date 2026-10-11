package com.trevlar.menukit.api.window;

/**
 * A typed handle on a panel itself: its own visibility and opacity. Both are
 * client-tier and stored like every other behavior, by address, with no separate
 * panel config. A panel-level value cascades to the panel's elements (their owner
 * chain passes through the panel) unless an element overrides it. An opaque panel
 * makes what it covers inert, so opacity is the one covering property.
 */
public final class PanelHandle extends WindowHandle {

    PanelHandle(Address address) {
        super(address);
    }

    /** Drives the whole panel's client visibility by a {@link VisibilityRule}. Cascades to its elements. */
    public PanelHandle visibility(VisibilityRule rule) {
        set(BehaviorKeys.VISIBILITY, rule);
        return this;
    }

    /** Shows or hides the whole panel on the client (a constant rule). */
    public PanelHandle visibility(boolean visible) {
        return visibility(VisibilityRule.of(visible));
    }

    /** Whether the panel is opaque to input, taking clicks over its bounds. */
    public PanelHandle opacity(TriBool opaque) {
        set(BehaviorKeys.OPACITY, opaque);
        return this;
    }
}
