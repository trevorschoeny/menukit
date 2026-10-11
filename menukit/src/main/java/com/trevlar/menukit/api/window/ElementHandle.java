package com.trevlar.menukit.api.window;

/**
 * A typed handle on a panel element (label, button, outline), a created thing
 * that is not a slot. Element verbs are all client-tier, so they live here as
 * shorthand methods; a new one is a key plus a method, with no engine change.
 * Slot verbs are not reachable here. The engine would reject them through
 * {@code appliesTo} anyway, but the type makes it a compile error first.
 */
public final class ElementHandle extends WindowHandle {

    ElementHandle(Address address) {
        super(address);
    }

    /** Drives this element's client visibility by a {@link VisibilityRule}. */
    public ElementHandle visibility(VisibilityRule rule) {
        set(BehaviorKeys.VISIBILITY, rule);
        return this;
    }

    /** Shows or hides this element on the client (a constant rule). */
    public ElementHandle visibility(boolean visible) {
        return visibility(VisibilityRule.of(visible));
    }
}
