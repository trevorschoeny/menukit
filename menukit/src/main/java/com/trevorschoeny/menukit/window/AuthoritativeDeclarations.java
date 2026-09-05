package com.trevorschoeny.menukit.window;

/**
 * Port (MK defines, MKC implements): the READ side of the server tier — AXIS-1 of
 * the cascade. The engine consults this for a SERVER-tier key BEFORE its own
 * client cascade, so a server-authoritative declaration outranks any client wish
 * for that key ("a server gate can't be overruled by a client cosmetic").
 *
 * <p>The implementation (MKC, Phase 3c) performs the server tier's own
 * slot &gt; group resolution over its {@code BehaviorBindingTable} and returns the
 * winner; MK owns the authority axis (placing this above the client tier) and the
 * client cascade. With MKC absent the installed implementation is the
 * {@link NoServerTier} null-object, whose {@link #resolve} always returns
 * {@link Decl#inherit()} — i.e. the server never overrides, so the key falls
 * through to the client tier and finally the library default.
 */
public interface AuthoritativeDeclarations {

    /**
     * The server-authoritative declaration for {@code (address, key)} — the
     * server tier's own resolved result. Returns {@link Decl.Inherit} (never
     * {@code null}) when the server does not override.
     */
    <V> Decl<V> resolve(Address address, BehaviorKey<V> key);
}
