package com.trevorschoeny.menukit.window;

import java.util.Objects;

/**
 * The MK-side holder for the server tier's installed implementation (DIP
 * ownership-inversion: MK owns the ports, MKC conforms and registers here at
 * init). Defaults to the {@link NoServerTier} null-object, so MK alone is fully
 * functional — server-tier reads say "no override" and writes are no-ops.
 *
 * <p>The reactive-dispatch port (Phase 4) joins this holder: server-authoritative
 * reactions route through {@link #dispatch()}, a no-op when MK-alone.
 */
public final class ServerTier {

    private ServerTier() {}

    private static volatile ServerTierBridge bridge = NoServerTier.INSTANCE;
    private static volatile AuthoritativeDeclarations declarations = NoServerTier.INSTANCE;
    private static volatile VanillaSlotIdentity identity = NoServerTier.INSTANCE;
    private static volatile ReactiveDispatch dispatch = NoServerTier.INSTANCE;
    private static volatile boolean present = false;

    /** MKC installs its server-tier implementation here at init. */
    public static void install(ServerTierBridge bridgeImpl, AuthoritativeDeclarations declarationsImpl) {
        bridge = Objects.requireNonNull(bridgeImpl, "bridgeImpl");
        declarations = Objects.requireNonNull(declarationsImpl, "declarationsImpl");
        present = true;
    }

    /** MKC installs its §0050-backed vanilla-slot identity here (additive). */
    public static void installIdentity(VanillaSlotIdentity identityImpl) {
        identity = Objects.requireNonNull(identityImpl, "identityImpl");
    }

    /** MKC installs its server-thread reactive firing here (additive). */
    public static void installDispatch(ReactiveDispatch dispatchImpl) {
        dispatch = Objects.requireNonNull(dispatchImpl, "dispatchImpl");
    }

    static ReactiveDispatch dispatch() {
        return dispatch;
    }

    static VanillaSlotIdentity identity() {
        return identity;
    }

    /** Whether a real server tier (MKC) is installed. */
    public static boolean present() {
        return present;
    }

    static ServerTierBridge bridge() {
        return bridge;
    }

    static AuthoritativeDeclarations declarations() {
        return declarations;
    }
}
