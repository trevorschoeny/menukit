package com.trevorschoeny.menukit.window;

/**
 * A typed handle on a panel element (label, button, outline) — a created thing
 * that is NOT a slot. Today's element verbs are all CLIENT-tier and MK-typed, so
 * they live here as sugar; new ones (e.g. {@code ON_CLICK}, {@code DECORATION})
 * land as keys + a sugar method with zero engine change. Slot verbs are unreachable
 * here (the engine would also reject them via {@code appliesTo}, but the type makes
 * it a compile error first).
 */
public final class ElementHandle extends WindowHandle {

    ElementHandle(Address address) {
        super(address);
    }

    /** Show/hide this element on the client (CLIENT-tier). */
    public ElementHandle visibility(TriBool visible) {
        set(BehaviorKeys.VISIBILITY, visible);
        return this;
    }
}
