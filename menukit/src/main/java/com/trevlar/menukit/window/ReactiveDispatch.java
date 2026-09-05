package com.trevlar.menukit.window;

/**
 * Port (MK defines, MKC implements): the firing side of server-authoritative
 * reactions. The reaction <em>declarations</em> ({@code ON_INSERT}/{@code ON_TAKE}
 * keys, {@link ReactiveHook}, {@link ReactEvent}) all live in MK so an MK-alone
 * consumer can name the verbs; but a SERVER-tier reaction may have real game-state
 * effects and must fire on the server thread inside the menu transaction, which
 * only MKC can do. So MK resolves the hook and applies the {@link ReactionGuard},
 * then hands the actual invocation to this port.
 *
 * <p>With MKC absent the installed dispatch is the {@link NoServerTier}
 * null-object, so a SERVER-tier reaction authored on a client-only install simply
 * never fires — never null, never crash (the keys still resolve; nothing invokes
 * them). Observed (CLIENT-tier) reactions do <em>not</em> go through this port —
 * MK fires them directly off synced snapshots, MK-alone capable.
 *
 * <p><b>Owed (architecture Part 2 §6, the one honest gap):</b> the firing
 * <em>seams</em> — which vanilla menu methods to intercept across every menu type
 * to call {@link WindowReactions#fireInsert}/{@link WindowReactions#fireTake}
 * (with {@code server=true}) after a committed mutation — are the named owed
 * implementation, verified in-game. This port is the boundary they will call
 * through; until they exist, nothing invokes the dispatch.
 */
public interface ReactiveDispatch {

    /**
     * Invoke {@code hook} for {@code event} on the server, inside the current menu
     * transaction. MK has already resolved the hook and cleared the re-entrancy
     * bound; the implementation only runs it with whatever server-thread/transaction
     * guarantees authoritative effects require.
     */
    void fire(ReactiveHook hook, ReactEvent event);
}
