package com.trevlar.menukit.api.window;

/**
 * The value of the {@code VISIBILITY} behavior: whether an addressable thing is
 * shown, as a client-side predicate rather than a flag. One key covers both "hide
 * this" and "show this only while hovered" (the old {@code revealWhen}), because
 * the rule is evaluated again each frame.
 *
 * <h2>Client only</h2>
 *
 * VISIBILITY is a client-tier key, and a rule runs only on the client, in render
 * and input dispatch. The server never resolves it for a content or sync decision:
 * a created slot's contents keep syncing whether or not the client shows the slot.
 * So a rule may read client-only state (hover, config) and must not be relied on
 * server-side. In single-player the engine's store is shared between client and
 * server, and resolving VISIBILITY only on the client is what keeps a client hide
 * from ever stopping server sync.
 */
@FunctionalInterface
public interface VisibilityRule {

    /** Whether the thing is shown now. Evaluated each frame, on the client. */
    boolean visible();

    /** Always shown, the library default for the VISIBILITY key. */
    VisibilityRule VISIBLE = () -> true;

    /** Never shown. */
    VisibilityRule HIDDEN = () -> false;

    /** A constant rule from a boolean. */
    static VisibilityRule of(boolean visible) {
        return visible ? VISIBLE : HIDDEN;
    }
}
