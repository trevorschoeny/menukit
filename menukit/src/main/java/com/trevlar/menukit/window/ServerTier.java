package com.trevlar.menukit.window;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The MK-side holder for the server tier's installed implementation (DIP
 * ownership-inversion: MK owns the ports, MKC conforms and registers here at
 * init). Defaults to the {@link NoServerTier} null-object, so MK alone is fully
 * functional — server-tier reads say "no override" and writes are no-ops.
 *
 * <p>The reactive-dispatch port (Phase 4) joins this holder: server-authoritative
 * reactions route through {@link #dispatch()}, a no-op when MK-alone.
 *
 * <h2>Load-order independence (the init-time declaration footgun)</h2>
 *
 * A consumer declares server-tier behavior at mod init, by address — the thesis
 * "behavior keyed by address, independent of creation." But MKC installs the tier
 * during ITS init, and Fabric does not guarantee the consumer initializes after
 * MKC. Without care, a declaration made before {@link #install} would route to the
 * null-object and be <b>silently dropped</b>. So pre-install server-tier writes are
 * BUFFERED here (see {@link #declareWhenReady}) and flushed on install — the
 * declaration lands regardless of load order. MK-alone (MKC never installs) the
 * buffer simply never flushes, which equals the no-op server tier MK-alone gives.
 */
public final class ServerTier {

    private ServerTier() {}

    private static volatile ServerTierBridge bridge = NoServerTier.INSTANCE;
    private static volatile AuthoritativeDeclarations declarations = NoServerTier.INSTANCE;
    private static volatile VanillaSlotIdentity identity = NoServerTier.INSTANCE;
    private static volatile ReactiveDispatch dispatch = NoServerTier.INSTANCE;
    private static volatile boolean present = false;

    /** Server-tier writes made before {@link #install}; replayed on install. */
    private static final List<Runnable> PENDING = new ArrayList<>();

    /** MKC installs its server-tier implementation here at init. */
    public static void install(ServerTierBridge bridgeImpl, AuthoritativeDeclarations declarationsImpl) {
        Objects.requireNonNull(bridgeImpl, "bridgeImpl");
        Objects.requireNonNull(declarationsImpl, "declarationsImpl");
        // Flush under the PENDING lock so a write racing with install is either
        // buffered-then-flushed here or sees present==true and applies directly.
        synchronized (PENDING) {
            bridge = bridgeImpl;
            declarations = declarationsImpl;
            present = true;
            for (Runnable buffered : PENDING) buffered.run();
            PENDING.clear();
        }
    }

    /**
     * Apply a server-tier write now if the tier is installed, else buffer it for
     * replay on {@link #install}. The {@code write} should perform the actual
     * {@code bridge().declare(...)} call (it re-reads {@code bridge()} at run time,
     * so a buffered write uses the installed bridge, not the null-object). This is
     * what makes a consumer's init-time server-behavior declaration land regardless
     * of whether it ran before or after MKC's tier install.
     */
    static void declareWhenReady(Runnable write) {
        if (present) {                 // fast path: installed — apply directly
            write.run();
            return;
        }
        synchronized (PENDING) {
            if (present) {             // installed between the check above and the lock
                write.run();
                return;
            }
            PENDING.add(write);
        }
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
