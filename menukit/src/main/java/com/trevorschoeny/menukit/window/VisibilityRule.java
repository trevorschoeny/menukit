package com.trevorschoeny.menukit.window;

/**
 * The value of the {@code VISIBILITY} behavior — whether an addressable thing is
 * shown, as a <b>client-side predicate</b> rather than a static flag. This is what
 * lets one key cover both "hide this" and "reveal this only while hovered" (the old
 * {@code revealWhen}): the rule is re-evaluated each frame in the client dispatch.
 *
 * <h2>Client-only, by contract</h2>
 *
 * VISIBILITY is a CLIENT-tier key and a rule is invoked <b>only on the client</b>,
 * in the render/input dispatch. The server never resolves VISIBILITY for a content
 * or sync decision — a created slot's contents keep syncing whether or not the
 * client is currently showing the slot (hiding is a render decision, not a server
 * state). So a rule may freely read client-only state (hover, config) and must not
 * be relied on server-side. (The engine's per-address store is shared client+server
 * in single-player; honoring "client resolves VISIBILITY" is what keeps a client
 * hide from ever stopping server sync.)
 */
@FunctionalInterface
public interface VisibilityRule {

    /** Whether the thing is currently shown. Evaluated per frame, client-side. */
    boolean visible();

    /** Always shown — the library default for the VISIBILITY key. */
    VisibilityRule VISIBLE = () -> true;

    /** Never shown. */
    VisibilityRule HIDDEN = () -> false;

    /** A constant rule from a boolean. */
    static VisibilityRule of(boolean visible) {
        return visible ? VISIBLE : HIDDEN;
    }
}
