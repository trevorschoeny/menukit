package com.trevlar.menukit.api.window;

/**
 * A typed handle on a panel itself: its own visibility and opacity.
 * All CLIENT-tier, MK-typed, resolved by the same engine and stored in the same
 * address-keyed side-table as every other behavior (no separate panel-config
 * system). A panel-level default cascades to the panel's child elements via the
 * AXIS-2 specificity walk (their owner chain passes through this panel) unless a
 * child overrides. An opaque panel makes what it covers inert (§0065), so opacity
 * is the one covering property.
 */
public final class PanelHandle extends WindowHandle {

    PanelHandle(Address address) {
        super(address);
    }

    /** Drive the whole panel's client visibility by a {@link VisibilityRule} (cascades to children). */
    public PanelHandle visibility(VisibilityRule rule) {
        set(BehaviorKeys.VISIBILITY, rule);
        return this;
    }

    /** Show/hide the whole panel on the client (constant rule). */
    public PanelHandle visibility(boolean visible) {
        return visibility(VisibilityRule.of(visible));
    }

    /** Whether the panel is interaction-opaque (eats clicks over its bounds). */
    public PanelHandle opacity(TriBool opaque) {
        set(BehaviorKeys.OPACITY, opaque);
        return this;
    }
}
