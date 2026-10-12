package com.trevlar.menukit.api;

import org.jetbrains.annotations.ApiStatus;

/**
 * Declarations freeze; state does not.
 *
 * <p>Every mod declares its panels, adapters, tooltip rules and menu chrome from its
 * initializer, and so do the Kits built on MenuKit (SlotKit's slot groups, operations
 * and channels; ConfigKit's settings), through this one freeze, so every Kit freezes at
 * the same moment. Once every entrypoint has run (the first {@code SERVER_STARTING} on
 * a server, {@code CLIENT_STARTED} on a client, both before any menu can open), MenuKit
 * freezes them, and a later declaration throws, the way Minecraft's registries do. A
 * late declaration used to half-work: listed on one side, unwired on the other, or
 * taking effect only for screens built after it.
 *
 * <p>What stays legal is <b>state</b>: a panel shown or hidden, a value written, an
 * index a library fills as menus are built.
 */
public final class Declarations {

    private Declarations() {}

    private static volatile boolean frozen = false;
    private static volatile String frozenAt = "";

    /**
     * Throws if declarations have frozen. A library built on MenuKit calls this at the
     * top of each of its own declaring methods, so its declarations freeze with
     * MenuKit's.
     *
     * @param what the call being made, for the message ("ScreenPanelAdapter(mymod:toolbar)")
     */
    public static void requireOpen(String what) {
        if (frozen) {
            throw new IllegalStateException(what + " after declarations froze (" + frozenAt + "). "
                    + "Declare from your mod's initializer: MenuKit freezes declarations once every "
                    + "entrypoint has run, before any menu can open.");
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
