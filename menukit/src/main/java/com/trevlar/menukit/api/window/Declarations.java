package com.trevlar.menukit.api.window;

import org.jetbrains.annotations.ApiStatus;

/**
 * Declarations freeze; state does not (§0063).
 *
 * <p>Every mod declares its slot groups, categories, operations, vetoes, menus,
 * container panels, channels and resolvers from its initializer. Once every
 * entrypoint has run (the first {@code SERVER_STARTING} on a server,
 * {@code CLIENT_STARTED} on a client, both before any menu can open), MenuKit
 * freezes them, and a later declaration throws, the way Minecraft's registries do.
 * A late declaration used to half-work: listed on one side, unwired on the other,
 * or taking effect only for menus built after it.
 *
 * <p>What stays legal is <b>state</b>: runtime cascade writes
 * ({@code Window.slot(a).set(...)}, {@code SlotOperations.inherent}), and the
 * indexes MenuKit fills as menus are built (a group declared by
 * {@code CreatedSlots.onto} when its menu is constructed), which are idempotent.
 */
public final class Declarations {

    private Declarations() {}

    private static volatile boolean frozen = false;
    private static volatile String frozenAt = "";

    /**
     * Throws if declarations have frozen.
     *
     * @param what the call being made, for the message ("SlotOperations.define(menukit:drop)")
     */
    @ApiStatus.Internal
    public static void requireOpen(String what) {
        if (frozen) {
            throw new IllegalStateException(what + " after declarations froze (" + frozenAt + "). "
                    + "Declare from your mod's initializer: MenuKit freezes declarations once every "
                    + "entrypoint has run, before any menu can open (§0063).");
        }
    }

    /** Whether declarations have frozen. */
    public static boolean frozen() {
        return frozen;
    }

    /** Freezes declarations. Called by MenuKit at client start and at the first server start. */
    @ApiStatus.Internal
    public static void freeze(String when) {
        if (frozen) return;
        frozenAt = when;
        frozen = true;
    }
}
